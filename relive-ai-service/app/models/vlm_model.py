"""
Vision model — Qwen2.5-VL, used only for image processing step 9 (22-category
vocabulary generation). See app/models/llm_model.py for the separate
text-only model (query parsing, synonym generation).

All 22 categories are sent in ONE call (one image encoding) rather than
22 separate calls — confirmed via logs that llama-cpp-python's
create_chat_completion fully re-encodes the image on every separate call
regardless of shared conversation history, so splitting into multiple calls
bought us nothing but 22x the encoding cost. Each category gets a required
"scratchpad" reasoning field before its word list, inside the same JSON
grammar, to push the model past just restating the example words given for
each category.

Word-count fix: the JSON schema deliberately does NOT put a minItems floor
on each category's "words" array. A schema-level minimum would force the
model to hallucinate filler words for categories that genuinely don't apply
to a given image. Instead, exhaustiveness is pushed for via the prompt
itself (explicit "do not stop early" instructions) plus non-greedy sampling
(temperature/top_p/repeat_penalty below) — greedy decoding (temperature=0)
reliably converges on the shortest valid answer per category, since nothing
in a schema-only constraint pushes the model to keep enumerating once it's
produced something plausible. max_tokens is also computed dynamically per
call (mirroring the pattern in llm_model.py's generate_synonyms) instead of
a single flat constant, so the output budget scales with how much room is
actually left in the context window.

NOTE: an earlier revision added 3 category-specific content rules here
(banning relationship-guessing in People, capping Colors/Materials to
common names, dropping generic words from Image Style). Those have been
rolled back on purpose — they were reactive patches derived from one image
and risked being wrong or irrelevant for the millions of other images this
will process. Redundant/non-discriminative/occasionally-hallucinated words
are treated as tolerable noise for now, not something to chase with more
hand-written category rules; word-quality work is focused on the one thing
that isn't tolerable (fabricated/non-existent words), which is a
lemmatization problem, not a prompting problem — see app/utils/lemmatizer.py.

_llm_lock: this object isn't currently called concurrently with itself
under the current architecture (the backend's single-threaded async
executor already prevents that), but the lock is added anyway as cheap,
defensive consistency with llm_model.py's _llm_lock, which fixes a
confirmed real hang caused by exactly this kind of unprotected concurrent
access to a shared llama-cpp-python model object.

Loading starts at the DETECTED hardware tier and steps down only on an
actual load failure.
"""

import base64
import io
import json
import os
import threading

from PIL import Image
from llama_cpp import Llama, LlamaGrammar
from llama_cpp.llama_chat_format import Qwen25VLChatHandler

from app.config import (
    VLM_MODEL_BY_TIER,
    VLM_REPO_BY_MODEL,
    VLM_GGUF_FILE_BY_MODEL,
    VLM_MMPROJ_FILE_BY_MODEL,
    VLM_CONTEXT_WINDOW,
    VLM_VOCAB_MIN_TOKENS,
    VLM_IMAGE_TOKEN_RESERVE,
    VLM_TOKEN_SAFETY_MARGIN,
    VLM_VOCAB_TEMPERATURE,
    VLM_VOCAB_TOP_P,
    VLM_VOCAB_REPEAT_PENALTY,
)
from app.hardware import Tier, gpu_tier, describe as describe_hardware
from app.services.vocabulary_categories import VOCABULARY_CATEGORIES

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]


def _try_load(model_name: str):
    repo_id = VLM_REPO_BY_MODEL[model_name]
    gguf_file = VLM_GGUF_FILE_BY_MODEL[model_name]
    mmproj_file = VLM_MMPROJ_FILE_BY_MODEL[model_name]

    print(f"[vlm_model] Attempting to load {model_name} ({repo_id})...")
    chat_handler = Qwen25VLChatHandler.from_pretrained(repo_id=repo_id, filename=mmproj_file)
    llm = Llama.from_pretrained(
        repo_id=repo_id,
        filename=gguf_file,
        chat_handler=chat_handler,
        n_ctx=VLM_CONTEXT_WINDOW,
        n_threads=os.cpu_count(),
        verbose=False,
    )
    print(f"[vlm_model] Loaded {model_name}.")
    return llm


def _load_with_fallback():
    start_tier = gpu_tier()
    start_idx = _TIER_ORDER.index(start_tier)
    last_error = None
    print(f"[vlm_model] Detected tier: {start_tier}. Starting load attempt there.")
    for tier in _TIER_ORDER[start_idx:]:
        model_name = VLM_MODEL_BY_TIER[tier]
        try:
            return _try_load(model_name)
        except Exception as e:
            print(f"[vlm_model] Load failed for tier={tier} ({model_name}): {e}")
            last_error = e
            continue
    raise RuntimeError(
        f"Could not load any VLM tier from {start_tier} downward. "
        f"Hardware: {describe_hardware()}. Last error: {last_error}"
    )


_llm = _load_with_fallback()
_llm_lock = threading.Lock()

_NUM_CATEGORIES = len(VOCABULARY_CATEGORIES)

_VOCAB_SCHEMA = {
    "type": "object",
    "properties": {
        "categories": {
            "type": "array",
            "minItems": _NUM_CATEGORIES,
            "maxItems": _NUM_CATEGORIES,
            "items": {
                "type": "object",
                "properties": {
                    "scratchpad": {"type": "string"},
                    "words": {"type": "array", "items": {"type": "string"}},
                },
                "required": ["scratchpad", "words"],
            },
        }
    },
    "required": ["categories"],
}
_vocab_grammar = LlamaGrammar.from_json_schema(json.dumps(_VOCAB_SCHEMA))

_VOCAB_SYSTEM_PROMPT = """You are an exhaustive visual vocabulary extractor for a personal photo search engine.
You will be given one image and a numbered list of 22 categories. For EACH category, in order, produce:
- "scratchpad": a short free-text note where you actually look at the image and think through what, if
  anything, in this specific category is genuinely visible — do this BEFORE deciding the word list. Do not
  skip this step or leave it a placeholder; use it to actually reason, since this is what catches things
  you'd otherwise miss.
- "words": every word from that category that genuinely, visibly applies, based on your scratchpad.

Critical rules:
- The example words listed under each category are illustrative STARTING POINTS ONLY — they are not
  exhaustive and not a ceiling. If something else genuinely visible in the image fits the category but
  isn't in the examples, include it anyway. Do not just restate the given examples.
- Only include a word if it can genuinely be used to describe something actually visible in the image.
  Never hallucinate or guess.
- Look thoroughly — do not miss something small or partially visible.
- There is NO upper limit on how many words a category can have. If ten, twenty, or more words genuinely
  apply to a category, list all of them. Producing too FEW words for a category that clearly has more to
  say is a much bigger mistake than producing too many — do not stop after just one or two words if more
  genuinely visible things fit. Only give a short list if that is genuinely all there is to say.
- If nothing in a category applies, its scratchpad should say so briefly and "words" should be an empty list.
- Use singular, lowercase, simple word forms (e.g. "ship" not "ships", "child" not "children").
- Never include a person's proper name in any category.
- Return exactly 22 entries in "categories", in the same order the categories are given below."""


def _build_categories_block() -> str:
    lines = []
    for i, category in enumerate(VOCABULARY_CATEGORIES, start=1):
        excludes_line = f"\n   Excludes: {category['excludes']}" if category["excludes"] else ""
        lines.append(
            f"{i}. {category['name']}\n"
            f"   Scope: {category['scope']}\n"
            f"   Example words (non-exhaustive — think beyond these): {category['includes']}{excludes_line}"
        )
    return "\n\n".join(lines)


_CATEGORIES_BLOCK = _build_categories_block()


def _image_to_data_uri(image: Image.Image) -> str:
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG")
    encoded = base64.b64encode(buffer.getvalue()).decode("utf-8")
    return f"data:image/jpeg;base64,{encoded}"


def _compute_max_tokens(text_prompt: str) -> int:
    full_text_for_count = _VOCAB_SYSTEM_PROMPT + text_prompt
    text_token_count = len(_llm.tokenize(full_text_for_count.encode("utf-8")))

    return max(
        VLM_VOCAB_MIN_TOKENS,
        VLM_CONTEXT_WINDOW - text_token_count - VLM_IMAGE_TOKEN_RESERVE - VLM_TOKEN_SAFETY_MARGIN,
        )


def generate_vocabulary(image: Image.Image) -> list[str]:
    image_data_uri = _image_to_data_uri(image)
    text_prompt = f"Categories:\n\n{_CATEGORIES_BLOCK}"

    max_tokens = _compute_max_tokens(text_prompt)

    messages = [
        {"role": "system", "content": _VOCAB_SYSTEM_PROMPT},
        {
            "role": "user",
            "content": [
                {"type": "image_url", "image_url": {"url": image_data_uri}},
                {"type": "text", "text": text_prompt},
            ],
        },
    ]

    with _llm_lock:
        completion = _llm.create_chat_completion(
            messages=messages,
            grammar=_vocab_grammar,
            max_tokens=max_tokens,
            temperature=VLM_VOCAB_TEMPERATURE,
            top_p=VLM_VOCAB_TOP_P,
            repeat_penalty=VLM_VOCAB_REPEAT_PENALTY,
        )
    raw_text = completion["choices"][0]["message"]["content"]

    all_words: set[str] = set()
    try:
        parsed = json.loads(raw_text)
        categories = parsed.get("categories", [])
        for entry in categories:
            words = [str(w).strip().lower() for w in entry.get("words", []) if str(w).strip()]
            all_words.update(words)
    except Exception as e:
        print(f"[vlm_model] Vocabulary parse failed: {e}")

    return sorted(all_words)
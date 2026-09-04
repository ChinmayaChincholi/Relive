"""
Vision model — Qwen2.5-VL, used only for image processing step 9 (22-category
vocabulary generation). See app/models/llm_model.py for the separate
text-only model (query parsing, synonym generation).

All 22 categories are now sent in ONE call (one image encoding) rather than
22 separate calls — confirmed via logs that llama-cpp-python's
create_chat_completion fully re-encodes the image on every separate call
regardless of shared conversation history, so splitting into multiple calls
bought us nothing but 22x the encoding cost. Each category gets a required
"scratchpad" reasoning field before its word list, inside the same JSON
grammar, to push the model past just restating the example words given for
each category.

Loading starts at the DETECTED hardware tier and steps down only on an
actual load failure.
"""

import base64
import io
import json
import os

from PIL import Image
from llama_cpp import Llama, LlamaGrammar
from llama_cpp.llama_chat_format import Qwen25VLChatHandler

from app.config import (
    VLM_MODEL_BY_TIER,
    VLM_REPO_BY_MODEL,
    VLM_GGUF_FILE_BY_MODEL,
    VLM_MMPROJ_FILE_BY_MODEL,
    VLM_CONTEXT_WINDOW,
    VLM_MAX_NEW_TOKENS_VOCAB,
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

_NUM_CATEGORIES = len(VOCABULARY_CATEGORIES)

# One entry per category, in the SAME fixed order as VOCABULARY_CATEGORIES —
# correlated by position rather than repeating category names in the schema,
# to keep this a flat (non-recursive) schema that from_json_schema handles
# fine (the earlier grammar bug was specific to genuinely self-referential
# $ref trees, not fixed-length arrays like this one).
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


def generate_vocabulary(image: Image.Image) -> list[str]:
    """Takes an already-processed PIL Image (RGB, resized) — NOT a file path.
    Single call, single image encode, all 22 categories addressed with a
    per-category reasoning scratchpad inside the constrained JSON output."""
    image_data_uri = _image_to_data_uri(image)

    messages = [
        {"role": "system", "content": _VOCAB_SYSTEM_PROMPT},
        {
            "role": "user",
            "content": [
                {"type": "image_url", "image_url": {"url": image_data_uri}},
                {"type": "text", "text": f"Categories:\n\n{_CATEGORIES_BLOCK}"},
            ],
        },
    ]

    completion = _llm.create_chat_completion(
        messages=messages,
        grammar=_vocab_grammar,
        max_tokens=VLM_MAX_NEW_TOKENS_VOCAB,
        temperature=0.0,
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
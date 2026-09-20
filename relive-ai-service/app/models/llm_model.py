"""
Text-only model — Qwen3.5/3.6, used for image processing step 10 (synonym
generation, no image needed).

Plain Llama, no chat_handler — this never depended on vision support.
Loading starts at the DETECTED hardware tier (cpu_ram_tier — text generation
here is CPU/RAM-bound, not GPU-bound) and steps down only on an actual load
failure.

_llm_lock: this module's _llm object is called from generate_synonyms()
(part of every image's /analyze processing). FastAPI routes here are plain
"def", so Starlette dispatches concurrent requests to worker threads,
meaning two threads really can call create_chat_completion() on the SAME
Llama object at once — a documented llama.cpp thread-safety hazard. The lock
serializes access so concurrent calls queue behind each other instead of
racing.
"""
import json
import os
import threading

from llama_cpp import Llama, LlamaGrammar

from app.config import (
    LLM_MODEL_BY_TIER,
    LLM_REPO_BY_MODEL,
    LLM_GGUF_FILE_BY_MODEL,
    LLM_CONTEXT_WINDOW,
    LLM_MAX_NEW_TOKENS_SYNONYMS,
    LLM_SYNONYM_SAFETY_MARGIN,
)
from app.hardware import Tier, cpu_ram_tier, describe as describe_hardware

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]

def _try_load(model_name: str):
    repo_id = LLM_REPO_BY_MODEL[model_name]
    gguf_file = LLM_GGUF_FILE_BY_MODEL[model_name]
    if any(ch in gguf_file for ch in "*?["):
        raise ValueError(
            f"{model_name}: LLM_GGUF_FILE_BY_MODEL entry \"{gguf_file}\" is a "
            f"wildcard, not a pinned filename --- check {repo_id}'s file "
            f"listing and replace it with the exact filename before loading."
        )
    print(f"[llm_model] Attempting to load {model_name} ({repo_id})...")
    llm = Llama.from_pretrained(
        repo_id=repo_id,
        filename=gguf_file,
        n_ctx=LLM_CONTEXT_WINDOW,
        n_threads=os.cpu_count(),
        use_mmap=False,
        verbose=True,
    )
    print(f"[llm_model] Loaded {model_name}.")
    return llm

def _load_with_fallback():
    start_tier = cpu_ram_tier()
    start_idx = _TIER_ORDER.index(start_tier)
    last_error = None
    print(f"[llm_model] Detected tier: {start_tier}. Starting load attempt there.")
    for tier in _TIER_ORDER[start_idx:]:
        model_name = LLM_MODEL_BY_TIER[tier]
        try:
            return _try_load(model_name)
        except Exception as e:
            print(f"[llm_model] Load failed for tier={tier} ({model_name}): {e}")
            last_error = e
            continue
    raise RuntimeError(
        f"Could not load any LLM tier from {start_tier} downward. "
        f"Hardware: {describe_hardware()}. Last error: {last_error}"
    )

_llm = _load_with_fallback()
_llm_lock = threading.Lock()

# ---------------------------------------------------------------------------
# Synonym-generation grammar — flat schema, from_json_schema works fine here.
# ---------------------------------------------------------------------------
_SYNONYM_SCHEMA = {
    "type": "object",
    "properties": {
        "expansions": {
            "type": "array",
            # Hard upper bound regardless of batch size — a defensive
            # backstop, not expected to bind in normal operation.
            "maxItems": 25,
            "items": {
                "type": "object",
                "properties": {
                    "word": {"type": "string", "maxLength": 30},
                    "related": {
                        "type": "array",
                        # THE actual fix for the confirmed repetition-loop
                        # bug: without this, a word like "water" or "pants"
                        # had no grammar-level reason to ever stop growing
                        # its "related" array, and a model stuck repeating
                        # itself ("leggings" appeared 4 times in one 10-item
                        # list) would keep going until max_tokens cut it off
                        # mid-string. This caps it regardless of whether the
                        # model is genuinely finding more synonyms or just
                        # looping — 8 is generous for real use.
                        "maxItems": 8,
                        "items": {"type": "string", "maxLength": 30},
                    },
                },
                "required": ["word", "related"],
            },
        }
    },
    "required": ["expansions"],
}

# ---------------------------------------------------------------------------
# Synonym / related-word expansion (import time, text-only, batched)
# ---------------------------------------------------------------------------
_SYNONYM_SYSTEM_PROMPT = """You are generating search-index expansions for a personal photo search engine.
For each given word, list safe synonyms and closely related words that a user might reasonably type
into a search box expecting to find a photo tagged with the original word.

Rules:
- Synonyms (near-identical meaning) are always safe to include.
- Only include a "closely related" (not strictly synonymous) word if it would still be a REASONABLE
match — i.e. if a user searched that related word, this photo genuinely being returned would not
feel wrong or surprising to them. When in doubt, leave it out.
- Stay in the same sense/context as the word was used — do not add meanings from an unrelated context
(e.g. do not add dog-related words for "bark" if the word refers to tree bark).
- Return lowercase, singular word forms.
- If a word genuinely has no safe expansions, return an empty list for it — do not force results."""

def generate_synonyms(words: list[str]) -> dict[str, list[str]]:
    if not words:
        return {}
    return _generate_synonyms_batch(words)

def _generate_synonyms_batch(words: list[str]) -> dict[str, list[str]]:
    if not words:
        return {}
    numbered = "\n".join(f"- {w}" for w in words)
    prompt = f"Words:\n{numbered}"
    full_prompt_text = _SYNONYM_SYSTEM_PROMPT + prompt
    prompt_token_count = len(_llm.tokenize(full_prompt_text.encode("utf-8")))
    max_tokens = min(150 * len(words), max(500, LLM_CONTEXT_WINDOW - prompt_token_count - LLM_SYNONYM_SAFETY_MARGIN))
    try:
        grammar = LlamaGrammar.from_json_schema(json.dumps(_SYNONYM_SCHEMA))
        with _llm_lock:
            _llm.reset()
            completion = _llm.create_chat_completion(
                messages=[
                    {"role": "system", "content": _SYNONYM_SYSTEM_PROMPT},
                    {"role": "user", "content": prompt},
                ],
                grammar=grammar,
                max_tokens=max_tokens,
                temperature=0.0,
            )
        raw_text = completion["choices"][0]["message"]["content"]
    except Exception as e:
        print(f"[llm_model] Synonym generation call failed for a batch of "
              f"{len(words)} word(s): {e} --- retrying with smaller batch(es).")
        return _retry_split(words)
    try:
        parsed = json.loads(raw_text)
        result = {}
        for entry in parsed.get("expansions", []):
            word = str(entry.get("word", "")).strip().lower()
            related = [str(r).strip().lower() for r in entry.get("related", []) if str(r).strip()]
            if word:
                result[word] = related
        return result
    except Exception as e:
        print(f"[llm_model] Synonym parse failed for a batch of {len(words)} "
              f"word(s): {e} (raw output length={len(raw_text)} chars)")
        print(f"[llm_model] Raw failed output for {words}: {raw_text!r}")
        print(f"[llm_model] Retrying with smaller batch(es).")
        return _retry_split(words)

def _retry_split(words: list[str]) -> dict[str, list[str]]:
    if len(words) <= 1:
        if words:
            print(f"[llm_model] Giving up on synonym generation for a single "
                  f"word that still failed on its own: {words[0]!r}")
        return {}
    mid = len(words) // 2
    left = _generate_synonyms_batch(words[:mid])
    right = _generate_synonyms_batch(words[mid:])
    merged = dict(left)
    merged.update(right)
    return merged
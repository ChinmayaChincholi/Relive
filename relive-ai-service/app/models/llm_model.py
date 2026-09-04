"""
Text-only model — Qwen3.5/3.6, used for:
  - image processing step 10 (synonym generation, no image needed)
  - image retrieval step 2 (query -> boolean expression tree)
Plain Llama, no chat_handler — this never depended on vision support.

Loading starts at the DETECTED hardware tier (cpu_ram_tier — text generation
here is CPU/RAM-bound, not GPU-bound) and steps down only on an actual load
failure.
"""

import json
import os

from llama_cpp import Llama, LlamaGrammar

from app.config import (
    LLM_MODEL_BY_TIER,
    LLM_REPO_BY_MODEL,
    LLM_GGUF_FILE_BY_MODEL,
    LLM_CONTEXT_WINDOW,
    LLM_MAX_NEW_TOKENS_SYNONYMS,
    LLM_MAX_NEW_TOKENS_QUERY,
)
from app.hardware import Tier, cpu_ram_tier, describe as describe_hardware

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]


def _try_load(model_name: str):
    repo_id = LLM_REPO_BY_MODEL[model_name]
    gguf_file = LLM_GGUF_FILE_BY_MODEL[model_name]

    print(f"[llm_model] Attempting to load {model_name} ({repo_id})...")
    llm = Llama.from_pretrained(
        repo_id=repo_id,
        filename=gguf_file,
        n_ctx=LLM_CONTEXT_WINDOW,
        n_threads=os.cpu_count(),
        verbose=False,
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

# ---------------------------------------------------------------------------
# Synonym-generation grammar — flat schema, from_json_schema works fine here
# (the KeyError only ever showed up on the recursive Expression schema below).
# ---------------------------------------------------------------------------
_SYNONYM_SCHEMA = {
    "type": "object",
    "properties": {
        "expansions": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "word": {"type": "string"},
                    "related": {"type": "array", "items": {"type": "string"}},
                },
                "required": ["word", "related"],
            },
        }
    },
    "required": ["expansions"],
}
_synonym_grammar = LlamaGrammar.from_json_schema(json.dumps(_SYNONYM_SCHEMA))

# ---------------------------------------------------------------------------
# Query expression tree grammar — hand-written GBNF instead of
# from_json_schema. The $defs/$ref-based JSON Schema approach hit a real bug
# in this llama-cpp-python version's ref resolver (KeyError on a nested
# $ref that isn't the top-level one — Expression resolved, the nested
# TermLeaf ref inside it didn't). GBNF supports genuine recursive rules
# natively (a rule can reference itself), so writing the grammar directly
# sidesteps the broken converter entirely rather than working around it.
# ---------------------------------------------------------------------------
_EXPRESSION_GBNF = r'''
root ::= ws expression ws

expression ::= "{" ws
  "\"term\"" ws ":" ws term-or-null ws "," ws
  "\"must\"" ws ":" ws expr-array ws "," ws
  "\"should\"" ws ":" ws expr-array ws "," ws
  "\"must_not\"" ws ":" ws expr-array ws
"}"

expr-array ::= "[" ws (expression (ws "," ws expression)*)? ws "]"

term-or-null ::= term-leaf | "null"

term-leaf ::= "{" ws
  "\"domain\"" ws ":" ws domain-string ws "," ws
  "\"value\"" ws ":" ws json-string ws "," ws
  "\"range_end\"" ws ":" ws range-end ws
"}"

domain-string ::= "\"PERSON\"" | "\"LOCATION\"" | "\"DATE\"" | "\"TIME\"" | "\"VOCAB\""

range-end ::= json-string | "null"

json-string ::= "\"" ( [^"\\\x7F\x00-\x1F] | "\\" (["\\bfnrt] | "u" [0-9a-fA-F]{4}) )* "\""

ws ::= [ \t\n]*
'''
_expression_grammar = LlamaGrammar.from_string(_EXPRESSION_GBNF)

# ---------------------------------------------------------------------------
# Job 2 — synonym / related-word expansion (import time, text-only, batched)
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
    """One batched call for all words from one image, not one call per word.
    max_tokens is computed from the ACTUAL remaining context window space
    after the real prompt, not a guessed per-word multiplier — two guessed
    multipliers in a row (400, then 80/word) both still truncated on a
    large-enough vocabulary, so this removes the guessing entirely: tokenize
    the real prompt, use whatever's left."""
    if not words:
        return {}

    numbered = "\n".join(f"- {w}" for w in words)
    prompt = f"Words:\n{numbered}"

    full_prompt_text = _SYNONYM_SYSTEM_PROMPT + prompt
    prompt_token_count = len(_llm.tokenize(full_prompt_text.encode("utf-8")))

    safety_margin = 100  # headroom for chat-template formatting overhead
    max_tokens = max(500, LLM_CONTEXT_WINDOW - prompt_token_count - safety_margin)

    completion = _llm.create_chat_completion(
        messages=[
            {"role": "system", "content": _SYNONYM_SYSTEM_PROMPT},
            {"role": "user", "content": prompt},
        ],
        grammar=_synonym_grammar,
        max_tokens=max_tokens,
        temperature=0.0,
    )
    raw_text = completion["choices"][0]["message"]["content"]

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
        print(f"[llm_model] Synonym parse failed: {e}")
        return {}


# ---------------------------------------------------------------------------
# Job 3 — query parsing into the boolean expression tree (retrieval time)
# ---------------------------------------------------------------------------

_QUERY_SYSTEM_PROMPT = """You are a query parser for a personal photo search engine. The query has already
been spell-corrected. Parse it into a recursive boolean expression tree.

Schema: an Expression has "term" (set only on leaf nodes) OR one or more of "must"/"should"/"must_not"
(lists of child Expressions; empty list if unused). A leaf's "term" has:
- domain: PERSON (a person's name — including relationship words used AS a name, e.g. "mom", "dad"
  ARE VOCAB not PERSON unless clearly a proper name), LOCATION, DATE, TIME, or VOCAB (anything else
  that helps describe the image — objects, activities, moods, events, etc).
- value: the term itself. For DATE use "DD-MM-YYYY". For TIME use 24-hour "HH:MM".
- range_end: set only when this term is a range (e.g. a date/time range); null otherwise.

must = AND (every child must match). should = OR (at least one child must match). must_not = NOT
(none of these children may match).

Word-form rule: normalize VOCAB terms toward their base singular form when generating "value" (e.g.
"ships" -> "ship") — downstream lookup handles exact word-form matching separately, your job is just
correct domain/value/range extraction and correct AND/OR/NOT structure.

Only extract words that actually help describe the image (skip "give", "me", "images", "of", "photos"
unless "photos" is itself the subject, e.g. "a person taking photos").

Date ranges without an explicit day/month at both ends: fill missing lower bound as day 01 / month 01,
missing upper bound as the last day of that month/year. Do not accept a date range with no year specified
at all — in that case, treat the term as best-effort as if it were VOCAB text instead, since a reliable
range cannot be constructed.

Always return valid JSON matching the schema exactly. Never include commentary."""


def parse_query(query: str) -> dict:
    completion = _llm.create_chat_completion(
        messages=[
            {"role": "system", "content": _QUERY_SYSTEM_PROMPT},
            {"role": "user", "content": query},
        ],
        grammar=_expression_grammar,
        max_tokens=LLM_MAX_NEW_TOKENS_QUERY,
        temperature=0.0,
    )
    raw_text = completion["choices"][0]["message"]["content"]

    try:
        return json.loads(raw_text)
    except Exception as e:
        print(f"[llm_model] Query parse failed, falling back to a single VOCAB term: {e}")
        return {
            "term": {"domain": "VOCAB", "value": query.strip().lower(), "range_end": None},
            "must": [], "should": [], "must_not": [],
        }
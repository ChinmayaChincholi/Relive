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
    LLM_SYNONYM_SAFETY_MARGIN,
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
# Synonym-generation grammar — flat schema, from_json_schema works fine here.
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
# Query expression tree grammar — JSON Schema, fully inlined (no $ref), 3
# real levels of must/should/must_not nesting then a forced leaf at level 4.
# See prior revisions' history for why: a hand-written GBNF via
# LlamaGrammar.from_string() crashed natively 3 times running, so this uses
# LlamaGrammar.from_json_schema() instead — the code path already proven
# reliable for the vocabulary/synonym grammars — with the same bounded-depth
# structure expressed as inlined nested dicts.
# ---------------------------------------------------------------------------

_TERM_LEAF_SCHEMA = {
    "type": "object",
    "properties": {
        "domain": {"type": "string", "enum": ["PERSON", "LOCATION", "DATE", "TIME", "VOCAB"]},
        "value": {"type": "string"},
        "range_end": {"anyOf": [{"type": "string"}, {"type": "null"}]},
    },
    "required": ["domain", "value", "range_end"],
}

_TERM_OR_NULL_SCHEMA = {"anyOf": [_TERM_LEAF_SCHEMA, {"type": "null"}]}


def _leaf_level_expression_schema() -> dict:
    return {
        "type": "object",
        "properties": {
            "term": _TERM_OR_NULL_SCHEMA,
            "must": {"type": "array", "maxItems": 0},
            "should": {"type": "array", "maxItems": 0},
            "must_not": {"type": "array", "maxItems": 0},
        },
        "required": ["term", "must", "should", "must_not"],
    }


def _expression_level_schema(child_schema: dict) -> dict:
    return {
        "type": "object",
        "properties": {
            "term": _TERM_OR_NULL_SCHEMA,
            "must": {"type": "array", "items": child_schema},
            "should": {"type": "array", "items": child_schema},
            "must_not": {"type": "array", "items": child_schema},
        },
        "required": ["term", "must", "should", "must_not"],
    }


_EXPRESSION_SCHEMA = _expression_level_schema(
    _expression_level_schema(
        _expression_level_schema(
            _leaf_level_expression_schema()
        )
    )
)
_expression_grammar = LlamaGrammar.from_json_schema(json.dumps(_EXPRESSION_SCHEMA))

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

    max_tokens = max(500, LLM_CONTEXT_WINDOW - prompt_token_count - LLM_SYNONYM_SAFETY_MARGIN)

    try:
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
    except Exception as e:
        print(f"[llm_model] Synonym generation call failed for a batch of "
              f"{len(words)} word(s): {e} — retrying with smaller batch(es).")
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
              f"word(s): {e} — retrying with smaller batch(es).")
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


# ---------------------------------------------------------------------------
# Job 3 — query parsing into the boolean expression tree (retrieval time)
# ---------------------------------------------------------------------------

_QUERY_SYSTEM_PROMPT = """You are a query parser for a personal photo search engine. The query has already
been spell-corrected. Parse it into a recursive boolean expression tree.

Schema: an Expression has "term" (set only on leaf nodes) OR one or more of "must"/"should"/"must_not"
(lists of child Expressions; empty list if unused). A leaf's "term" has "domain", "value", and "range_end".

GENERAL PRINCIPLE for choosing a domain: PERSON, LOCATION, DATE, and TIME are each for one SPECIFIC,
LITERAL thing — a specific named individual, a specific named place, a specific calendar date/range, or a
specific clock time/range. A general CATEGORY or TYPE of any of these (a role instead of a name, a kind of
place instead of a place name, a visual impression instead of a literal date or clock time) is always
VOCAB instead, even when the word itself sounds related to one of the other domains.

- PERSON: a specific individual's actual name. Relationship or role words used loosely (e.g. "mom", "dad",
  "bride", "groom", "graduate", "teacher", "the birthday boy") are NOT names of a specific person the
  system can look up — they are VOCAB, describing a category of person, not an identity. Only use PERSON
  when the query clearly names one specific person.

- LOCATION: ONLY a specific NAMED real-world place — a city, region/state, or country name (e.g. "Paris",
  "California", "Japan", "Tokyo") — matched against place names derived from each photo's GPS location.
  Generic TYPES or categories of place are VOCAB, not LOCATION, even though they sound place-related — for
  example: beach, park, forest, mountain, lake, river, restaurant, office, kitchen, backyard, stadium,
  market, city street, countryside. Only an actual proper place NAME is LOCATION; a category of place is
  always VOCAB.

- DATE: ONLY the calendar date a PHOTO ITSELF was taken, as literally recorded with the photo (format
  "DD-MM-YYYY", using "range_end" for a range). Never infer a date from an event name or context — e.g.
  "wedding" is a VOCAB event term, not a DATE; do not guess what date someone's wedding happened on. Use
  DATE only when the query directly references a calendar date, month, year, or an explicit date range.

- TIME: ONLY the literal clock time-of-day a photo was taken, as recorded with the photo (24-hour
  "HH:MM", using "range_end" for a range). Prefer TIME (over VOCAB) for words that describe WHEN in the
  day something happened and clearly map to a clock window — "morning", "afternoon", "evening", "night" —
  since the query is really asking about capture time. Do NOT use TIME for purely visual/lighting
  descriptions like "sunset", "sunrise", "dawn", "dusk", "golden hour", or "starry sky" — these describe
  how a photo visually LOOKS (assigned by looking at the image), not a literal clock reading, and the
  system has no way to compute a clock-time window for them — these stay VOCAB.

- VOCAB: the default domain for everything else that describes what a photo shows or how it looks —
  objects, animals, plants, actions, activities, emotions, colors, materials, shapes, events and
  occasions, abstract themes, generic categories of person/place (see above), and visual/lighting
  descriptors that aren't a literal clock time (see above). When in doubt between VOCAB and a more specific
  domain, VOCAB is the safer default — the more specific domains are only for a literal, unambiguous name,
  place, date, or clock time.

must = AND (every child must match). should = OR (at least one child must match). must_not = NOT
(none of these children may match).

The tree can nest at most 3 levels deep: the root's children may themselves have must/should/must_not
children, and THOSE children may too, but that third level must be plain leaf terms only (no further
nesting). This is far more than any realistic photo search query needs — if a query somehow seems to call
for deeper nesting than this, simplify the logical structure to the closest reasonable fit within 3 levels
rather than trying to express it exactly.

IMPORTANT — keep output as SHORT as correctly possible: if a single term is all a query needs, set "term"
directly on the top-level object and leave "must"/"should"/"must_not" as empty arrays — do NOT wrap a
single term inside an unnecessary "must"/"should" array containing one nested object. Only use nested
must/should/must_not when the query genuinely has more than one condition to combine. Unnecessary nesting
wastes output length for no benefit.

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

_FALLBACK_STOPWORDS = {
    "a", "an", "the", "of", "in", "on", "at", "and", "or", "but", "not",
    "photo", "photos", "picture", "pictures", "image", "images", "pic", "pics",
    "me", "my", "give", "show", "find", "with", "from", "for", "to",
}


def _fallback_expression(query: str) -> dict:
    words = [w for w in query.strip().lower().split() if w and w not in _FALLBACK_STOPWORDS]
    if not words:
        words = [query.strip().lower()]

    return {
        "term": None,
        "must": [],
        "should": [
            {"term": {"domain": "VOCAB", "value": w, "range_end": None},
             "must": [], "should": [], "must_not": []}
            for w in words
        ],
        "must_not": [],
    }


def parse_query(query: str) -> dict:
    full_prompt_text = _QUERY_SYSTEM_PROMPT + query
    prompt_token_count = len(_llm.tokenize(full_prompt_text.encode("utf-8")))
    max_tokens = max(500, LLM_CONTEXT_WINDOW - prompt_token_count - LLM_SYNONYM_SAFETY_MARGIN)

    completion = _llm.create_chat_completion(
        messages=[
            {"role": "system", "content": _QUERY_SYSTEM_PROMPT},
            {"role": "user", "content": query},
        ],
        grammar=_expression_grammar,
        max_tokens=max_tokens,
        temperature=0.0,
    )
    raw_text = completion["choices"][0]["message"]["content"]

    try:
        parsed = json.loads(raw_text)
        # Debug visibility: this used to only print on a PARSE FAILURE, which
        # is useless for diagnosing a case like "beach" vs "sea" — a
        # successful-but-wrong parse (e.g. Qwen classifying "beach" as
        # LOCATION instead of VOCAB) produced no signal at all before this.
        # Every query's actual parsed tree now prints here so a
        # misclassification is directly visible instead of having to guess.
        print(f"[llm_model] Parsed query {query!r} -> {json.dumps(parsed)}")
        return parsed
    except Exception as e:
        print(f"[llm_model] Query parse failed: {e} — falling back to a per-word OR match.")
        return _fallback_expression(query)
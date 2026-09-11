"""
Text-only model — Qwen3.5/3.6, used for:
- image processing step 10 (synonym generation, no image needed)
- image retrieval step 2 (query -> boolean expression tree)

Plain Llama, no chat_handler — this never depended on vision support.
Loading starts at the DETECTED hardware tier (cpu_ram_tier — text generation
here is CPU/RAM-bound, not GPU-bound) and steps down only on an actual load
failure.

_llm_lock: this module's _llm object is called from BOTH generate_synonyms()
(part of every image's /analyze processing) and parse_query() (every
search's /parse_query). FastAPI routes here are plain "def", so Starlette
dispatches concurrent requests to worker threads, meaning two threads really
can call create_chat_completion() on the SAME Llama object at once — a
documented llama.cpp thread-safety hazard. The lock serializes access so a
search and an in-flight synonym-generation call queue behind each other
instead of racing.
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
_synonym_grammar = LlamaGrammar.from_json_schema(json.dumps(_SYNONYM_SCHEMA))

# ---------------------------------------------------------------------------
# Query expression tree grammar — JSON Schema, fully inlined (no $ref), 3
# real levels of must/should/must_not nesting then a forced leaf at level 4.
# leaf vs. node are mutually exclusive via oneOf, so a node can never carry
# both a real "term" and non-empty children at once.
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
_NULL_TERM_SCHEMA = {"type": "null"}


def _leaf_variant_schema() -> dict:
    return {
        "type": "object",
        "properties": {
            "term": _TERM_LEAF_SCHEMA,
            "must": {"type": "array", "maxItems": 0},
            "should": {"type": "array", "maxItems": 0},
            "must_not": {"type": "array", "maxItems": 0},
        },
        "required": ["term", "must", "should", "must_not"],
    }


def _node_variant_schema(child_schema: dict) -> dict:
    return {
        "type": "object",
        "properties": {
            "term": _NULL_TERM_SCHEMA,
            "must": {"type": "array", "items": child_schema},
            "should": {"type": "array", "items": child_schema},
            "must_not": {"type": "array", "items": child_schema},
        },
        "required": ["term", "must", "should", "must_not"],
    }


def _expression_level_schema(child_schema: dict) -> dict:
    return {"oneOf": [_leaf_variant_schema(), _node_variant_schema(child_schema)]}


def _leaf_level_expression_schema() -> dict:
    return _leaf_variant_schema()


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
    # Capped, same reasoning as parse_query(): a batch of N words needs at
    # most a few dozen tokens of JSON per word. Uncapped, this formula could
    # allow 6000+ tokens of headroom for a small batch, and a model stuck in
    # a repetition loop will happily use all of it before being cut off —
    # confirmed in testing: an 18-word batch produced a 20,000+ character
    # truncated, unparseable response before this cap was added. Kept as a
    # defense-in-depth backstop alongside the maxItems schema bound above
    # and repeat_penalty below, which target the actual looping tendency
    # more directly.
    max_tokens = min(150 * len(words), max(500, LLM_CONTEXT_WINDOW - prompt_token_count - LLM_SYNONYM_SAFETY_MARGIN))
    try:
        with _llm_lock:
            completion = _llm.create_chat_completion(
                messages=[
                    {"role": "system", "content": _SYNONYM_SYSTEM_PROMPT},
                    {"role": "user", "content": prompt},
                ],
                grammar=_synonym_grammar,
                max_tokens=max_tokens,
                temperature=0.0,
                # Confirmed repetition-loop evidence: a real (successful)
                # batch still produced ['trousers', 'slacks', 'jeans',
                # 'leggings', 'shorts', 'capris', 'leggings', 'sweatpants',
                # 'leggings', 'leggings'] — the same word repeated 4 times
                # in a 10-item list. repeat_penalty discourages the model
                # from re-emitting recently-generated tokens, targeting this
                # tendency at its source rather than only capping the
                # damage after the fact.
                repeat_penalty=1.2,
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


# ---------------------------------------------------------------------------
# Job 3 — query parsing into the boolean expression tree (retrieval time)
#
# CHANGE (accuracy fix): this prompt used to define must/should/must_not in
# one line each and rely on Qwen to infer structural patterns (negation, OR,
# AND) from that definition alone. In testing, negation was the confirmed
# failure: "photos of X without Y" was coming back with Y inside "must"
# instead of "must_not". The prompt had detailed domain-classification rules
# but ZERO worked examples of query structure. This version keeps the domain
# rules (they were working correctly) and adds: (1) an explicit,
# unambiguous list of trigger words/phrases for NOT/OR/AND, and (2) a large
# set of worked input->output examples covering simple and compound queries,
# so there is a concrete pattern to match for every structural case, not
# just an abstract rule to infer from.
# ---------------------------------------------------------------------------
_QUERY_SYSTEM_PROMPT = """You are a query parser for a personal photo search engine. The query has already
been spell-corrected. Parse it into a recursive boolean expression tree.

Schema: an Expression has "term" (set only on leaf nodes) OR one or more of "must"/"should"/"must_not"
(lists of child Expressions; empty list if unused). A leaf's "term" has "domain", "value", and
"range_end".

=====================================================================
DOMAIN CLASSIFICATION
=====================================================================
GENERAL PRINCIPLE for choosing a domain: PERSON, LOCATION, DATE, and TIME are each for one SPECIFIC,
LITERAL thing — a specific named individual, a specific named place, a specific calendar date/range, or
a specific clock time/range. A general CATEGORY or TYPE of any of these (a role instead of a name, a
kind of place instead of a place name, a visual impression instead of a literal date or clock time) is
always VOCAB instead, even when the word itself sounds related to one of the other domains.

- PERSON: a specific individual's actual name. Relationship or role words used loosely (e.g. "mom",
"dad", "bride", "groom", "graduate", "teacher", "the birthday boy") are NOT names of a specific person
the system can look up — they are VOCAB, describing a category of person, not an identity. Only use
PERSON when the query clearly names one specific person. Note: exact domain classification is
re-verified and corrected deterministically downstream against the real registered name/location
lists, so a reasonable best-effort guess here is fine even in ambiguous cases.
- LOCATION: ONLY a specific NAMED real-world place — a city, region/state, or country name (e.g.
"Paris", "California", "Japan", "Tokyo") — matched against place names derived from each photo's GPS
location. Generic TYPES or categories of place are VOCAB, not LOCATION, even though they sound
place-related — for example: beach, park, forest, mountain, lake, river, restaurant, office, kitchen,
backyard, stadium, market, city street, countryside. Only an actual proper place NAME is LOCATION; a
category of place is always VOCAB. Exact domain classification is re-verified and corrected
deterministically downstream against the real registered location list, so a reasonable best-effort
guess here is fine even in ambiguous cases.
- DATE: ONLY the calendar date a PHOTO ITSELF was taken, as literally recorded with the photo. Never
infer a date from an event name or context — e.g. "wedding" is a VOCAB event term, not a DATE; do not
guess what date someone's wedding happened on. Use DATE only when the query directly references a
calendar date, month, or year.
IMPORTANT — you are NOT reliable at calendar arithmetic (e.g. how many days are in a month, which
years are leap years), so NEVER compute or write out the exact last day of a month or year yourself.
Instead, use "value" (always a full "DD-MM-YYYY", day defaulting to 01 if the query doesn't give one)
together with "range_end" in whichever of these forms matches how precise the query actually was —
downstream code computes the real end date correctly for you:
  * Exact single day given: "range_end": null.
  * A month and year given, no day (e.g. "August 2023"): "value": "01-08-2023", "range_end": "08-2023"
    (just two-digit month and four-digit year — do not write a day count here).
  * Only a year given (e.g. "2023"): "value": "01-01-2023", "range_end": "2023" (just the four-digit
    year — do not write a month or day here).
  * An exact day range with both ends fully known: "value" and "range_end" both full "DD-MM-YYYY".
Do not accept a date range with no year specified at all — in that case, treat the term as best-effort
as if it were VOCAB text instead, since a reliable range cannot be constructed.
- TIME: ONLY the literal clock time-of-day a photo was taken, as recorded with the photo (24-hour
"HH:MM", using "range_end" for a range). Prefer TIME (over VOCAB) for words that describe WHEN in the
day something happened and clearly map to a clock window — "morning", "afternoon", "evening", "night"
— since the query is really asking about capture time. Do NOT use TIME for purely visual/lighting
descriptions like "sunset", "sunrise", "dawn", "dusk", "golden hour", or "starry sky" — these describe
how a photo visually LOOKS (assigned by looking at the image), not a literal clock reading, and the
system has no way to compute a clock-time window for them — these stay VOCAB.
- VOCAB: the default domain for everything else that describes what a photo shows or how it looks —
objects, animals, plants, actions, activities, emotions, colors, materials, shapes, events and
occasions, abstract themes, generic categories of person/place (see above), and visual/lighting
descriptors that aren't a literal clock time (see above). When in doubt between VOCAB and a more
specific domain, VOCAB is the safer default — the more specific domains are only for a literal,
unambiguous name, place, date, or clock time.

=====================================================================
LOGICAL STRUCTURE — must / should / must_not
=====================================================================
must = AND (every child must match). should = OR (at least one child must match). must_not = NOT
(none of these children may match).

The tree can nest at most 3 levels deep: the root's children may themselves have must/should/must_not
children, and THOSE children may too, but that third level must be plain leaf terms only (no further
nesting). This is far more than any realistic photo search query needs — if a query somehow seems to
call for deeper nesting than this, simplify the logical structure to the closest reasonable fit within
3 levels rather than trying to express it exactly.

--- Recognizing NOT (must_not) — read this carefully, this is the single most common mistake ---
A term belongs in "must_not" whenever the query says it should NOT be present. This is not limited to
the literal word "not". ALL of the following phrasings mean "put this term in must_not", with no
exceptions:
  * "without X"
  * "excluding X"
  * "except X" / "except for X"
  * "but not X" / "but no X"
  * "no X" (when X is something the photo should lack, e.g. "a party with no cake")
  * "not X"
  * "don't want X" / "I don't want X in it"
The term itself (domain/value) is extracted completely normally — ONLY the must/should/must_not
placement changes because of the trigger word. A negated term must NEVER be placed in "must" or
"should", even when it is the same domain as, or appears right next to, a term that IS wanted. Being
mentioned in the same sentence as a wanted term does not make an excluded term wanted too — check each
term's own trigger word independently.

--- Recognizing OR (should) ---
"or", "either...or", a comma-separated list ending in "or" (e.g. "a beach, a park, or a forest"), and
"any of X, Y" all mean should — only one of the listed alternatives needs to match. Do not put
OR-linked terms in "must" — that would incorrectly require ALL of them to be present at once instead
of just one.

--- Recognizing AND (must) ---
"and", "with", "along with", "as well as", "also", and a bare comma-separated list NOT ending in "or"
(e.g. "a dog, a cat, and a bird") all mean must — every listed term needs to be present. Do not put
AND-linked terms in "should" — that would incorrectly allow a match with only one of them present
instead of requiring all of them.

=====================================================================
WORKED EXAMPLES — study every one of these. Your output for a similar query must follow exactly the
same pattern. These are not illustrative suggestions, they are the literal expected input -> output
mapping for each case shown.
=====================================================================

Example 1 — a single term needs no must/should/must_not wrapping at all:
Query: "photos of a dog"
Output: {"term": {"domain": "VOCAB", "value": "dog", "range_end": null}, "must": [], "should": [], "must_not": []}

Example 2 — simple AND, two required terms:
Query: "photos of a dog and a cat"
Output: {"term": null, "must": [
  {"term": {"domain": "VOCAB", "value": "dog", "range_end": null}, "must": [], "should": [], "must_not": []},
  {"term": {"domain": "VOCAB", "value": "cat", "range_end": null}, "must": [], "should": [], "must_not": []}
], "should": [], "must_not": []}

Example 3 — simple OR, either is acceptable:
Query: "photos of a dog or a cat"
Output: {"term": null, "must": [], "should": [
  {"term": {"domain": "VOCAB", "value": "dog", "range_end": null}, "must": [], "should": [], "must_not": []},
  {"term": {"domain": "VOCAB", "value": "cat", "range_end": null}, "must": [], "should": [], "must_not": []}
], "must_not": []}

Example 4 — a bare exclusion, nothing positively required:
Query: "photos without a dog"
Output: {"term": null, "must": [], "should": [], "must_not": [
  {"term": {"domain": "VOCAB", "value": "dog", "range_end": null}, "must": [], "should": [], "must_not": []}
]}
Note: even though must/should are otherwise empty, must_not is still the correct — and ONLY — place
for "dog" here. Never move a negated term into "must" just because must/should would otherwise be
empty.

Example 5 — AND combined with NOT (this exact shape is where mistakes have happened before — match it
exactly):
Query: "photos of person Riya without person Kabir"
Output: {"term": null,
  "must": [
    {"term": {"domain": "PERSON", "value": "riya", "range_end": null}, "must": [], "should": [], "must_not": []}
  ],
  "should": [],
  "must_not": [
    {"term": {"domain": "PERSON", "value": "kabir", "range_end": null}, "must": [], "should": [], "must_not": []}
  ]}
Riya is wanted, so she goes in "must". Kabir is explicitly excluded by "without", so he goes in
"must_not" — never in "must" alongside Riya, even though both are PERSON terms from the same sentence.

Example 6 — three-way OR:
Query: "photos of a beach, a park, or a forest"
Output: {"term": null, "must": [], "should": [
  {"term": {"domain": "VOCAB", "value": "beach", "range_end": null}, "must": [], "should": [], "must_not": []},
  {"term": {"domain": "VOCAB", "value": "park", "range_end": null}, "must": [], "should": [], "must_not": []},
  {"term": {"domain": "VOCAB", "value": "forest", "range_end": null}, "must": [], "should": [], "must_not": []}
], "must_not": []}
"beach", "park", and "forest" are generic place TYPES, not proper place names, so all three are VOCAB,
not LOCATION — that domain choice is unrelated to the AND/OR/NOT structure decision.

Example 7 — AND of two required terms, plus a NOT:
Query: "photos of a wedding with balloons but no cake"
Output: {"term": null,
  "must": [
    {"term": {"domain": "VOCAB", "value": "wedding", "range_end": null}, "must": [], "should": [], "must_not": []},
    {"term": {"domain": "VOCAB", "value": "balloon", "range_end": null}, "must": [], "should": [], "must_not": []}
  ],
  "should": [],
  "must_not": [
    {"term": {"domain": "VOCAB", "value": "cake", "range_end": null}, "must": [], "should": [], "must_not": []}
  ]}
"but no cake" is a negation trigger phrase, exactly like "without" in Example 5 — "cake" is excluded.

Example 8 — a required term combined with an OR group at the SAME level (must and should can both be
populated on one node — this does not require nesting):
Query: "photos of a dog, taken in December 2023 or January 2024"
Output: {"term": null,
  "must": [
    {"term": {"domain": "VOCAB", "value": "dog", "range_end": null}, "must": [], "should": [], "must_not": []}
  ],
  "should": [
    {"term": {"domain": "DATE", "value": "01-12-2023", "range_end": "12-2023"}, "must": [], "should": [], "must_not": []},
    {"term": {"domain": "DATE", "value": "01-01-2024", "range_end": "01-2024"}, "must": [], "should": [], "must_not": []}
  ],
  "must_not": []}
The dog is unconditionally required (must); the two months are alternatives to each other (should).
"must" and "should" are independent lists on the same node — do not nest a should-only child node
inside a must array just because both lists are non-empty at once.

Example 9 — genuine nesting required (an OR between two alternatives, each of which is itself an AND
of two things — this reaches the maximum allowed depth):
Query: "photos of a birthday, with either a dog and balloons, or a cake and candles"
Output: {"term": null,
  "must": [
    {"term": {"domain": "VOCAB", "value": "birthday", "range_end": null}, "must": [], "should": [], "must_not": []}
  ],
  "should": [
    {"term": null, "must": [
      {"term": {"domain": "VOCAB", "value": "dog", "range_end": null}, "must": [], "should": [], "must_not": []},
      {"term": {"domain": "VOCAB", "value": "balloon", "range_end": null}, "must": [], "should": [], "must_not": []}
    ], "should": [], "must_not": []},
    {"term": null, "must": [
      {"term": {"domain": "VOCAB", "value": "cake", "range_end": null}, "must": [], "should": [], "must_not": []},
      {"term": {"domain": "VOCAB", "value": "candle", "range_end": null}, "must": [], "should": [], "must_not": []}
    ], "should": [], "must_not": []}
  ],
  "must_not": []}
Nest ONLY when a group of terms is itself joined by AND/OR to another group, like here — never nest a
single term for no reason (see Example 1 and the "keep output SHORT" rule below).

Example 10 — negation applied to a whole OR group, not to one of the OR terms itself:
Query: "photos of a dog or a cat, but not at the beach"
Output: {"term": null, "must": [], "should": [
    {"term": {"domain": "VOCAB", "value": "dog", "range_end": null}, "must": [], "should": [], "must_not": []},
    {"term": {"domain": "VOCAB", "value": "cat", "range_end": null}, "must": [], "should": [], "must_not": []}
  ], "must_not": [
    {"term": {"domain": "VOCAB", "value": "beach", "range_end": null}, "must": [], "should": [], "must_not": []}
  ]}
"but not at the beach" excludes "beach" specifically. It does not turn the dog/cat OR into a NOT, and
"beach" does not need its own nested node since it is a single excluded term sitting alongside the
should list at the same level.

Example 11 — a single DATE term, no other conditions, exact day given (including an ordinal
day-of-month — extract only the digits, the suffix carries no meaning):
Query: "photos from 3rd June 2023"
Output: {"term": {"domain": "DATE", "value": "03-06-2023", "range_end": null}, "must": [], "should": [], "must_not": []}

Example 12 — a single DATE term, no other conditions, month+year precision (no day given):
Query: "photos from June 2023"
Output: {"term": {"domain": "DATE", "value": "01-06-2023", "range_end": "06-2023"}, "must": [], "should": [], "must_not": []}

Example 13 — a single DATE term, no other conditions, year-only precision:
Query: "photos from 2024"
Output: {"term": {"domain": "DATE", "value": "01-01-2024", "range_end": "2024"}, "must": [], "should": [], "must_not": []}

Example 14 — a single DATE RANGE term, no other conditions (a range between two different
months/years is still ONE leaf term, not an OR of two dates — do not nest this or split it into
"should"):
Query: "photos taken between June 2023 and August 2024"
Output: {"term": {"domain": "DATE", "value": "01-06-2023", "range_end": "08-2024"}, "must": [], "should": [], "must_not": []}

Example 15 — a single TIME RANGE term, no other conditions:
Query: "photos taken between 9 AM and 3 PM"
Output: {"term": {"domain": "TIME", "value": "09:00", "range_end": "15:00"}, "must": [], "should": [], "must_not": []}

A single DATE or TIME term follows exactly the same "keep output SHORT" rule as any other single
term (see Example 1) — set "term" directly at the top level, do not wrap it in "must" or "should" just
because no other condition was given.

Example 16 — a single LOCATION term, no other conditions (this follows exactly the same pattern as
Example 1 and Examples 11-15 — a bare single term of ANY domain is never wrapped in must/should):
Query: "photos taken in Paris"
Output: {"term": {"domain": "LOCATION", "value": "paris", "range_end": null}, "must": [], "should": [], "must_not": []}

Example 17 — AND across two different domains (PERSON + LOCATION):
Query: "photos of chinmaya in Paris"
Output: {"term": null, "must": [
  {"term": {"domain": "PERSON", "value": "chinmaya", "range_end": null}, "must": [], "should": [], "must_not": []},
  {"term": {"domain": "LOCATION", "value": "paris", "range_end": null}, "must": [], "should": [], "must_not": []}
], "should": [], "must_not": []}

=====================================================================
OUTPUT RULES
=====================================================================
IMPORTANT — keep output as SHORT as correctly possible: if a single term is all a query needs, set
"term" directly on the top-level object and leave "must"/"should"/"must_not" as empty arrays — do NOT
wrap a single term inside an unnecessary "must"/"should" array containing one nested object (see
Example 1). Only use nested must/should/must_not when the query genuinely has more than one condition
to combine. Unnecessary nesting wastes output length for no benefit.

Word-form rule: normalize VOCAB terms toward their base singular form when generating "value" (e.g.
"ships" -> "ship") — downstream lookup handles exact word-form matching separately, your job is just
correct domain/value/range extraction and correct AND/OR/NOT structure.

Only extract words that actually help describe the image (skip "give", "me", "images", "of", "photos"
unless "photos" is itself the subject, e.g. "a person taking photos").

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
    # Reverted: this used to also accept a known_names list and prepend it to
    # the prompt, in an attempt to get Qwen to self-correct PERSON
    # classification. Even given the exact registered name list including
    # "Amma", it still classified "amma" as VOCAB. Prompt-based grounding
    # wasn't reliable even in the best case. Domain correctness now lives
    # entirely in SearchService.classifyAndEvaluate() on the Java side,
    # deterministically re-checked against real registered data.
    full_prompt_text = _QUERY_SYSTEM_PROMPT + query
    prompt_token_count = len(_llm.tokenize(full_prompt_text.encode("utf-8")))
    # Capped at 1000 regardless of context-window headroom — a query
    # expression tree needs a few hundred tokens at most. Confirmed real
    # exposure without this cap: a ~7000-token ceiling here let one confused
    # generation hang for 15+ minutes on CPU-bound hardware before this fix.
    # The prompt above is considerably longer than before (the worked
    # examples add real token count), but LLM_CONTEXT_WINDOW (8192) has
    # comfortable headroom for that — this cap is about bounding worst-case
    # generation length, not prompt length, and doesn't need to change.
    max_tokens = min(1000, max(500, LLM_CONTEXT_WINDOW - prompt_token_count - LLM_SYNONYM_SAFETY_MARGIN))
    with _llm_lock:
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
        print(f"[llm_model] Parsed query {query!r} -> {json.dumps(parsed)}")
        return parsed
    except Exception as e:
        print(f"[llm_model] Query parse failed: {e} — falling back to a per-word OR match.")
        return _fallback_expression(query)
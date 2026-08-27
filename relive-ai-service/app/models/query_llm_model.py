import json
import os

from llama_cpp import Llama, LlamaGrammar

from app.config import (
    QUERY_LLM_MODEL_BY_TIER,
    QUERY_LLM_CONTEXT_WINDOW,
    QUERY_LLM_MAX_NEW_TOKENS,
)
from app.hardware import Tier, cpu_ram_tier, describe as describe_hardware

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]

_REPO_BY_FILENAME = {
    "Qwen3.5-4B-Q4_K_M.gguf": "unsloth/Qwen3.5-4B-GGUF",
    "Qwen3.5-9B-Q4_K_M.gguf": "unsloth/Qwen3.5-9B-GGUF",
    "Qwen3.8-27B-Instruct-Q4_K_M.gguf": "unsloth/Qwen3.8-27B-Instruct-GGUF",
}

RESPONSE_SCHEMA = {
    "type": "object",
    "properties": {
        "must_include": {"type": "array", "items": {"type": "string"}},
        "must_exclude": {"type": "array", "items": {"type": "string"}},
        "any_of": {
            "type": "array",
            "items": {"type": "array", "items": {"type": "string"}},
        },
        "persons": {"type": "array", "items": {"type": "string"}},
        "locations": {"type": "array", "items": {"type": "string"}},
        "year": {"type": ["integer", "null"]},
        "month": {"type": ["integer", "null"]},
        "time_of_day": {"type": ["string", "null"]},
        "min_people": {"type": ["integer", "null"]},
        "free_text_semantic": {"type": "string"},
    },
    "required": [
        "must_include", "must_exclude", "any_of", "persons", "locations",
        "year", "month", "time_of_day", "min_people", "free_text_semantic",
    ],
}

SYSTEM_PROMPT = """You are a query parser for a personal photo search engine.
Parse the user's natural-language search query into structured JSON.

Rules:
- must_include: things that MUST all be present (AND logic), e.g. objects,
  activities, general concepts. Do not include person names or place names here.
- must_exclude: things that must NOT be present (from "without", "no", "except",
  "not", negation of any kind).
- any_of: groups of alternatives (OR logic). Each inner list is one OR-group,
  e.g. "cat or dog" -> [["cat", "dog"]]. Usually empty.
- persons: any words that look like a person's name (proper nouns referring to
  people). Include even if you're not fully sure — a downstream fuzzy-match
  step against the user's actual contacts will confirm or reject it.
- locations: any place names mentioned.
- year / month: extracted from the query if a date is mentioned, else null.
- time_of_day: "day" or "night" if implied by the query, else null.
- min_people: a minimum face/person count if implied (e.g. "group photo" -> 2,
  "just me" / "selfie" -> 0), else null.
- free_text_semantic: the residual descriptive meaning of the query, in
  natural language, for semantic embedding search — usually close to the
  original query with names/dates/locations/negation stripped out.

Always return valid JSON matching the schema. Never include commentary."""


def _try_load(model_filename: str):
    repo_id = _REPO_BY_FILENAME.get(model_filename)
    if repo_id is None:
        raise RuntimeError(f"No known HuggingFace repo for GGUF file: {model_filename}")

    print(f"Loading query-parser LLM ({repo_id}/{model_filename})...")
    llm = Llama.from_pretrained(
        repo_id=repo_id,
        filename=model_filename,
        n_ctx=QUERY_LLM_CONTEXT_WINDOW,
        n_threads=os.cpu_count(),
        verbose=False,
    )
    print(f"Query-parser LLM loaded: {model_filename}")
    return llm


def _load_with_fallback():
    start_tier = cpu_ram_tier()
    start_idx = _TIER_ORDER.index(start_tier)
    last_error = None

    for tier in _TIER_ORDER[start_idx:]:
        model_filename = QUERY_LLM_MODEL_BY_TIER[tier]
        try:
            return _try_load(model_filename)
        except Exception as e:
            print(f"Query-parser LLM load failed for tier={tier} ({model_filename}): {e}")
            last_error = e
            continue

    raise RuntimeError(
        f"Could not load any query-parser LLM tier. Hardware: {describe_hardware()}. "
        f"Last error: {last_error}"
    )


_llm = _load_with_fallback()
_grammar = LlamaGrammar.from_json_schema(json.dumps(RESPONSE_SCHEMA))

_DEFAULTS = {
    "must_include": [],
    "must_exclude": [],
    "any_of": [],
    "persons": [],
    "locations": [],
    "year": None,
    "month": None,
    "time_of_day": None,
    "min_people": None,
    "free_text_semantic": "",
}


def parse_query(query: str) -> dict:

    completion = _llm.create_chat_completion(
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": query},
        ],
        grammar=_grammar,
        max_tokens=QUERY_LLM_MAX_NEW_TOKENS,
        temperature=0.0,
    )

    raw_text = completion["choices"][0]["message"]["content"]

    try:
        parsed = json.loads(raw_text)
    except Exception as e:
        print(f"Query LLM JSON parse failed, falling back to raw query: {e}")
        parsed = {}

    result = dict(_DEFAULTS)
    for key, default in _DEFAULTS.items():
        result[key] = parsed.get(key, default)
        if result[key] is None and isinstance(default, list):
            result[key] = []

    if not result["free_text_semantic"]:
        result["free_text_semantic"] = query

    # Lowercase normalize the string lists for consistent downstream matching.
    result["must_include"] = [str(v).strip().lower() for v in result["must_include"] if str(v).strip()]
    result["must_exclude"] = [str(v).strip().lower() for v in result["must_exclude"] if str(v).strip()]
    result["any_of"] = [
        [str(v).strip().lower() for v in group if str(v).strip()]
        for group in result["any_of"]
    ]
    result["persons"] = [str(v).strip() for v in result["persons"] if str(v).strip()]
    result["locations"] = [str(v).strip().lower() for v in result["locations"] if str(v).strip()]

    return result


def verify_candidate(query: str, rich_description: str) -> bool:

    prompt = (
        f"Query: {query}\n"
        f"Photo description: {rich_description}\n\n"
        "Does this photo satisfy the query? Consider all conditions "
        "including any negation. Respond with only one word: yes or no."
    )

    completion = _llm.create_chat_completion(
        messages=[{"role": "user", "content": prompt}],
        max_tokens=5,
        temperature=0.0,
    )

    answer = completion["choices"][0]["message"]["content"].strip().lower()
    return answer.startswith("y")
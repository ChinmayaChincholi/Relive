import json
import re

import torch
from PIL import Image
from transformers import AutoModelForCausalLM, AutoModelForImageTextToText, AutoProcessor

from app.config import VLM_MODEL_BY_TIER, VLM_MAX_NEW_TOKENS
from app.hardware import Tier, describe as describe_hardware

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]

STRUCTURED_PROMPT = (
    "Describe this photo in detail. Respond ONLY with a single JSON object "
    "with exactly these keys, and no other text:\n"
    "{\n"
    '  "description": "2-3 sentence natural-language description of the scene",\n'
    '  "objects": ["list", "of", "distinct", "objects", "visible"],\n'
    '  "activities": ["list", "of", "activities", "or", "actions", "happening"],\n'
    '  "setting": "short phrase for where this is, e.g. \'kitchen\', \'beach\', \'city street\'",\n'
    '  "event_type_guess": "best guess at the occasion, e.g. \'birthday party\', '
    '\'casual outing\', \'wedding\', \'none\'",\n'
    '  "mood": "one or two words for the overall mood/atmosphere",\n'
    '  "colors": ["dominant", "colors", "in", "the", "image"],\n'
    '  "indoor_outdoor": "indoor or outdoor",\n'
    '  "time_of_day": "day or night, based on the actual lighting in the scene",\n'
    '  "ocr_text": "any readable text/signage visible in the image, or empty string"\n'
    "}"
)


def _try_load(model_name: str):
    print(f"Loading VLM ({model_name})...")
    processor = AutoProcessor.from_pretrained(model_name, trust_remote_code=True)

    load_dtype = torch.float16 if device.type == "cuda" else torch.float32

    try:
        model = AutoModelForImageTextToText.from_pretrained(
            model_name,
            dtype=load_dtype,
            trust_remote_code=True,
        )
    except (ValueError, KeyError):
        model = AutoModelForCausalLM.from_pretrained(
            model_name,
            dtype=load_dtype,
            trust_remote_code=True,
        )

    model.to(device)
    model.eval()
    print(f"VLM loaded: {model_name}")
    return processor, model

def _load_with_fallback():
    from app.hardware import gpu_tier

    start_tier = gpu_tier()
    start_idx = _TIER_ORDER.index(start_tier)
    last_error = None

    for tier in _TIER_ORDER[start_idx:]:
        model_name = VLM_MODEL_BY_TIER[tier]
        try:
            return _try_load(model_name)
        except Exception as e:
            print(f"VLM load failed for tier={tier} ({model_name}): {e}")
            last_error = e
            continue

    raise RuntimeError(
        f"Could not load any VLM tier. Hardware: {describe_hardware()}. "
        f"Last error: {last_error}"
    )


processor, model = _load_with_fallback()


def _extract_json(text: str) -> dict:
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        raise ValueError(f"No JSON object found in VLM output: {text[:200]}")
    return json.loads(match.group(0))


_REQUIRED_KEYS = {
    "description": "",
    "objects": [],
    "activities": [],
    "setting": "",
    "event_type_guess": "none",
    "mood": "",
    "colors": [],
    "indoor_outdoor": "outdoor",
    "time_of_day": "day",
    "ocr_text": "",
}


def _fill_defaults(parsed: dict) -> dict:
    result = dict(_REQUIRED_KEYS)
    for key, default in _REQUIRED_KEYS.items():
        value = parsed.get(key, default)
        if value is None:
            value = default
        result[key] = value
    return result


def describe_image(image: Image.Image) -> dict:
    messages = [
        {
            "role": "user",
            "content": [
                {"type": "image", "image": image},
                {"type": "text", "text": STRUCTURED_PROMPT},
            ],
        }
    ]

    inputs = processor.apply_chat_template(
        messages,
        tokenize=True,
        add_generation_prompt=True,
        return_tensors="pt",
        return_dict=True,
    ).to(device)

    with torch.no_grad():
        output_ids = model.generate(
            **inputs,
            max_new_tokens=VLM_MAX_NEW_TOKENS,
            do_sample=False,
        )

    generated = output_ids[:, inputs["input_ids"].shape[1]:]
    raw_text = processor.batch_decode(generated, skip_special_tokens=True)[0]

    try:
        parsed = _extract_json(raw_text)
    except Exception as e:
        print(f"VLM JSON parse failed, using fallback structure: {e}")
        parsed = {"description": raw_text.strip()[:500]}

    result = _fill_defaults(parsed)

    for list_key in ("objects", "activities", "colors"):
        if isinstance(result[list_key], str):
            result[list_key] = [result[list_key]] if result[list_key] else []
        result[list_key] = [str(v).strip().lower() for v in result[list_key] if str(v).strip()]

    result["indoor_outdoor"] = str(result["indoor_outdoor"]).strip().lower()
    if result["indoor_outdoor"] not in ("indoor", "outdoor"):
        result["indoor_outdoor"] = "outdoor"

    result["time_of_day"] = str(result["time_of_day"]).strip().lower()
    if result["time_of_day"] not in ("day", "night"):
        result["time_of_day"] = "day"

    return result
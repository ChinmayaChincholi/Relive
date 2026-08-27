import numpy as np
from PIL import Image

from app.config import (
    OBJECT_DETECTION_MODEL_BY_TIER,
    OBJECT_DETECTION_CONFIDENCE,
    OBJECT_DETECTION_ALLOW_PML_XL,
)
from app.hardware import Tier, gpu_tier, describe as describe_hardware

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]

_MODEL_CLASS_BY_NAME = {
    "rfdetr-nano": "RFDETRNano",
    "rfdetr-small": "RFDETRSmall",
    "rfdetr-medium": "RFDETRMedium",
    "rfdetr-large": "RFDETRLarge",
    "rfdetr-xlarge": "RFDETRXLarge",   # PML 1.0 — requires rfdetr[plus]
    "rfdetr-2xlarge": "RFDETR2XLarge",  # PML 1.0 — requires rfdetr[plus]
}


def _resolve_model_name(tier: str) -> str:
    name = OBJECT_DETECTION_MODEL_BY_TIER[tier]
    if tier == Tier.HIGH and OBJECT_DETECTION_ALLOW_PML_XL:
        name = "rfdetr-2xlarge"
    return name


def _try_load(model_name: str):
    import rfdetr

    class_name = _MODEL_CLASS_BY_NAME[model_name]
    model_cls = getattr(rfdetr, class_name)
    print(f"Loading RF-DETR object detection model ({model_name})...")
    detector = model_cls()
    detector.optimize_for_inference()
    print(f"RF-DETR loaded: {model_name}")
    return detector


def _load_with_fallback():
    start_tier = gpu_tier()
    start_idx = _TIER_ORDER.index(start_tier)
    last_error = None

    for tier in _TIER_ORDER[start_idx:]:
        model_name = _resolve_model_name(tier)
        try:
            return _try_load(model_name), model_name
        except Exception as e:
            print(f"RF-DETR load failed for tier={tier} ({model_name}): {e}")
            last_error = e
            continue

    raise RuntimeError(
        f"Could not load any RF-DETR tier. Hardware: {describe_hardware()}. "
        f"Last error: {last_error}"
    )


_detector, _loaded_model_name = _load_with_fallback()

def detect_objects(image_pil: Image.Image):
    try:
        detections = _detector.predict(image_pil, threshold=OBJECT_DETECTION_CONFIDENCE)
    except Exception as e:
        print(f"Object detection error: {e}")
        return []

    if detections.data is None or "class_name" not in detections.data:
        return []

    names = detections.data["class_name"]
    confidences = detections.confidence

    detected = set()
    for name, confidence in zip(names, confidences):
        if confidence >= OBJECT_DETECTION_CONFIDENCE and name:
            detected.add(str(name).lower())

    return list(detected)
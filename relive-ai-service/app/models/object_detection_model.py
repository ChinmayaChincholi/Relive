"""
Object detection fallback (image processing step 12). Two families now:
RF-DETR for LOW/MID (Apache 2.0, via the `rfdetr` package, unchanged
mechanism from before), D-FINE-X for HIGH (MIT, via transformers'
DFineForObjectDetection — no extra package, no PML licensing gate).
Always attempts HIGH first regardless of detected hardware tier, stepping
down only on an actual load failure (see design discussion — detection can
under-report real capability).
"""

import numpy as np
from PIL import Image

from app.config import (
    OBJECT_DETECTION_FAMILY_BY_TIER,
    OBJECT_DETECTION_MODEL_BY_TIER,
    OBJECT_DETECTION_CONFIDENCE,
    D_FINE_HF_REPO_BY_MODEL,
)

from app.hardware import Tier, gpu_tier, describe as describe_hardware

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]

_RFDETR_CLASS_BY_NAME = {
    "rfdetr-nano": "RFDETRNano",
    "rfdetr-small": "RFDETRSmall",
    "rfdetr-medium": "RFDETRMedium",
    "rfdetr-large": "RFDETRLarge",
}


def _try_load_rfdetr(model_name: str):
    import rfdetr
    class_name = _RFDETR_CLASS_BY_NAME[model_name]
    model_cls = getattr(rfdetr, class_name)
    print(f"[object_detection] Loading RF-DETR ({model_name})...")
    detector = model_cls()
    detector.optimize_for_inference()
    print(f"[object_detection] Loaded RF-DETR ({model_name}).")
    return ("rfdetr", detector)


def _try_load_dfine(model_name: str):
    import torch
    from transformers import DFineForObjectDetection, AutoImageProcessor

    repo_id = D_FINE_HF_REPO_BY_MODEL[model_name]
    print(f"[object_detection] Loading D-FINE ({repo_id})...")
    processor = AutoImageProcessor.from_pretrained(repo_id)
    model = DFineForObjectDetection.from_pretrained(repo_id)
    model.eval()
    print(f"[object_detection] Loaded D-FINE ({repo_id}).")
    return ("dfine", (model, processor))


def _try_load(tier: str):
    family = OBJECT_DETECTION_FAMILY_BY_TIER[tier]
    model_name = OBJECT_DETECTION_MODEL_BY_TIER[tier]
    if family == "rfdetr":
        return _try_load_rfdetr(model_name)
    elif family == "dfine":
        return _try_load_dfine(model_name)
    raise ValueError(f"Unknown object detection family: {family}")


def _load_with_fallback():
    start_tier = gpu_tier()  # object detection is GPU-bound when a GPU exists
    start_idx = _TIER_ORDER.index(start_tier)
    last_error = None

    print(f"[object_detection] Detected tier: {start_tier}. Starting load attempt there.")

    for tier in _TIER_ORDER[start_idx:]:
        try:
            return _try_load(tier)
        except Exception as e:
            print(f"[object_detection] Load failed for tier={tier}: {e}")
            last_error = e
            continue

    raise RuntimeError(
        f"Could not load any object detection tier from {start_tier} downward. "
        f"Hardware: {describe_hardware()}. Last error: {last_error}"
    )


_backend, _detector = _load_with_fallback()


def _detect_rfdetr(image_pil: Image.Image) -> set[str]:
    detections = _detector.predict(image_pil, threshold=OBJECT_DETECTION_CONFIDENCE)
    if detections.data is None or "class_name" not in detections.data:
        return set()
    names = detections.data["class_name"]
    confidences = detections.confidence
    return {
        str(name).lower()
        for name, confidence in zip(names, confidences)
        if confidence >= OBJECT_DETECTION_CONFIDENCE and name
    }


def _detect_dfine(image_pil: Image.Image) -> set[str]:
    import torch
    model, processor = _detector
    inputs = processor(images=image_pil, return_tensors="pt")
    with torch.no_grad():
        outputs = model(**inputs)
    results = processor.post_process_object_detection(
        outputs, target_sizes=[(image_pil.height, image_pil.width)], threshold=OBJECT_DETECTION_CONFIDENCE
    )
    detected = set()
    for result in results:
        for label_id in result["labels"]:
            label = model.config.id2label[label_id.item()]
            detected.add(str(label).lower())
    return detected


def detect_objects(image_pil: Image.Image) -> list[str]:
    try:
        if _backend == "rfdetr":
            detected = _detect_rfdetr(image_pil)
        else:
            detected = _detect_dfine(image_pil)
        return sorted(detected)
    except Exception as e:
        print(f"[object_detection] Detection error: {e}")
        return []
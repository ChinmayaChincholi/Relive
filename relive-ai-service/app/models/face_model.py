import cv2
import numpy as np
import insightface

from app.config import (
    FACE_MODEL_PACK_BY_TIER,
    FACE_DETECTION_CONFIDENCE,
    MIN_FACE_SIZE,
    MAX_IMAGE_DIMENSION,
)
from app.hardware import Tier, gpu_tier, describe as describe_hardware

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]
_app = None


def _try_load(pack_name: str):
    print(f"[face_model] Attempting to load InsightFace pack '{pack_name}'...")
    face_app = insightface.app.FaceAnalysis(name=pack_name)
    face_app.prepare(ctx_id=0, det_thresh=FACE_DETECTION_CONFIDENCE)
    print(f"[face_model] Loaded InsightFace pack '{pack_name}'.")
    return face_app


def _load_with_fallback():
    start_tier = gpu_tier()  # face detection/recognition is GPU-bound when a GPU exists
    start_idx = _TIER_ORDER.index(start_tier)
    last_error = None

    print(f"[face_model] Detected tier: {start_tier}. Starting load attempt there.")

    for tier in _TIER_ORDER[start_idx:]:
        pack_name = FACE_MODEL_PACK_BY_TIER[tier]
        try:
            return _try_load(pack_name)
        except Exception as e:
            print(f"[face_model] Load failed for tier={tier} ({pack_name}): {e}")
            last_error = e
            continue
    raise RuntimeError(
        f"Could not load any face model tier from {start_tier} downward. "
        f"Hardware: {describe_hardware()}. Last error: {last_error}"
    )


_app = _load_with_fallback()


def _load_and_resize(image_path: str):
    img = cv2.imread(image_path)
    if img is None:
        raise ValueError(f"Could not read image: {image_path}")
    h, w = img.shape[:2]
    scale = MAX_IMAGE_DIMENSION / max(h, w)
    if scale < 1.0:
        img = cv2.resize(img, (int(w * scale), int(h * scale)))
    return img


def extract_faces_from_image(image_path: str) -> list[dict]:
    """Always returns real embeddings — this is now the ONLY face extraction
    path; the old count-only path (count_faces) has been removed since the
    main import pipeline needs real embeddings to do anything useful with
    faces, not just a headcount."""
    img = _load_and_resize(image_path)
    faces = _app.get(img)

    results = []
    for i, face in enumerate(faces):
        box = face.bbox.astype(int)
        w = box[2] - box[0]
        h = box[3] - box[1]
        if w < MIN_FACE_SIZE or h < MIN_FACE_SIZE:
            continue

        crop = img[max(0, box[1]):box[3], max(0, box[0]):box[2]]
        crop_path = f"{image_path}.face{i}.jpg"
        cv2.imwrite(crop_path, crop)

        embedding = face.normed_embedding.tolist() if face.normed_embedding is not None else None
        if embedding is None:
            continue

        results.append({
            "crop_path": crop_path,
            "embedding": embedding,
            "confidence": float(face.det_score),
        })

    return results
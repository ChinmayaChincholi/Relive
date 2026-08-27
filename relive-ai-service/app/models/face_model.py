import os
import uuid
import numpy as np
from pathlib import Path
from PIL import Image
import insightface

from app.config import (
    FACE_MODEL_PACK_BY_TIER,
    FACE_DETECTION_CONFIDENCE,
    MIN_FACE_SIZE,
    MAX_IMAGE_DIMENSION,
)
from app.hardware import Tier, gpu_tier, has_gpu, describe as describe_hardware

RELIVE_DATA_DIR = os.environ.get("RELIVE_DATA_DIR", str(Path.home() / ".relive"))
FACE_CROPS_DIR = os.path.join(RELIVE_DATA_DIR, "face_crops")
os.makedirs(FACE_CROPS_DIR, exist_ok=True)
print(f"Face crops will be saved to: {FACE_CROPS_DIR}")

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]


def _try_load(pack_name: str):
    print(f"Loading InsightFace model pack ({pack_name})...")
    ctx_id = 0 if has_gpu() else -1  # -1 = CPU in InsightFace's convention
    app_ = insightface.app.FaceAnalysis(name=pack_name)
    app_.prepare(ctx_id=ctx_id, det_size=(640, 640))
    print(f"InsightFace pack loaded: {pack_name}")
    return app_


def _load_with_fallback():
    start_tier = gpu_tier()
    start_idx = _TIER_ORDER.index(start_tier)
    last_error = None

    for tier in _TIER_ORDER[start_idx:]:
        pack_name = FACE_MODEL_PACK_BY_TIER[tier]
        try:
            return _try_load(pack_name)
        except Exception as e:
            print(f"InsightFace load failed for tier={tier} ({pack_name}): {e}")
            last_error = e
            continue

    raise RuntimeError(
        f"Could not load any InsightFace model pack. Hardware: {describe_hardware()}. "
        f"Last error: {last_error}"
    )


face_app = _load_with_fallback()


def _load_and_resize(image_path: str):
    pil_image = Image.open(image_path).convert("RGB")
    w, h = pil_image.size
    if max(w, h) > MAX_IMAGE_DIMENSION:
        scale = MAX_IMAGE_DIMENSION / max(w, h)
        pil_image = pil_image.resize((int(w * scale), int(h * scale)), Image.LANCZOS)
    return pil_image, np.array(pil_image, dtype=np.uint8)


def count_faces(image_path: str) -> int:
    try:
        _, img_array = _load_and_resize(image_path)
        # InsightFace expects BGR (OpenCV convention)
        faces = face_app.get(img_array[:, :, ::-1])
        return sum(1 for f in faces if float(f.det_score) >= FACE_DETECTION_CONFIDENCE)
    except Exception as e:
        print(f"Face count error on {image_path}: {e}")
        return 0


def extract_faces_from_image(image_path: str):
    try:
        pil_image, img_array = _load_and_resize(image_path)
        img_height, img_width = img_array.shape[:2]
    except Exception as e:
        print(f"Error opening image {image_path}: {e}")
        return []

    try:
        faces = face_app.get(img_array[:, :, ::-1])  # BGR for InsightFace
    except Exception as e:
        print(f"InsightFace detection error on {image_path}: {e}")
        return []

    results_list = []

    for face in faces:
        confidence = float(face.det_score)
        if confidence < FACE_DETECTION_CONFIDENCE:
            continue

        x1, y1, x2, y2 = map(int, face.bbox)
        x1 = max(0, x1)
        y1 = max(0, y1)
        x2 = min(img_width, x2)
        y2 = min(img_height, y2)

        w = x2 - x1
        h = y2 - y1

        if w < MIN_FACE_SIZE or h < MIN_FACE_SIZE:
            continue

        face_area_ratio = (w * h) / (img_width * img_height)
        if face_area_ratio > 0.9:
            continue

        # ArcFace embedding is already computed by face_app.get() (512-dim,
        # L2-normalized) — no separate embedding call needed, unlike the old
        # DeepFace.represent() step.
        embedding = face.normed_embedding
        if embedding is None:
            continue

        padding = int(max(w, h) * 0.3)
        cx1 = max(0, x1 - padding)
        cy1 = max(0, y1 - padding)
        cx2 = min(img_width, x2 + padding)
        cy2 = min(img_height, y2 + padding)

        pil_crop = pil_image.crop((cx1, cy1, cx2, cy2))
        pil_crop_resized = pil_crop.resize((128, 128), Image.LANCZOS)
        crop_filename = f"{uuid.uuid4().hex}.jpg"

        crop_abs_path = os.path.join(FACE_CROPS_DIR, crop_filename)
        pil_crop_resized.save(crop_abs_path, "JPEG")

        crop_rel_path = os.path.join("face_crops", crop_filename)

        results_list.append({
            "crop_path": crop_rel_path,
            "embedding": embedding.tolist(),
            "confidence": confidence,
        })

    return results_list
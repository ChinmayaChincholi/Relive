import os
import uuid
import numpy as np
from pathlib import Path
from PIL import Image
from deepface import DeepFace
from ultralytics import YOLO
from app.config import (
    YOLO_FACE_MODEL,
    YOLO_FACE_CONFIDENCE,
    FACE_RECOGNITION_MODEL,
    MIN_FACE_SIZE,
    MAX_IMAGE_DIMENSION
)

# Write face crops to ~/.relive/face_crops/ so the Spring backend can serve them.
# The backend resolves crop paths against ${relive.data.dir} = ~/.relive/.
# Override with RELIVE_DATA_DIR env var if needed.
RELIVE_DATA_DIR = os.environ.get("RELIVE_DATA_DIR", str(Path.home() / ".relive"))
FACE_CROPS_DIR = os.path.join(RELIVE_DATA_DIR, "face_crops")
os.makedirs(FACE_CROPS_DIR, exist_ok=True)

print(f"Face crops will be saved to: {FACE_CROPS_DIR}")
print(f"Loading YOLO face model ({YOLO_FACE_MODEL})...")
yolo_face = YOLO(YOLO_FACE_MODEL)
print("YOLO face model loaded.")

def extract_faces_from_image(image_path: str):

    try:
        pil_image = Image.open(image_path).convert("RGB")

        w, h = pil_image.size
        if max(w, h) > MAX_IMAGE_DIMENSION:
            scale = MAX_IMAGE_DIMENSION / max(w, h)
            pil_image = pil_image.resize(
                (int(w * scale), int(h * scale)),
                Image.LANCZOS
            )

        img_array = np.array(pil_image, dtype=np.uint8)
        img_height, img_width = img_array.shape[:2]

    except Exception as e:
        print(f"Error opening image {image_path}: {e}")
        return []

    try:
        results = yolo_face(img_array, verbose=False)
    except Exception as e:
        print(f"YOLO detection error on {image_path}: {e}")
        return []

    if not results or len(results[0].boxes) == 0:
        return []

    detections = results[0].boxes
    results_list = []

    for box in detections:

        confidence = float(box.conf[0])
        if confidence < YOLO_FACE_CONFIDENCE:
            continue

        x1, y1, x2, y2 = map(int, box.xyxy[0].tolist())
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

        padding = int(max(w, h) * 0.3)
        cx1 = max(0, x1 - padding)
        cy1 = max(0, y1 - padding)
        cx2 = min(img_width, x2 + padding)
        cy2 = min(img_height, y2 + padding)

        pil_crop = pil_image.crop((cx1, cy1, cx2, cy2))

        try:
            crop_array = np.array(
                pil_crop.resize((160, 160)),
                dtype=np.uint8
            )

            face_objs = DeepFace.represent(
                img_path=crop_array,
                model_name=FACE_RECOGNITION_MODEL,
                detector_backend="skip",
                enforce_detection=False
            )

            if not face_objs:
                continue

            embedding = face_objs[0].get("embedding")
            if not embedding:
                continue

        except Exception as e:
            print(f"Embedding error for face in {image_path}: {e}")
            continue

        pil_crop_resized = pil_crop.resize((128, 128), Image.LANCZOS)
        crop_filename = f"{uuid.uuid4().hex}.jpg"

        # Full absolute path for saving the file
        crop_abs_path = os.path.join(FACE_CROPS_DIR, crop_filename)
        pil_crop_resized.save(crop_abs_path, "JPEG")

        # Relative path stored in DB: "face_crops/<filename>"
        # The backend resolves this against ~/.relive/ → ~/.relive/face_crops/<filename>
        crop_rel_path = os.path.join("face_crops", crop_filename)

        results_list.append({
            "crop_path": crop_rel_path,
            "embedding": embedding,
            "confidence": confidence
        })

    return results_list
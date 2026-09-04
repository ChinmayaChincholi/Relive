from PIL import Image

from app.models.vlm_model import generate_vocabulary
from app.models.llm_model import generate_synonyms
from app.models.object_detection_model import detect_objects
from app.models.face_model import extract_faces_from_image

from app.utils.image_utils import (
    resize_image,
    extract_exif_date,
    extract_exif_location,
)
from app.utils.lemmatizer import lemmatize


def analyze_image(file_path: str, media_id: int) -> dict:
    """
    Image processing pipeline, steps 2-5 and 9-12 (steps 6-8, face
    detection/recognition/clustering, are handled separately by
    FaceService.java calling /extract_faces — see routes/faces.py).
    """
    original_image = Image.open(file_path)

    exif_date = extract_exif_date(original_image)
    exif_location = extract_exif_location(original_image)  # dict or None now

    image = original_image.convert("RGB")
    image = resize_image(image)

    # Step 9 — 22-category vocabulary generation (one cached image encode).
    vlm_words = set(generate_vocabulary(image))

    # Step 10 — synonym/related-word expansion, one batched call.
    synonym_map = generate_synonyms(list(vlm_words))
    all_synonyms = {w for related in synonym_map.values() for w in related}

    # Step 12 — object detection fallback, only adds words the VLM missed.
    detected_objects = set(detect_objects(image))
    fallback_only = detected_objects - vlm_words - all_synonyms

    # Lemmatize every word before storage so import-time keys and query-time
    # lookups (LemmatizerUtil.java, mirrored rules) land on the same
    # canonical form — this is a safety net beyond the prompt's own
    # singular/lowercase instructions, not a replacement for them.
    vlm_words = {lemmatize(w) for w in vlm_words}
    all_synonyms = {lemmatize(w) for w in all_synonyms}
    fallback_only = {lemmatize(w) for w in fallback_only}

    vocabulary_words = sorted(vlm_words | all_synonyms | fallback_only)

    return {
        "vocabulary_words": vocabulary_words,
        "date_taken": exif_date,
        "location": exif_location,   # {city, region, country, display} or None
    }
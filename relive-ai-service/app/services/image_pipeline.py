from PIL import Image

from app.services.vector_index import add_image_vector, add_text_vector
from app.models.vlm_model import describe_image
from app.models.clip_model import get_image_embedding
from app.models.text_embedding_model import get_text_embedding
from app.models.object_detection_model import detect_objects
from app.models.face_model import count_faces

from app.utils.image_utils import (
    resize_image,
    extract_exif_date,
    extract_exif_location,
)

from app.utils.text_utils import normalize_object


def _compose_searchable_text(vlm_result: dict) -> str:
    parts = [vlm_result["description"].strip()]

    if vlm_result["setting"]:
        parts.append(f"Setting: {vlm_result['setting']}.")
    if vlm_result["activities"]:
        parts.append(f"Activities: {', '.join(vlm_result['activities'])}.")
    if vlm_result["event_type_guess"] and vlm_result["event_type_guess"].lower() != "none":
        parts.append(f"Occasion: {vlm_result['event_type_guess']}.")
    if vlm_result["mood"]:
        parts.append(f"Mood: {vlm_result['mood']}.")
    if vlm_result["colors"]:
        parts.append(f"Colors: {', '.join(vlm_result['colors'])}.")
    if vlm_result["ocr_text"]:
        parts.append(f"Visible text: {vlm_result['ocr_text']}.")

    return " ".join(parts)


def analyze_image(file_path, media_id):

    original_image = Image.open(file_path)

    exif_date = extract_exif_date(original_image)
    exif_location = extract_exif_location(original_image)

    image = original_image.convert("RGB")
    image = resize_image(image)

    vlm_result = describe_image(image)
    searchable_text = _compose_searchable_text(vlm_result)

    detected_objects = detect_objects(image)
    merged_objects = {
        normalize_object(obj)
        for obj in (vlm_result["objects"] + detected_objects + vlm_result["activities"])
        if obj
    }

    face_count = count_faces(file_path)

    time_of_day = vlm_result["time_of_day"]

    image_embedding = get_image_embedding(image)
    add_image_vector(media_id, image_embedding)

    text_embedding = get_text_embedding(searchable_text)
    add_text_vector(media_id, text_embedding)

    return {
        "caption": searchable_text,
        "semantic_objects": list(merged_objects),
        "face_count": face_count,
        "time_of_day": time_of_day,
        "date_taken": exif_date,
        "location": exif_location,
    }
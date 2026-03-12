from PIL import Image
import spacy

from app.models.blip_model import generate_caption
from app.models.clip_model import get_image_embedding
from app.models.yolo_model import detect_faces

from app.utils.image_utils import (
    resize_image,
    detect_day_night,
    extract_exif_date
)

from app.utils.text_utils import normalize_object


nlp = spacy.load("en_core_web_sm")


def extract_noun_phrases(caption):

    doc = nlp(caption)

    phrases = []

    for chunk in doc.noun_chunks:
        phrases.append(chunk.text.lower())

    return list(set(phrases))


def analyze_image(file_path):

    original_image = Image.open(file_path)

    exif_date = extract_exif_date(original_image)

    image = original_image.convert("RGB")

    image = resize_image(image)

    caption = generate_caption(image)

    phrases = extract_noun_phrases(caption)

    objects = list({
        normalize_object(obj)
        for obj in phrases
    })

    face_count = detect_faces(image)

    time_of_day = detect_day_night(image)

    embedding = get_image_embedding(image)

    return {
        "caption": caption,
        "semantic_objects": objects,
        "face_count": face_count,
        "time_of_day": time_of_day,
        "embedding": embedding,
        "date_taken": exif_date
    }
from fastapi import APIRouter
from pydantic import BaseModel
from PIL import Image
import numpy as np

from app.models.clip_model import get_image_embedding
from app.models.blip_model import generate_caption
from app.services.vector_index import add_vector

router = APIRouter()


class AnalyzeRequest(BaseModel):
    image_path: str
    media_id: int


def extract_objects(caption: str):

    caption = caption.lower()

    words = caption.replace(",", "").split()

    stopwords = {
        "a","an","the","in","on","at","of","with","and","is",
        "are","to","for","his","her","their"
    }

    objects = []

    for word in words:
        if word not in stopwords and len(word) > 2:
            objects.append(word)

    return list(set(objects))


@router.post("/analyze")
def analyze(request: AnalyzeRequest):

    # open image safely
    image = Image.open(request.image_path).convert("RGB")

    # force image to fully load into memory
    image = Image.fromarray(np.array(image))

    # generate caption
    caption = generate_caption(image)

    # generate CLIP embedding
    embedding = get_image_embedding(image)

    # add vector to FAISS
    add_vector(request.media_id, embedding)

    # extract semantic objects
    objects = extract_objects(caption)

    return {
        "caption": caption,
        "semantic_objects": objects,
        "face_count": 0,
        "time_of_day": "day",
        "date_taken": None
    }
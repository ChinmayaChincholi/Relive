from fastapi import APIRouter
from pydantic import BaseModel
from typing import List
import numpy as np
from sklearn.cluster import DBSCAN
from app.config import FACE_CLUSTER_EPS

from app.models.face_model import extract_faces_from_image

router = APIRouter()


class FaceExtractionRequest(BaseModel):
    image_path: str
    media_id: int


class ClusterRequest(BaseModel):
    embeddings: List[List[float]]


@router.post("/extract_faces")
def extract_faces(request: FaceExtractionRequest):

    faces = extract_faces_from_image(request.image_path)

    return {
        "media_id": request.media_id,
        "faces": [
            {
                "crop_path": f["crop_path"],
                "embedding": f["embedding"],
                "confidence": f.get("confidence", 1.0)
            }
            for f in faces
        ]
    }


@router.post("/cluster_faces")
def cluster_faces(request: ClusterRequest):

    if not request.embeddings:
        return {"labels": []}

    embeddings = np.array(request.embeddings, dtype="float32")

    norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
    embeddings = embeddings / np.where(norms == 0, 1, norms)

    clustering = DBSCAN(
        eps=FACE_CLUSTER_EPS,
        min_samples=1,
        metric="cosine"
    ).fit(embeddings)

    return {
        "labels": clustering.labels_.tolist()
    }
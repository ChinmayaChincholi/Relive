from fastapi import APIRouter
from pydantic import BaseModel
from typing import List
import numpy as np
import hdbscan
import time

from app.config import (
    FACE_CLUSTER_MIN_CLUSTER_SIZE,
    FACE_CLUSTER_MIN_SAMPLES,
    FACE_CLUSTER_METRIC,
)

from app.models.face_model import extract_faces_from_image

router = APIRouter()


class FaceExtractionRequest(BaseModel):
    image_path: str
    media_id: int


class ClusterRequest(BaseModel):
    embeddings: List[List[float]]


@router.post("/extract_faces")
def extract_faces(request: FaceExtractionRequest):
    start = time.perf_counter()
    faces = extract_faces_from_image(request.image_path)
    print(f"[faces] media_id={request.media_id} step=extract_faces "
          f"face_count={len(faces)} took={time.perf_counter() - start:.3f}s")
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

    # HDBSCAN's underlying k-d tree query requires at least as many points
    # as its k parameter (tied to min_samples) — with too few embeddings in
    # the unnamed pool (e.g. right after the very first photo import, or a
    # library with only a couple of faces overall), it throws a hard
    # ValueError instead of just finding no clusters. Below that threshold,
    # there's nothing meaningful for HDBSCAN to do anyway — every point is
    # unclustered ("noise") by definition until there are enough similar
    # points to group, so return that directly rather than calling HDBSCAN
    # on data it can't handle.
    if len(request.embeddings) <= FACE_CLUSTER_MIN_SAMPLES:
        return {"labels": [-1] * len(request.embeddings)}

    embeddings = np.array(request.embeddings, dtype="float32")

    norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
    embeddings = embeddings / np.where(norms == 0, 1, norms)

    clusterer = hdbscan.HDBSCAN(
        min_cluster_size=FACE_CLUSTER_MIN_CLUSTER_SIZE,
        min_samples=FACE_CLUSTER_MIN_SAMPLES,
        metric=FACE_CLUSTER_METRIC,
    )
    labels = clusterer.fit_predict(embeddings)

    return {
        "labels": labels.tolist()
    }
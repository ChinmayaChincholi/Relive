from fastapi import APIRouter
from pydantic import BaseModel

from app.models.clip_model import get_text_embedding as get_clip_text_embedding
from app.models.text_embedding_model import get_text_embedding as get_desc_text_embedding
from app.services.vector_index import search_image_vectors, search_text_vectors
from app.config import SEMANTIC_SEARCH_TOP_K, SEMANTIC_SEARCH_MIN_SCORE

router = APIRouter()


class SearchRequest(BaseModel):
    query: str


@router.post("/semantic_search")
def semantic_search(request: SearchRequest):
    embedding = get_clip_text_embedding(request.query)
    results = search_image_vectors(embedding, k=SEMANTIC_SEARCH_TOP_K, min_score=SEMANTIC_SEARCH_MIN_SCORE)

    return {
        "results": results
    }


@router.post("/text_search")
def text_search(request: SearchRequest):
    embedding = get_desc_text_embedding(request.query)
    results = search_text_vectors(embedding, k=SEMANTIC_SEARCH_TOP_K, min_score=SEMANTIC_SEARCH_MIN_SCORE)

    return {
        "results": results
    }
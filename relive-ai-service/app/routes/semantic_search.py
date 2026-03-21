from fastapi import APIRouter
from pydantic import BaseModel

from app.models.clip_model import get_text_embedding
from app.services.vector_index import search_vectors

router = APIRouter()


class SearchRequest(BaseModel):
    query: str


@router.post("/semantic_search")
def semantic_search(request: SearchRequest):

    embedding = get_text_embedding(request.query)

    results = search_vectors(embedding, k=50)

    return {
        "results": results
    }
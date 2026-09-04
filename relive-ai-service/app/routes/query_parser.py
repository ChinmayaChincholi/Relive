from fastapi import APIRouter
from pydantic import BaseModel

from app.models.llm_model import parse_query as qwen_parse_query

router = APIRouter()


class QueryRequest(BaseModel):
    query: str


@router.post("/parse_query")
def parse_query(request: QueryRequest):
    # Spelling correction happens on the Java side (SymSpellUtil) before this
    # is called, so `request.query` here is already corrected.
    return qwen_parse_query(request.query)
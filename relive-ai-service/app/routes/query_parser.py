from fastapi import APIRouter
from pydantic import BaseModel

from app.models.query_llm_model import parse_query as llm_parse_query

router = APIRouter()


class QueryRequest(BaseModel):
    query: str


@router.post("/parse_query")
def parse_query(request: QueryRequest):
    return llm_parse_query(request.query.strip())
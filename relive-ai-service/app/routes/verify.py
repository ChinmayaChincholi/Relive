from fastapi import APIRouter
from pydantic import BaseModel
from typing import List

from app.models.query_llm_model import verify_candidate

router = APIRouter()


class Candidate(BaseModel):
    media_id: int
    rich_description: str


class VerifyRequest(BaseModel):
    query: str
    candidates: List[Candidate]


@router.post("/verify_candidates")
def verify_candidates(request: VerifyRequest):

    results = []
    for candidate in request.candidates:
        verified = verify_candidate(request.query, candidate.rich_description)
        results.append({
            "media_id": candidate.media_id,
            "verified": verified,
        })

    return {"results": results}
from fastapi import APIRouter
from pydantic import BaseModel

from app.services.image_pipeline import analyze_image

router = APIRouter()


class AnalyzeRequest(BaseModel):
    image_path: str
    media_id: int


@router.post("/analyze")
def analyze(request: AnalyzeRequest):

    result = analyze_image(request.image_path, request.media_id)

    return {
        "caption": result["caption"],
        "semantic_objects": result["semantic_objects"],
        "face_count": result["face_count"],
        "time_of_day": result["time_of_day"],
        "date_taken": result["date_taken"],
        "location": result["location"]
    }
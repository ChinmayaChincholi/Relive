from fastapi import APIRouter
from pydantic import BaseModel

from app.services.image_pipeline import analyze_image

router = APIRouter()


class ImageRequest(BaseModel):
    file_path: str


@router.post("/analyze")
def analyze(request: ImageRequest):

    return analyze_image(request.file_path)
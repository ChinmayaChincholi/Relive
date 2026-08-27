from fastapi import APIRouter
from pydantic import BaseModel

from app.services.vector_index import remove_image_vector, remove_text_vector

router = APIRouter()


class DeleteRequest(BaseModel):
    media_id: int


@router.post("/delete_vectors")
def delete_vectors(request: DeleteRequest):
    remove_image_vector(request.media_id)
    remove_text_vector(request.media_id)
    return {"deleted": True}
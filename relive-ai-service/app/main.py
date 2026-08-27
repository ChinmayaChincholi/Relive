from fastapi import FastAPI

from app.hardware import describe as describe_hardware
from app.routes.analyze import router as analyze_router
from app.routes.query_parser import router as query_router
from app.routes.semantic_search import router as semantic_router
from app.routes.faces import router as faces_router
from app.routes.verify import router as verify_router

from app.services.vector_index import init_indexes
from app.models.text_embedding_model import EMBEDDING_DIM as TEXT_EMBEDDING_DIM

from app.routes.delete import router as delete_router

print(f"Detected hardware: {describe_hardware()}")

init_indexes(text_embedding_dim=TEXT_EMBEDDING_DIM)

app = FastAPI()

app.include_router(analyze_router)
app.include_router(query_router)
app.include_router(semantic_router)
app.include_router(faces_router)
app.include_router(verify_router)
app.include_router(delete_router)

print("Relive AI Service running.")
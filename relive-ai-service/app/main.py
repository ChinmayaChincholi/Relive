from fastapi import FastAPI

from app.routes.analyze import router as analyze_router
from app.routes.query_parser import router as query_router
from app.routes.semantic_search import router as semantic_router
from app.routes.faces import router as faces_router

app = FastAPI()

app.include_router(analyze_router)
app.include_router(query_router)
app.include_router(semantic_router)
app.include_router(faces_router)

print("Relive AI Service running.")
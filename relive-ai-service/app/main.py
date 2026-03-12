from fastapi import FastAPI

from app.routes.analyze import router as analyze_router
from app.routes.query_parser import router as query_router

app = FastAPI()

app.include_router(analyze_router)
app.include_router(query_router)

print("Relive AI Service running.")
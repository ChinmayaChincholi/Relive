from fastapi import FastAPI

from app.hardware import describe as describe_hardware
from app.routes.analyze import router as analyze_router
from app.routes.faces import router as faces_router

print(f"Detected hardware: {describe_hardware()}")

app = FastAPI()

app.include_router(analyze_router)
app.include_router(faces_router)

print("Relive AI Service running.")
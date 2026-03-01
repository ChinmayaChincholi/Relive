# Relive
NLP-Driven Personal Media Retrieval System using ML

Relive follows a modular microservice architecture:

- React (Frontend)
- Spring Boot (Backend API)
- FastAPI (Vision + ML Service)
- MySQL (Database)

The Vision Service processes media files and returns structured metadata used by the backend for intelligent search.

## Vision Service Setup (FastAPI + ML Pipeline)

This service handles:

- Image captioning (BLIP)
- Semantic object extraction (spaCy + CLIP)
- Face detection (YOLOv8)
- EXIF metadata extraction
- Image embeddings (CLIP)

### Requirements

- Python 3.9 or higher
- pip
- (Optional) CUDA-enabled GPU for faster processing

### Installation Steps

Navigate to the 'vision-service' directory: cd vision-service
Install dependencies: pip install -r requirements.txt
Download spaCy language model: python -m spacy download en_core_web_sm

To Run the Service : uvicorn main:app --reload --port 5000
The service will be available at: http://localhost:5000

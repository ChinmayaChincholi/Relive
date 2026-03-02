# Relive

NLP-Driven Personal Media Retrieval System using Machine Learning

Relive is a full-stack AI-powered media retrieval system that allows users to search personal photos using natural language queries such as:

- "Show me beach photos from 2022"
- "Photos with two people at night"
- "Pictures of dogs in Ooty"

The system combines computer vision, NLP, and semantic embeddings to enable intelligent media search.

---

## Architecture

Relive consists of three main components:

### 1. Spring Boot Backend
- User authentication (JWT)
- Media metadata storage
- Search APIs
- Integration with ML service

### 2. FastAPI Vision Service
- BLIP image captioning
- spaCy noun phrase extraction
- CLIP embedding generation
- YOLO face detection
- EXIF metadata extraction
- Day/Night detection

### 3. React + Vite Frontend
- User authentication
- Media upload
- Search interface
- Dashboard and result display

---

## Tech Stack

Backend:
- Java (Spring Boot)
- MySQL / JPA

ML Service:
- FastAPI
- PyTorch
- BLIP
- CLIP
- YOLOv8 (face model)
- spaCy

Frontend:
- React
- Vite
- Axios

---

## Folder Structure

project/ - Root Folder (Run commands from here)

    |--- project/
            |--- src/main/java - (Spring Boot Backend) 
            |--- vision-service/main.py - (Python FastAPI ML Service) 
    |--- relive-frontend 
            |---src - (React with Vite Frontend) 


project (top-most folder) contains project (Spring Boot Backend and ML vision service) and relive-frontend (React Frontend). 
## How to Run

### Backend

cd project

mvn spring-boot:run


### Vision Service

cd project/vision-service

pip install -r requirements.txt

uvicorn main:app --reload --port 5000


### Frontend

cd relive-frontend

npm install

npm run dev


## Branch Strategy

- main → Stable release branch
- dev → Integration branch
- feature/* → Feature-specific branches

All changes are merged via Pull Requests.

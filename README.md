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

## Folder Structure

project/                      ← Repository Root (run commands from here)
│
├── project/                  ← Spring Boot Backend
│   ├── src/main/java
│   └── vision-service/
│        └── main.py          ← FastAPI ML Service
│
└── relive-frontend/          ← React + Vite Frontend
    └── src/

 ## How to Run the Application
 All commands below must be executed from the repository root folder (the top-most project folder).

1️) Run Spring Boot Backend

From repository root:

cd project
mvn spring-boot:run

Backend will start at:

http://localhost:8080

Make sure:

MySQL is running

Database credentials are configured correctly in application.properties

2️) Run FastAPI ML Vision Service

From repository root:

cd project/vision-service
Create virtual environment (first time only)
python -m venv venv
venv\Scripts\activate     # Windows

Then install dependencies:

pip install -r requirements.txt

Start the ML service:

uvicorn main:app --reload --port 5000

ML service will run at:

http://localhost:5000

Swagger docs available at:

http://localhost:5000/docs
3️⃣ Run React Frontend

From repository root:

cd relive-frontend
npm install       # first time only
npm run dev

Frontend runs at:

http://localhost:5173
🔷 Important

All three services must be running simultaneously:

Service	Port
Backend (Spring)	8080
ML Service (FastAPI)	5000
Frontend (React)	5173

If ML service is not running, media processing will fail.

If backend is not running, frontend API calls will fail.

🔷 Full Startup Order (Recommended)

Start MySQL

Start ML service (port 5000)

Start Backend (port 8080)

Start Frontend (port 5173)

🔷 Example Complete Flow
# From root
cd project/vision-service
venv\Scripts\activate
uvicorn main:app --reload --port 5000

# New terminal
cd project
mvn spring-boot:run

# New terminal
cd relive-frontend
npm run dev

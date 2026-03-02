Relive

NLP-Driven Personal Media Retrieval System using Machine Learning

Relive is a full-stack AI-powered media retrieval system that allows users to search personal photos using natural language queries such as:

"Show me beach photos from 2022"

"Photos with two people at night"

"Pictures of dogs in Ooty"

The system combines computer vision, NLP, and semantic embeddings to enable intelligent media search.

Architecture

Relive consists of three main components:

1. Spring Boot Backend

User authentication (JWT)

Media metadata storage

Search APIs

Integration with ML service

2. FastAPI Vision Service

BLIP image captioning

spaCy noun phrase extraction

CLIP embedding generation

YOLO face detection

EXIF metadata extraction

Day/Night detection

3. React + Vite Frontend

User authentication

Media upload

Search interface

Dashboard and result display

Tech Stack
Backend

Java (Spring Boot)

MySQL

Spring Data JPA

JWT Authentication

ML Service

FastAPI

PyTorch

BLIP

CLIP

YOLOv8 (Face Detection Model)

spaCy

Frontend

React

Vite

Axios

Folder Structure
project/                         # Repository Root (run commands from here)
│
├── project/                     # Spring Boot Backend
│   ├── src/main/java/
│   ├── pom.xml
│   └── vision-service/          # FastAPI ML Service
│       ├── main.py
│       ├── requirements.txt
│       └── yolov8n-face-lindevs.pt
│
└── relive-frontend/             # React + Vite Frontend
    ├── src/
    ├── package.json
    └── vite.config.js

⚠️ All commands below must be executed from the repository root folder (the top-most project folder).

How to Run the Application

Make sure you are inside the repository root:

cd project

You should see:

project/

relive-frontend/

.git

1️⃣ Run Spring Boot Backend

From repository root:

cd project
mvn spring-boot:run

Backend runs at:

http://localhost:8080

Make sure:

MySQL is running

Database credentials are configured correctly in application.properties

2️⃣ Run FastAPI ML Vision Service

From repository root:

cd project/vision-service
Create virtual environment (first time only)
python -m venv venv

Activate it (Windows):

venv\Scripts\activate

Install dependencies:

pip install -r requirements.txt

Start the ML service:

uvicorn main:app --reload --port 5000

ML service runs at:

http://localhost:5000

Swagger API documentation available at:

http://localhost:5000/docs
3️⃣ Run React Frontend

From repository root:

cd relive-frontend
npm install      # first time only
npm run dev

Frontend runs at:

http://localhost:5173
Important

All three services must be running simultaneously.

Service	Port
Backend (Spring Boot)	8080
ML Service (FastAPI)	5000
Frontend (React)	5173

If the ML service is not running, media processing will fail.

If the backend is not running, frontend API calls will fail.

Recommended Startup Order

Start MySQL

Start ML service (port 5000)

Start Backend (port 8080)

Start Frontend (port 5173)

Example Complete Startup Flow
Terminal 1 (ML Service)
cd project/vision-service
venv\Scripts\activate
uvicorn main:app --reload --port 5000
Terminal 2 (Backend)
cd project
mvn spring-boot:run
Terminal 3 (Frontend)
cd relive-frontend
npm run dev
Branch Strategy

main → Stable release branch

dev → Active development branch

feature/* → Feature-specific branches

All major changes are merged using Pull Requests.

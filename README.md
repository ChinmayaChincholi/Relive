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

Setup Instructions

Follow the steps below to run Relive locally. (First Time)

1. Clone the Repository

    git clone https://github.com/<your-username>/Relive.git
   
    cd Relive

3. Setup Backend (Spring Boot)

Navigate to the backend project folder and run the application.

    cd project
    mvn spring-boot:run

The backend will start on: 
http://localhost:8080

3. Setup Vision Service (FastAPI + ML Models)

Navigate to the vision service folder.

    cd project/vision-service

Create a Python virtual environment.

    python -m venv venv

Activate the virtual environment.

Windows :

    venv\Scripts\activate

Mac / Linux :

    source venv/bin/activate

Install required dependencies.

    pip install -r requirements.txt

Download the spaCy language model.

    python -m spacy download en_core_web_sm

Run the vision service.

    uvicorn main:app --port 5000

The ML service will start on: 
http://localhost:5000

4. Setup Frontend (React + Vite)

Open a new terminal and navigate to the frontend folder.

    cd relive-frontend

Install dependencies.

    npm install

Run the development server.

    npm run dev

The frontend will start on: 
http://localhost:5173

5. Running the Full System

Make sure the following three services are running:

Spring Boot Backend	on port 8080

Vision Service (FastAPI) on port 5000

React Frontend on port 5173

Once all services are running, open the frontend in your browser and start using Relive.


## Branch Strategy

- main → Stable release branch
- dev → Integration branch
- feature/* → Feature-specific branches

All changes are merged via Pull Requests.

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

### 2. FastAPI AI Service
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

ML Service: (Requires Python 3.11.9)
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

## Running the Relive Application

Relive consists of **three main services** that must be started separately:

1. **AI Service** – FastAPI service running ML models
2. **Backend** – Spring Boot API handling authentication, media metadata, and search
3. **Frontend** – React web application for the user interface

Start the services in the following order:

1. AI Service
2. Backend
3. Frontend

---

# 1. Setup and Run AI Service

The AI service runs machine learning models such as **BLIP captioning, YOLO face detection, CLIP embeddings, and spaCy NLP processing**.

### Prerequisites

* Python **3.11**
* pip

### Step 1: Navigate to AI service folder

```bash
cd relive-ai-service
```

### Step 2: Create virtual environment

```bash
py -3.11 -m venv venv
```

### Step 3: Activate virtual environment

**Windows**

```bash
venv\Scripts\activate
```

**Mac/Linux**

```bash
source venv/bin/activate
```

### Step 4: Install dependencies

```bash
pip install -r requirements.txt
```

### Step 5: Install spaCy language model

```bash
pip install https://github.com/explosion/spacy-models/releases/download/en_core_web_sm-3.7.1/en_core_web_sm-3.7.1-py3-none-any.whl
```

### Step 6: Run the AI service

```bash
uvicorn app.main:app --reload --port 5000
```

The service will start at:

```
http://localhost:5000
```

---

# 2. Setup and Run Backend (Spring Boot)

The backend manages:

* User authentication (JWT)
* Media metadata storage
* Communication with the AI service
* Search APIs

### Prerequisites

* Java **17+**
* Maven
* MySQL

### Step 1: Navigate to backend folder

```bash
cd relive-backend
```

### Step 2: Configure database

Update `application.properties` with your MySQL credentials:

```
spring.datasource.url=jdbc:mysql://localhost:3306/relive
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

Create the database in MySQL:

```sql
CREATE DATABASE relive;
```

### Step 3: Run the backend

Using Maven:

```bash
mvn spring-boot:run
```

Or run the main class from your IDE (IntelliJ / VS Code).

Backend will start at:

```
http://localhost:8080
```

---

# 3. Setup and Run Frontend (React)

The frontend provides the UI for:

* User login/register
* Importing media
* Viewing imported media
* Natural language search

### Prerequisites

* Node.js **18+**
* npm

### Step 1: Navigate to frontend folder

```bash
cd relive-frontend
```

### Step 2: Install dependencies

```bash
npm install
```

### Step 3: Start the frontend

```bash
npm run dev
```

Frontend will run at:

```
http://localhost:5173
```

---

# Application Architecture

```
React Frontend (Port 5173)
        │
        ▼
Spring Boot Backend (Port 8080)
        │
        ▼
FastAPI AI Service (Port 5000)
```

The backend communicates with the AI service using REST APIs to process images and generate metadata for intelligent search.



## Project Folder Structure

```
Relive
│
├── relive-backend/                 # Spring Boot backend
│   ├── src/main/java/              # Controllers, services, repositories, entities
│   ├── src/main/resources/         # application.properties, configs
│   └── pom.xml
│
├── relive-frontend/                # React + Vite frontend
│   ├── src/
│   │   ├── pages/                  # Login, Register, Dashboard, Ask, ImportedMedia
│   │   ├── components/             # Reusable UI components
│   │   └── services/               # API calls to backend
│   ├── public/
│   └── package.json
│
├── relive-ai-service/              # FastAPI machine learning service
│   ├── main.py                     # FastAPI entry point
│   ├── requirements.txt            # Python dependencies
│   ├── yolov8n-face-lindevs.pt     # YOLO face detection model
│   └── venv/                       # Python virtual environment (not committed)
│
└── README.md
```

## Branch Strategy

- main → Stable release branch
- dev → Integration branch
- feature/* → Feature-specific branches

All changes are merged via Pull Requests.


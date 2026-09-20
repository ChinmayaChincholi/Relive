# Relive

**Private photo search that never leaves your computer.**
Describe a memory in plain words: *"Riya at the beach in June 2023, without Kabir"*. Relive finds it. Every photo, face and search stays on your own machine.

---

## Table of contents

1. [What is Relive?](#what-is-relive)
2. [How Relive compares](#how-relive-compares)
3. [Features](#features)
4. [The one big caveat: processing time](#the-one-big-caveat-processing-time)
5. [Architecture](#architecture)
6. [Tech stack](#tech-stack)
7. [The AI models](#the-ai-models)
8. [System requirements](#system-requirements)
9. [Installation and running](#installation-and-running)
    - [Windows](#windows)
    - [macOS](#macos)
    - [Linux](#linux)
10. [Using Relive](#using-relive)
11. [Search tips](#search-tips)
12. [Where your data lives](#where-your-data-lives)
13. [Configuration](#configuration)
14. [Troubleshooting](#troubleshooting)
15. [Project structure](#project-structure)
16. [Privacy and compliance notes](#privacy-and-compliance-notes)
17. [Known limitations](#known-limitations)

---

## What is Relive?

Relive is a **fully local, privacy-preserving photo library with natural-language search**. You import photos, Relive "looks" at each one with AI models running on your own computer, and afterwards you can:

- **search in plain English** ("birthday party with balloons but no cake", "photos from March 2022", "night photos in Tokyo"),
- **browse by the people** it recognizes (and name them),
- **browse by the places** stored in your photos' GPS data,
- **browse by date**.

No photo, face, or search query is ever sent to a cloud service. After a one-time download of the AI models, Relive works with no internet connection at all.

## How Relive compares

| | Where your photos are analyzed | Works on |
|---|---|---|
| **Google Photos** | On Google's cloud servers | Any platform, but your photos live with Google |
| **Apple Photos** | On your Apple devices | Apple ecosystem only (iPhone, iPad, Mac) |
| **Relive** | **On your own computer, always** | **Windows, macOS and Linux** |

Relive is aimed at anyone who doesn't want their memories analysed by someone else's servers. That covers individuals who value privacy and organizations that hold confidential photo archives (legal evidence, HR investigations, facility security imagery, insurance claims, government or defense imagery). Because nothing leaves the machine, using it doesn't add a new vendor or data-processing agreement to a compliance review.

## Features

- **Natural-language search** with AND / OR / NOT logic, dates, date ranges, times of day and clock times
- **Typo-tolerant**: misspelled names, places and words still resolve
- **Rich scene understanding**: every photo is described across 21 categories (objects, animals, colors, actions, emotions, events, weather, ...)
- **Object detection** as a safety net for things the scene model misses
- **Face recognition and grouping** into people; name them, merge duplicates, split mistakes, delete groups
- **Places page**: photos grouped by the city/region/country read from GPS metadata
- **Duplicate protection**: the same file is never imported twice (SHA-256 hash)
- **Hardware-aware**: picks the largest models your machine can hold, and steps down automatically if loading fails
- **No cloud, no accounts, no telemetry**

## The one big caveat: processing time

> **Processing a single photo takes roughly 10-12 minutes on typical mid-range hardware.**

That is the price of doing everything offline. Each photo is examined by a multi-billion-parameter vision-language model, a text language model and two more detectors, all on your own CPU/GPU. Photos are processed **one at a time** in the background. The Import page estimates 12 minutes per remaining photo.

Practical advice: import in batches, leave your computer on (e.g. overnight) and keep using the app while it works. A photo becomes searchable as soon as it is finished. **Search itself is near-instant.**

## Architecture

Relive is three programs that talk to each other over local HTTP:

```
┌────────────────────┐       ┌─────────────────────────┐       ┌──────────────────────────┐
│  relive-frontend    │ HTTP  │  relive-backend          │ HTTP  │  relive-ai-service        │
│  React 19 + Vite    │──────▶│  Spring Boot (Java 17+)  │──────▶│  FastAPI (Python)         │
│  localhost:5173     │◀──────│  localhost:8080          │◀──────│  localhost:5000           │
└────────────────────┘       └────────────┬────────────┘       └──────────────────────────┘
                                          │
                                          ▼
                              ┌─────────────────────────┐
                              │  ~/.relive/              │
                              │   relive.db  (SQLite)    │
                              │   uploads/   (photos +   │
                              │              face crops) │
                              └─────────────────────────┘
```

| Service | Role |
|---|---|
| **relive-frontend** | The user interface. Talks only to the backend, never to the AI service. |
| **relive-backend** | The "brain and memory": receives uploads, stores files and the database, runs the job queue, assigns faces to people, and performs all search logic (spell-correction, query parsing, matching, boolean logic). Contains no machine learning. |
| **relive-ai-service** | Runs every AI model: scene understanding, object detection, synonym generation, face detection and recognition, face clustering, EXIF/GPS reading. Stateless: it stores nothing. |

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 19, Vite 7, React Router 7, Axios (fonts bundled locally via `@fontsource`, no CDN) |
| Backend | Spring Boot 3, Java 17+, Spring Data JPA / Hibernate 6, Lombok, SQLite |
| AI service | Python 3.11/3.12, FastAPI + Uvicorn, llama-cpp-python (GGUF models), PyTorch (CPU build), Transformers, InsightFace + ONNX Runtime, RF-DETR, HDBSCAN, simplemma, reverse_geocoder, pycountry, Pillow, OpenCV |
| Storage | SQLite file and ordinary files on disk under `~/.relive` |

## The AI models

Relive checks your RAM/VRAM at startup and picks a **tier** for each model. If a model fails to load, it steps down to the next smaller tier automatically.

| Task | Low tier | **Mid tier** | High tier |
|---|---|---|---|
| Scene understanding (vision-language) | Qwen2.5-VL-3B | **Qwen2.5-VL-7B** | Qwen2.5-VL-72B |
| Synonym generation (text LLM) | Qwen3.5-4B | **Qwen3.5-9B** | Qwen3.6-27B |
| Object detection | RF-DETR Nano | **RF-DETR Large** | D-FINE-X |
| Face detection + recognition | InsightFace `buffalo_s` | **InsightFace `buffalo_l`** | InsightFace `buffalo_l` |

- The language models run as 4-bit quantized GGUF files (`Q4_K_M`) through `llama-cpp-python`.
- Text-model tier: 14 GB of RAM or more selects Mid, otherwise Low.
- GPU-bound model tier: with a GPU, 16 GB or more of VRAM selects High, 6 GB or more selects Mid, otherwise Low. Without a GPU it uses the RAM rule above, so CPU-only machines top out at Mid.
- Override the choice with the environment variable `RELIVE_FORCE_TIER` (`low`, `mid` or `high`).

## System requirements

**Hardware (Mid tier, the tested configuration)**

| | Minimum | Recommended |
|---|---|---|
| RAM | 16 GB | 16-32 GB |
| Free disk | ~20 GB (models + photos) | 30 GB+ |
| CPU | 4 cores | 8+ cores |
| GPU | not required | optional |

The models are loaded fully into RAM when the AI service starts, so 16 GB is a realistic floor for Mid. With less RAM, use `RELIVE_FORCE_TIER=low`.

**Software**

| Tool | Version |
|---|---|
| Python | **3.11 or 3.12** (3.13 is not supported by the pinned `numpy==1.26.4`) |
| Java (JDK) | **17 or newer** |
| Maven | 3.9+ (or use `./mvnw` if it is included in `relive-backend/`) |
| Node.js | **20.19+ or 22.12+** (required by Vite 7) |
| Git | any recent version |
| C/C++ build tools + CMake | needed to build `llama-cpp-python` and `insightface` (see below) |

An internet connection is needed for the first setup only (installing dependencies and downloading the model files).

## Installation and running

The order matters. Always start **AI service, then backend, then frontend**, in three separate terminals.

### Windows

Use **PowerShell**.

#### 1. Install prerequisites (once)

```powershell
winget install Python.Python.3.12
winget install EclipseAdoptium.Temurin.17.JDK
winget install OpenJS.NodeJS.LTS
winget install Git.Git
winget install Kitware.CMake
winget install Microsoft.VisualStudio.2022.BuildTools --override "--wait --passive --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
```

Install **Maven** from https://maven.apache.org/download.cgi (unzip it and add its `bin` folder to your `PATH`), or skip it if `relive-backend` contains `mvnw.cmd`. **Close and reopen PowerShell** afterwards, then check:

```powershell
python --version ; java -version ; node --version ; npm --version ; cmake --version
```

#### 2. Get the code

```powershell
git clone <your-repo-url>
cd Relive
```

#### 3. Start the AI service (Terminal 1)

```powershell
cd relive-ai-service
python -m venv venv
# If activation is blocked:  Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 5000
```

The first start downloads several GB of models and can take a long while. Wait until you see `Relive AI Service running.` and `Uvicorn running on http://127.0.0.1:5000`.

#### 4. Start the backend (Terminal 2)

```powershell
cd relive-backend
mvn spring-boot:run
# or:  .\mvnw.cmd spring-boot:run
```

Wait for `Started ProjectApplication`.

#### 5. Start the frontend (Terminal 3)

```powershell
cd relive-frontend
npm install
npm run dev
```

Open **http://localhost:5173** in your browser.

---

### macOS

Use **Terminal** (zsh). Install [Homebrew](https://brew.sh) first if you don't have it.

#### 1. Install prerequisites (once)

```bash
xcode-select --install          # C/C++ compiler (skip if already installed)
brew install python@3.12 maven node cmake git
brew install --cask temurin@17
```

Check:

```bash
python3.12 --version && java -version && mvn -version && node --version && cmake --version
```

#### 2. Get the code

```bash
git clone <your-repo-url>
cd Relive
```

#### 3. Start the AI service (Terminal 1)

```bash
cd relive-ai-service
python3.12 -m venv venv
source venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 5000
```

Wait for `Relive AI Service running.`

#### 4. Start the backend (Terminal 2)

```bash
cd relive-backend
mvn spring-boot:run
# or:  ./mvnw spring-boot:run
```

#### 5. Start the frontend (Terminal 3)

```bash
cd relive-frontend
npm install
npm run dev
```

Open **http://localhost:5173**.

---

### Linux

Commands are for Debian/Ubuntu. On Fedora/Arch use the equivalent packages (`dnf`/`pacman`): a C/C++ toolchain, CMake, Python 3.11/3.12 with venv and headers, JDK 17+, Maven and Git. Ubuntu 24.04 ships Python 3.12 and Debian 12 ships Python 3.11.

#### 1. Install prerequisites (once)

```bash
sudo apt update
sudo apt install -y python3 python3-venv python3-dev build-essential cmake git curl openjdk-17-jdk maven

# Node.js 22 via nvm (the apt version is usually too old for Vite 7)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
# close and reopen the terminal, then:
nvm install 22
```

Check:

```bash
python3 --version && java -version && mvn -version && node --version && cmake --version
```

#### 2. Get the code

```bash
git clone <your-repo-url>
cd Relive
```

#### 3. Start the AI service (Terminal 1)

```bash
cd relive-ai-service
python3 -m venv venv
source venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 5000
```

Wait for `Relive AI Service running.`

#### 4. Start the backend (Terminal 2)

```bash
cd relive-backend
mvn spring-boot:run
# or:  ./mvnw spring-boot:run
```

#### 5. Start the frontend (Terminal 3)

```bash
cd relive-frontend
npm install
npm run dev
```

Open **http://localhost:5173**.

---

### Quick health check

| Service | Check |
|---|---|
| AI service | http://127.0.0.1:5000/docs opens the FastAPI page |
| Backend | http://localhost:8080/media/progress returns JSON |
| Frontend | http://localhost:5173 shows Relive |

### Running again later

Repeat only the "start" steps (activate the venv, then run uvicorn / `mvn spring-boot:run` / `npm run dev`). Installation is a one-time job.

### Optional: force a smaller (or larger) model tier

Set this **before** starting the AI service, in the same terminal:

```powershell
# Windows PowerShell
$env:RELIVE_FORCE_TIER = "low"      # low | mid | high
```
```bash
# macOS / Linux
export RELIVE_FORCE_TIER=low        # low | mid | high
```

### Optional: guarantee zero network use after setup

Once all models are downloaded, tell the Hugging Face library never to go online:

```powershell
# Windows PowerShell
$env:HF_HUB_OFFLINE = "1"
```
```bash
# macOS / Linux
export HF_HUB_OFFLINE=1
```

### Optional: GPU acceleration

The default `pip install` gives CPU builds. GPU-accelerated builds of `llama-cpp-python` (CUDA on Windows/Linux; Metal is used automatically on Apple Silicon) and ONNX Runtime are possible but not covered by `requirements.txt` and not verified by this project.

## Using Relive

1. **Import Media**: drag photos onto the Import page (or click to browse). JPG and PNG are supported. Each file is limited to 50 MB. Photos already in the library are skipped automatically. Watch the queue on the same page; a popup appears when everything is done.
2. **Home**: totals, processing progress, your people, and recent photos. The **Your People** and **Places** boxes are clickable shortcuts.
3. **Search**: type what you remember. Results are the photos that match.
4. **All Photos**: every photo grouped by month. **Select** lets you multi-select and delete; **double-click** a photo to see its details. The Select button stays visible while you scroll, and going back returns you to where you were.
5. **Your People**: face groups. Type a name and press **Save Name**, drag one card onto another (or use **Merge Faces**) to merge duplicates, click a group to open it, use **Select → Delete** to remove groups. Inside a group you can pick faces that don't belong and move them elsewhere.
6. **Your Places**: every detected place as a title, with its photos below.

Naming people matters: only **named** people can be searched by name.

## Search tips

| You type | What Relive understands |
|---|---|
| `Riya in Paris` | photos containing Riya **and** taken in Paris |
| `dog or cat` | either a dog or a cat |
| `wedding without Kabir` | wedding photos where Kabir is **not** present (`without`, `excluding`, `except`, `not`) |
| `June 2023`, `3rd June 2023`, `2023` | a month / a day / a whole year (a **year is required**) |
| `June 2023 to August 2024`, `between March 2022 and May 2022` | a date range |
| `morning`, `afternoon`, `evening`, `night` | time-of-day windows (05-12, 12-17, 17-21, 21-05) |
| `9:25 am`, `between 2:30 pm and 6:40 pm` | exact clock time / clock range |
| `Riya, beach or park` | a comma starts a new condition |

Filler words such as *photos, of, the, a, in, at, with, from, taken* are ignored. Misspellings of names, places and words are corrected automatically.

## Where your data lives

Everything is in a hidden folder in your home directory (`C:\Users\<you>\.relive` on Windows):

```
~/.relive/
├── relive.db        SQLite database (photo records, keywords, places, people, face data)
└── uploads/         your imported photos (renamed with random IDs) and the face-crop
                     images cut out of them (<photo>.face0.jpg, ...)
```

Model files are cached by their libraries (Hugging Face cache in `~/.cache/huggingface`, InsightFace in `~/.insightface`). To wipe Relive's data, stop all services and delete `~/.relive`.

## Configuration

| Setting | Where | Default |
|---|---|---|
| AI service URL | `relive-backend/src/main/resources/application.properties` → `ai.service.url` | `http://localhost:5000` |
| Upload size limits | same file, `spring.servlet.multipart.*` | 50 MB per file, 500 MB per request |
| Data folder | same file, `relive.data.dir` | `~/.relive` |
| Allowed frontend origin (CORS) | `relive-backend/.../config/CorsConfig.java` | `http://localhost:5173` |
| Backend URL used by the UI | `relive-frontend/src/api/api.js` and `services/mediaService.js` | `http://localhost:8080` |
| Model tier | environment variable `RELIVE_FORCE_TIER` | auto-detected |

## Troubleshooting

| Problem | Fix |
|---|---|
| `pip install` fails while building `llama-cpp-python` or `insightface` | Install the C/C++ build tools and CMake (see the prerequisites for your OS), reopen the terminal, retry. |
| Backend log shows *Connection refused* to `localhost:5000` | The AI service isn't running yet or is still loading models. Wait for `Relive AI Service running.` Photos that failed are re-queued automatically the next time the backend starts. |
| Photos stuck on "Analysing..." or marked failed | Check the AI-service terminal for the error, fix it, then **restart the backend**. It re-queues every photo that is still processing or failed. |
| AI service is killed / out of memory at startup | Set `RELIVE_FORCE_TIER=low`, close other apps, or free RAM. |
| First start takes very long | It is downloading several GB of models. This happens once. |
| A HEIC/HEIF photo fails | Current builds can't decode HEIC. Convert to JPEG/PNG first. |
| Blank page / network errors in the browser | Open exactly `http://localhost:5173` (CORS only allows that origin) and make sure the backend is running. |
| `Port already in use` | Something else uses 5000 / 8080 / 5173. Stop it, or change the port (and the matching setting in the table above). |
| Search finds nothing | Only fully processed photos are searchable, and people can only be searched by a name you have saved. |
| Names/places typed wrongly aren't auto-corrected right after a restart | Spell-correction vocabulary is rebuilt after each import batch. Fuzzy matching still handles many typos. |
| Cannot activate the venv in PowerShell | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`, then activate again. |

## Project structure

```
Relive/
├── relive-frontend/                 React app
│   └── src/
│       ├── api/ context/ services/  Axios setup, search state, API calls
│       ├── components/              AppLayout, Sidebar, TopBar, ConfirmModal
│       └── pages/                   Home, Import, Ask (Search), Media, MediaDetail,
│                                    Faces, PersonPhotos, Places
├── relive-backend/                  Spring Boot app
│   └── src/main/java/com/relive/project/
│       ├── controller/              REST endpoints (media, faces)
│       ├── service/                 upload, processing queue, faces, search, spell-correction
│       ├── util/                    query parser, Jaro-Winkler, SymSpell, lemmatizer helpers
│       ├── entity/ repository/      database model
│       ├── client/                  HTTP clients for the AI service
│       ├── startup/                 startup re-queue + database triggers
│       └── config/ dto/ mapper/
└── relive-ai-service/               FastAPI app
    ├── requirements.txt
    └── app/
        ├── main.py  config.py  hardware.py
        ├── routes/                  /analyze, /extract_faces, /cluster_faces
        ├── models/                  vlm_model, llm_model, object_detection_model, face_model
        ├── services/                image_pipeline, vocabulary_categories
        └── utils/                   image_utils (EXIF, GPS), lemmatizer
```

## Privacy and compliance notes

- Photos, face crops, embeddings, the database and search queries are handled on your machine only. The AI service does not call any external API.
- Network access happens **only** for installing dependencies and the one-time model download. Use `HF_HUB_OFFLINE=1` afterwards for a strict guarantee.
- Start the AI service with `--host 127.0.0.1` (as shown) so it is not reachable from other machines. Relive has no login system. It is designed for a single trusted user on a trusted machine, or an internal deployment you control.
- Running fully on-premises supports data-residency and confidentiality requirements, but **Relive is not independently certified** against any framework (HIPAA, SOC 2, FedRAMP, ...). Evaluate it against your own requirements.

## Known limitations

- About 10-12 minutes per photo; photos are processed strictly one at a time (queue holds up to 500 photos).
- Search returns matching photos but does **not rank** them by relevance.
- The query parser is rule-based: it understands a fixed set of trigger words (see Search tips), not free-form grammar.
- Photos without EXIF date/GPS data (screenshots, messaging-app copies) can't be found by date or place.
- Images only; no video processing. HEIC is not supported.
- The UI expects the default local addresses (`localhost:5173/8080/5000`).
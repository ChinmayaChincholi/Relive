# Relive

**Searchable photo intelligence that never leaves your machine.**

---

## What is Relive?

Relive is a photo library and search system built for organizations that cannot put sensitive imagery through a third-party cloud AI service — legal case documentation, internal investigations, HR and personnel records, confidential facilities/asset archives, government and defense imagery, insurance claims documentation, and similar confidentiality-bound photo collections.

It gives an organization the same "smart search" experience people expect from consumer cloud photo tools — describe a scene in plain English, find photos by who's in them, filter by date or location — but every model runs **entirely on infrastructure the organization controls**. No photo, face embedding, or search query is ever sent to an external API or third-party processor.

### The core goal

Let an organization deploy a fully capable, natural-language-searchable photo archive — with face recognition and rich scene understanding — entirely within its own network boundary, so that using AI-powered photo search never requires signing a data processing agreement with an outside vendor, adding a new subprocessor to a compliance review, or exposing sensitive imagery to any system the organization doesn't operate itself.

### What makes Relive different

- **Fully offline AI, not just offline storage.** Plenty of enterprise document/photo management tools store files on-premises but still call out to a cloud API for search or recognition. Relive's entire pipeline — scene understanding, object detection, face recognition, and query parsing — runs on local infrastructure using open-weight models, with zero outbound calls once models are cached.
- **No new subprocessor, no new data-sharing agreement.** Because nothing leaves the deployment environment, adopting Relive doesn't require adding a vendor to a compliance or vendor-risk review the way a cloud photo-AI service would.
- **Real natural-language search, not keyword matching.** Relive parses queries with a local LLM, so it understands compositional logic — "photos from the Q3 site inspection, excluding the exterior shots" — instead of just matching individual words.
- **Typo-tolerant by design.** Person names, location names, and tags are matched fuzzily against what's actually in the archive, not exact strings — useful across large organizational archives with inconsistent naming.
- **Hardware-aware, not one-size-fits-all.** Relive detects available RAM/VRAM at startup and automatically loads the largest, most accurate model that will reliably fit — the same deployment works on a modest on-prem server and a GPU-equipped workstation.
- **Two independent search signals, fused, not one guess.** Relive searches both by visual similarity (CLIP) and by the meaning of a rich, AI-written description of each photo (a dedicated text-embedding model), and combines the two — rather than relying on a single, often-too-narrow signal.

**A note on compliance:** running entirely on-premises is a foundational property that makes Relive compatible with strict data-residency and confidentiality requirements — but Relive itself is not independently certified against any specific regulatory framework (HIPAA, SOC 2, FedRAMP, etc.). Organizations with formal compliance obligations should evaluate Relive's architecture against their own specific requirements before deployment.

---

## Why an on-premises architecture (and not a cloud/SaaS service)?

Relive is architected as three services that run together **entirely within the organization's own infrastructure**, rather than as a hosted cloud product. This is a deliberate choice, driven by the kind of imagery this system is meant to handle:

- **Confidentiality is the actual requirement, not a nice-to-have.** Legal evidence, HR investigation photos, government/defense imagery, and internal security documentation are exactly the categories of data organizations are contractually or legally bound to keep off third-party infrastructure. A local architecture means there's no external copy of this imagery or its derived face/embedding data to be breached, subpoenaed from a vendor, or exposed by someone else's incident.
- **No new vendor in the data-processing chain.** Every cloud AI photo service is a new subprocessor that has to go through legal review, a data processing agreement, and ongoing vendor risk assessment. Running fully on-premises removes that entirely.
- **Predictable cost at archive scale.** Cloud AI inference for vision-language models, face recognition, and LLMs is expensive per-call at the scale of a real organizational photo archive. A local deployment's cost is the infrastructure the organization already operates.
- **Works fully offline or air-gapped**, after the one-time model download — relevant for defense, government, and other environments where network egress is restricted by policy.
- **The organization retains direct custody of the data.** Photos, face crops, embeddings, and the database all live in ordinary files on infrastructure the organization directly controls and audits.

### Who Relive is for

Relive is built for **organizations** or individual consumers — specifically ones handling photo archives under confidentiality, legal, or regulatory obligations that make cloud photo-AI services a non-starter:

- Legal teams managing case evidence and discovery photo archives
- HR and internal investigations teams handling personnel-related photo documentation
- Corporate security teams managing facility, incident, or asset imagery
- Government and defense contractors working with controlled or classified imagery
- Insurance teams processing claims documentation photo archives
- Any organization whose photo archive falls under an NDA, litigation hold, or internal confidentiality policy

The current setup (cloning a repo and running three services) targets an organization's technical/IT staff for deployment, not end-user self-installation — end users within the organization would interact with the web frontend once it's deployed on internal infrastructure.

---

## Architecture overview

Relive is three independent services that talk to each other over local HTTP:

```
┌─────────────────────┐        ┌──────────────────────┐        ┌────────────────────────┐
│  relive-frontend     │──HTTP─▶│  relive-backend       │──HTTP─▶│  relive-ai-service      │
│  React (Vite)        │        │  Spring Boot (Java)   │        │  FastAPI (Python)       │
│  Port: 5173 (dev)    │◀───────│  Port: 8080            │◀───────│  Port: 5000             │
└─────────────────────┘        └──────────┬────────────┘        └────────────────────────┘
                                            │
                                            ▼
                                     ┌─────────────┐
                                     │  SQLite DB   │
                                     │  (.relive)   │
                                     └─────────────┘
```

### `relive-frontend` — the UI

A React (Vite) single-page app. Lets the user upload photos, browse their library, search in natural language, and manage recognized people ("Your People"). Talks only to the backend — it never calls the AI service directly.

### `relive-backend` — orchestration, storage, and search logic

A Spring Boot application, backed by SQLite. Responsibilities:
- Receives uploaded photos, stores them on disk, and de-duplicates by SHA-256 file hash
- Calls the AI service to analyze each photo and stores the results (captions, tags, face embeddings, dates, locations)
- Owns all business logic for search: parses the AI service's query breakdown, fuzzy-matches people/locations/tags against what's actually in the library, fuses the two semantic search signals (Reciprocal Rank Fusion), and applies hard filters (date, person, location, AND/OR/NOT logic)
- Manages people: clustering assignment, naming, merging duplicate people, deleting

The backend deliberately contains **no machine learning code** — it's the "business logic and data" layer. All ML inference lives in the AI service.

### `relive-ai-service` — all machine learning

A FastAPI (Python) service that does nothing but run models: describing images, detecting objects, detecting/recognizing/clustering faces, generating embeddings, and parsing natural-language queries. Stateless per-request; the backend is responsible for persisting anything long-lived. Every model it uses is memory-tiered — see below.

---

## The pipeline: from upload to search result

### 1. Uploading a photo

1. The frontend sends the file to the backend.
2. The backend hashes the file (SHA-256) and skips it if it's already in the library — a safe, exact duplicate check.
3. The file is saved to disk, a `Media` row is created with `status = PROCESSING`, and the photo is queued for background analysis.

### 2. Understanding the photo (runs once per image)

| Step | Model / library | Why this one |
|---|---|---|
| Date & GPS extraction | Pillow (EXIF) + `reverse_geocoder` | Standard, deterministic, fully offline reverse geocoding — no API calls needed |
| Rich scene description | A local vision-language model (**Qwen2-VL** or **moondream2**, chosen by available hardware) | Produces a structured description — setting, mood, activities, objects, occasion guess — instead of a single generic caption, which is what actually makes descriptive search ("photos of a celebration") work |
| Object detection | **RF-DETR** | Currently one of the strongest open-weight object detectors, and specifically more accurate than older YOLO-style models on the overlapping/occluded objects typical of candid photos |
| Face detection & recognition | **InsightFace** (SCRFD detection + ArcFace recognition) | State-of-the-art open, offline face recognition (~99.8% on standard benchmarks); ArcFace embeddings feed directly into clustering |
| Face clustering into "people" | **HDBSCAN** | Automatically infers how many people/clusters exist without a manually tuned distance threshold, and improves incrementally as new photos are added |
| Visual embedding | **CLIP** | Embeds the image into a shared image/text space, powering "does this look like the query" search |
| Text embedding | A dedicated sentence-embedding model (**BGE**) | CLIP's own text understanding is weak on full sentences and negation; a model built specifically for text-to-text similarity gives a second, complementary search signal over the rich description from step above |

Every model in this table is **memory-tiered**: at startup, the AI service checks available RAM/VRAM and loads the largest version of each model that will reliably fit, with automatic fallback to a smaller variant if loading fails. This is why the exact same codebase runs on both a modest laptop and a workstation with a GPU.

### 3. Searching

1. The user types a query in plain English — e.g. "site inspection photos from March, excluding exterior shots" or "photos with Priya Sharma present."
2. A local LLM (**Qwen3.5**, size chosen by hardware) parses it into structured intent: what must be present, what must be excluded, named people, named places, dates, and any OR-groups — real compositional logic, not word-matching.
3. Person and location names are **fuzzy-matched** against what's actually stored in the library, so typos and near-misses still resolve correctly.
4. Two independent searches run in parallel: CLIP (visual similarity) and the text-embedding model (semantic similarity against each photo's rich description).
5. The backend fuses both ranked lists (Reciprocal Rank Fusion) and applies any hard filters (person, place, date, required/excluded tags) as a deterministic pass.
6. Results are returned to the frontend, ranked.

Everything from EXIF extraction through query parsing to final ranking happens locally, using open-weight models cached on first use — no photo or query ever leaves the machine.

---

## Prerequisites

Before setting up, install:

- **Python 3.11+** — for `relive-ai-service`
- **Java 17+** and **Maven** — for `relive-backend` (check `relive-backend/pom.xml` if you need the exact Java version this project targets)
- **Node.js 18+** and **npm** — for `relive-frontend`
- **Git**

> **A note on platform testing:** this pipeline has been built and actively debugged on Windows. The commands below should work equivalently on macOS and Linux, since every dependency (Python, Java, Node) is cross-platform — but end-to-end running on macOS/Linux hasn't been separately verified. If you hit a platform-specific dependency issue on Mac/Linux, it's likely to look similar in kind to the Windows issues already resolved in this project's history (version mismatches between fast-moving ML libraries) — check versions with `pip show <package>` and compare against `requirements.txt`.

---

## Setup instructions

Clone the repo first, on any platform:

```bash
git clone <your-repo-url>
cd Relive
```

### 1. AI service (`relive-ai-service`)

**Windows (PowerShell):**
```powershell
cd relive-ai-service
python -m venv venv
.\venv\Scripts\Activate.ps1
pip install -r requirements.txt
pip check
python -m uvicorn app.main:app --port 5000
```

**macOS / Linux (bash/zsh):**
```bash
cd relive-ai-service
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
pip check
python -m uvicorn app.main:app --port 5000
```

The first run will download several gigabytes of model weights (the vision-language model, CLIP, the text-embedding model, RF-DETR, InsightFace, and the local LLM) — this is a one-time cost; everything is cached afterward. Wait for `Relive AI Service running.` and `Uvicorn running on http://127.0.0.1:5000` before starting the backend.

If you're on a CPU-only machine, `requirements.txt` is already set up to fetch the smaller CPU-only build of PyTorch automatically.

### 2. Backend (`relive-backend`)

Open a **new terminal** (leave the AI service running) and, from the repo root:

**Windows / macOS / Linux (same Maven commands everywhere):**
```bash
cd relive-backend
mvn spring-boot:run
```
(If this project includes the Maven wrapper, `./mvnw spring-boot:run` on macOS/Linux or `mvnw.cmd spring-boot:run` on Windows works the same way without requiring Maven to be installed separately.)

Wait for `Started ProjectApplication` and `Tomcat started on port 8080`.

### 3. Frontend (`relive-frontend`)

Open a **third terminal** and, from the repo root:

**Windows / macOS / Linux (identical commands):**
```bash
cd relive-frontend
npm install
npm run dev
```

Vite will print a local URL (typically `http://localhost:5173`) — open that in your browser.

### Startup order matters

Always start the **AI service first** and wait for it to finish loading every model before starting the backend. If the backend (or its startup queue of unprocessed photos) tries to reach the AI service before it's finished loading, requests will fail with a connection error — simply restart image processing once the AI service is confirmed up.

---

## Where your data lives

- **Photos and face crops:** stored on disk under a local Relive data directory, within your organization's own infrastructure (check `application.properties` for the exact configured path)
- **Structured data** (captions, tags, dates, locations, face-to-person assignments): a local SQLite database
- **Search embeddings:** FAISS index files on disk, managed entirely by the AI service

Nothing in this list is ever transmitted outside the infrastructure you deploy Relive on — there is no external API call in the search or analysis pipeline that a network policy or air-gap would need to account for.
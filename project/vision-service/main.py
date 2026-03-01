from fastapi import FastAPI
from pydantic import BaseModel
from ultralytics import YOLO
from transformers import (
    BlipProcessor,
    BlipForConditionalGeneration,
    CLIPProcessor,
    CLIPModel
)
from PIL import Image
from PIL.ExifTags import TAGS
import torch
import cv2
import os
import numpy as np
import spacy
import re


print("=== BLIP BASE + SEMANTIC PIPELINE RUNNING ===")

app = FastAPI()

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

# ---------------- LOAD SPACY ----------------

print("Loading spaCy...")
nlp = spacy.load("en_core_web_sm")
print("spaCy loaded.")

# ---------------- LOAD BLIP CAPTION MODEL ----------------

print("Loading BLIP base caption model...")
blip_processor = BlipProcessor.from_pretrained(
    "Salesforce/blip-image-captioning-base"
)
blip_model = BlipForConditionalGeneration.from_pretrained(
    "Salesforce/blip-image-captioning-base"
)
blip_model.to(device)
print("BLIP base loaded.")

# ---------------- LOAD YOLO FACE MODEL ----------------

print("Loading YOLO face model...")
yolo_face_model = YOLO("yolov8n-face-lindevs.pt")
print("YOLO face model loaded.")

# ---------------- LOAD CLIP ----------------

print("Loading CLIP...")
clip_model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32")
clip_processor = CLIPProcessor.from_pretrained("openai/clip-vit-base-patch32")
clip_model.to(device)
print("CLIP loaded.")

# ---------------- REQUEST MODEL ----------------

class ImageRequest(BaseModel):
    file_path: str

class QueryRequest(BaseModel):
    query: str

# ---------------- IMAGE RESIZE ----------------

def resize_image(image, max_size=640):
    width, height = image.size
    if max(width, height) > max_size:
        scale = max_size / max(width, height)
        new_size = (int(width * scale), int(height * scale))
        return image.resize(new_size)
    return image

# ---------------- DAY/NIGHT DETECTION ----------------

def detect_day_night(image_pil):
    image_np = np.array(image_pil)
    hsv = cv2.cvtColor(image_np, cv2.COLOR_RGB2HSV)
    brightness = hsv[:, :, 2].mean()
    return "night" if brightness < 80 else "day"

# ---------------- NOUN PHRASE EXTRACTION ----------------

def extract_noun_phrases(caption):
    doc = nlp(caption)
    phrases = []

    for chunk in doc.noun_chunks:
        phrases.append(chunk.text.lower())

    return list(set(phrases))

# ---------------- CLIP PHRASE VALIDATION ----------------

def filter_phrases_with_clip(image, phrases):
    if not phrases:
        return []

    inputs = clip_processor(
        text=phrases,
        images=image,
        return_tensors="pt",
        padding=True
    ).to(device)

    with torch.no_grad():
        outputs = clip_model(**inputs)

    logits = outputs.logits_per_image
    probs = logits.softmax(dim=1)[0]

    refined = []
    for phrase, prob in zip(phrases, probs):
        if prob.item() > 0.05:
            refined.append(phrase)

    return refined

# ---------------- CLIP EMBEDDING ----------------

def get_image_embedding(image):
    inputs = clip_processor(
        images=image,
        return_tensors="pt"
    ).to(device)

    with torch.no_grad():
        outputs = clip_model.get_image_features(**inputs)

    embedding = outputs / outputs.norm(dim=-1, keepdim=True)
    return embedding[0].cpu().tolist()

# ---------------- DATE EXTRACTION ----------------

def extract_exif_date(image):
    try:
        exif_data = image._getexif()
        if not exif_data:
            return None

        for tag_id, value in exif_data.items():
            tag = TAGS.get(tag_id, tag_id)
            if tag == "DateTimeOriginal":
                return value  # format: "2020:05:17 14:32:11"

    except Exception:
        return None

    return None

# ----------------Object Normalization----------------

def normalize_object(obj):
    obj = obj.lower().strip()

    # Remove leading articles
    obj = re.sub(r"^(a|an|the)\s+", "", obj)

    # Remove plural 's' (basic version)
    if obj.endswith("s") and not obj.endswith("ss"):
        obj = obj[:-1]

    return obj

# ---------------- ANALYZE ENDPOINT ----------------

@app.post("/analyze")
def analyze(request: ImageRequest):

    file_path = request.file_path

    if not os.path.exists(file_path):
        return {
            "caption": "file not found",
            "semantic_objects": [],
            "face_count": 0,
            "time_of_day": "unknown",
            "embedding": [],
            "date_taken": None
        }

    try:

        original_image = Image.open(file_path)

        # Extract EXIF BEFORE conversion
        exif_date = extract_exif_date(original_image)

        # Now convert safely for ML processing
        image = original_image.convert("RGB")
        image = resize_image(image)

        # ---------- CAPTION (BLIP BASE) ----------
        inputs = blip_processor(
            image,
            return_tensors="pt"
        ).to(device)

        with torch.no_grad():
            output = blip_model.generate(
                **inputs,
                max_length=40
            )

        caption = blip_processor.decode(
            output[0],
            skip_special_tokens=True
        )

        # ---------- SEMANTIC OBJECTS ----------
        phrases = extract_noun_phrases(caption)
        semantic_objects = filter_phrases_with_clip(image, phrases)
        semantic_objects = list({
            normalize_object(obj)
            for obj in semantic_objects
        })

        # ---------- FACE DETECTION ----------
        results_face = yolo_face_model(image)
        face_count = 0

        if results_face and len(results_face[0].boxes) > 0:
            face_count = len(results_face[0].boxes)

        # ---------- TIME OF DAY ----------
        time_of_day = detect_day_night(image)

        # ---------- CLIP EMBEDDING ----------
        embedding = get_image_embedding(image)

        return {
            "caption": caption,
            "semantic_objects": semantic_objects,
            "face_count": face_count,
            "time_of_day": time_of_day,
            "embedding": embedding,
            "date_taken": exif_date
        }

    except Exception as e:
        return {
            "caption": f"error: {str(e)}",
            "semantic_objects": [],
            "face_count": 0,
            "time_of_day": "unknown",
            "embedding": [],
            "date_taken": None
        }

import re

@app.post("/parse_query")
def parse_query(request: QueryRequest):

    query = request.query.lower()

    # -------- Extract Year --------
    import re
    year_match = re.search(r"\b(19|20)\d{2}\b", query)
    year = int(year_match.group()) if year_match else None

    # -------- Extract Objects --------
    doc = nlp(query)

    ignored_words = {"photo", "photos", "image", "images", "picture", "pictures"}

    objects = []

    for token in doc:
        if token.pos_ == "NOUN":
            lemma = token.lemma_.lower()

            if lemma not in ignored_words:
                objects.append(normalize_object(lemma))

    # remove duplicates
    objects = list(set(objects))

    return {
        "objects": objects,
        "year": year
    }


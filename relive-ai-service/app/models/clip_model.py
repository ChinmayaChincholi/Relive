import torch
from transformers import CLIPModel, CLIPProcessor
from PIL import Image
from app.config import CLIP_MODEL

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

print(f"Loading CLIP model ({CLIP_MODEL})...")

clip_model = CLIPModel.from_pretrained(CLIP_MODEL)
clip_processor = CLIPProcessor.from_pretrained(CLIP_MODEL)
clip_model.to(device)

print("CLIP loaded.")


def normalize(features):
    return features / features.norm(dim=-1, keepdim=True)


def get_image_embedding(image):

    if not isinstance(image, Image.Image):
        raise ValueError("Expected PIL Image for embedding")

    inputs = clip_processor(
        images=image,
        return_tensors="pt"
    ).to(device)

    with torch.no_grad():
        features = clip_model.get_image_features(**inputs)

    features = normalize(features)

    return features[0].cpu().numpy().tolist()


def get_text_embedding(text):

    inputs = clip_processor(
        text=[text],
        return_tensors="pt",
        padding=True
    ).to(device)

    with torch.no_grad():
        features = clip_model.get_text_features(**inputs)

    features = normalize(features)

    return features[0].cpu().numpy().tolist()
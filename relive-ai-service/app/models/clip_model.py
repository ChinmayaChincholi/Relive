import torch
from transformers import CLIPModel, CLIPProcessor
from PIL import Image

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

print("Loading CLIP model...")

clip_model = CLIPModel.from_pretrained(
    "openai/clip-vit-base-patch32"
)

clip_processor = CLIPProcessor.from_pretrained(
    "openai/clip-vit-base-patch32"
)

clip_model.to(device)

print("CLIP loaded.")


def normalize(features):
    return features / features.norm(dim=-1, keepdim=True)


def get_image_embedding(image):

    # image is expected to be a PIL Image
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
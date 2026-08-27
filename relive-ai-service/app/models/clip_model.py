import torch
from transformers import CLIPModel, CLIPProcessor
from PIL import Image

from app.config import CLIP_MODEL_BY_TIER
from app.hardware import Tier, gpu_tier, describe as describe_hardware

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]


def _load_with_fallback():
    start_tier = gpu_tier()
    start_idx = _TIER_ORDER.index(start_tier)
    last_error = None

    for tier in _TIER_ORDER[start_idx:]:
        model_name = CLIP_MODEL_BY_TIER[tier]
        try:
            print(f"Loading CLIP model ({model_name})...")
            clip_processor = CLIPProcessor.from_pretrained(model_name)
            clip_model = CLIPModel.from_pretrained(model_name)
            clip_model.to(device)
            clip_model.eval()
            print(f"CLIP loaded: {model_name}")
            return clip_processor, clip_model
        except Exception as e:
            print(f"CLIP load failed for tier={tier} ({model_name}): {e}")
            last_error = e
            continue

    raise RuntimeError(
        f"Could not load any CLIP tier. Hardware: {describe_hardware()}. "
        f"Last error: {last_error}"
    )


clip_processor, clip_model = _load_with_fallback()


def normalize(features):
    if hasattr(features, "pooler_output"):
        features = features.pooler_output
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
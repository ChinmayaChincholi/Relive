import torch
from transformers import CLIPModel, CLIPProcessor

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

print("Loading CLIP...")

clip_model = CLIPModel.from_pretrained("openai/clip-vit-base-patch32")
clip_processor = CLIPProcessor.from_pretrained("openai/clip-vit-base-patch32")

clip_model.to(device)

print("CLIP loaded.")


def get_image_embedding(image):

    inputs = clip_processor(images=image, return_tensors="pt").to(device)

    with torch.no_grad():

        features = clip_model.get_image_features(**inputs)

    features = features / torch.norm(features, dim=-1, keepdim=True)

    return features[0].cpu().numpy().tolist()
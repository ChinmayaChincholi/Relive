import torch
from transformers import BlipProcessor, BlipForConditionalGeneration
from app.config import CAPTIONING_MODEL, CAPTIONING_MAX_LENGTH, CAPTIONING_NUM_BEAMS

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

print(f"Loading BLIP model ({CAPTIONING_MODEL})...")

processor = BlipProcessor.from_pretrained(CAPTIONING_MODEL)
model = BlipForConditionalGeneration.from_pretrained(CAPTIONING_MODEL)
model.to(device)

print("BLIP loaded.")


def generate_caption(image):

    inputs = processor(
        images=image,
        return_tensors="pt"
    ).to(device)

    with torch.no_grad():
        output = model.generate(
            **inputs,
            max_length=CAPTIONING_MAX_LENGTH,
            num_beams=CAPTIONING_NUM_BEAMS,
            early_stopping=True
        )

    caption = processor.decode(output[0], skip_special_tokens=True)

    return caption
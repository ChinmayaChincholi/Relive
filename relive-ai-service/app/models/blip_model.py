import torch
from transformers import BlipProcessor, BlipForConditionalGeneration

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

print("Loading BLIP model...")

processor = BlipProcessor.from_pretrained(
    "Salesforce/blip-image-captioning-base"
)

model = BlipForConditionalGeneration.from_pretrained(
    "Salesforce/blip-image-captioning-base"
)

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
            max_length=40
        )

    caption = processor.decode(
        output[0],
        skip_special_tokens=True
    )

    return caption
import numpy as np
import cv2
from PIL.ExifTags import TAGS


def resize_image(image, max_size=640):

    width, height = image.size

    if max(width, height) > max_size:
        scale = max_size / max(width, height)
        new_size = (int(width * scale), int(height * scale))
        return image.resize(new_size)

    return image


def detect_day_night(image_pil):

    image_np = np.array(image_pil)

    hsv = cv2.cvtColor(image_np, cv2.COLOR_RGB2HSV)

    brightness = hsv[:, :, 2].mean()

    return "night" if brightness < 80 else "day"


def extract_exif_date(image):

    try:

        exif_data = image._getexif()

        if not exif_data:
            return None

        for tag_id, value in exif_data.items():

            tag = TAGS.get(tag_id, tag_id)

            if tag == "DateTimeOriginal":
                return value

    except Exception:
        return None

    return None
import numpy as np
import cv2
from PIL.ExifTags import TAGS, GPSTAGS
import reverse_geocoder as rg


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


def extract_exif_location(image):
    """
    Extracts GPS coordinates from EXIF and reverse geocodes to
    a human-readable location string like 'Bengaluru, Karnataka, India'.
    Returns None if no GPS data available.
    """

    try:
        exif_data = image._getexif()
        if not exif_data:
            return None

        gps_info_raw = None

        for tag_id, value in exif_data.items():
            tag = TAGS.get(tag_id, tag_id)
            if tag == "GPSInfo":
                gps_info_raw = value
                break

        if not gps_info_raw:
            return None

        gps_data = {}
        for key, val in gps_info_raw.items():
            decoded = GPSTAGS.get(key, key)
            gps_data[decoded] = val

        lat = _convert_to_degrees(gps_data.get("GPSLatitude"))
        lon = _convert_to_degrees(gps_data.get("GPSLongitude"))

        if lat is None or lon is None:
            return None

        if gps_data.get("GPSLatitudeRef") == "S":
            lat = -lat
        if gps_data.get("GPSLongitudeRef") == "W":
            lon = -lon

        # Reverse geocode to human-readable location (fully offline)
        results = rg.search((lat, lon), mode=1)

        if results:
            r = results[0]
            city = r.get("name", "")
            region = r.get("admin1", "")
            country = r.get("cc", "")

            parts = [p for p in [city, region, country] if p]
            location_str = ", ".join(parts)

            # Also store raw coordinates alongside
            return f"{location_str} ({round(lat, 4)},{round(lon, 4)})"

        # Fallback to raw coordinates if geocoding fails
        return f"{round(lat, 6)},{round(lon, 6)}"

    except Exception as e:
        print(f"Location extraction error: {e}")
        return None


def _convert_to_degrees(value):

    if value is None or len(value) != 3:
        return None

    try:
        d = float(value[0])
        m = float(value[1])
        s = float(value[2])
        return d + (m / 60.0) + (s / 3600.0)
    except Exception:
        return None
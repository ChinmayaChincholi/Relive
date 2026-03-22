from ultralytics import YOLO
from app.config import OBJECT_DETECTION_MODEL, OBJECT_DETECTION_CONFIDENCE

print(f"Loading YOLOv8 object detection model ({OBJECT_DETECTION_MODEL})...")
object_detector = YOLO(OBJECT_DETECTION_MODEL)
print("YOLOv8 object detection loaded.")


def detect_objects(image_pil):

    import numpy as np
    img_array = np.array(image_pil)

    try:
        results = object_detector(img_array, verbose=False)
    except Exception as e:
        print(f"Object detection error: {e}")
        return []

    detected = set()

    if results and len(results[0].boxes) > 0:
        for box in results[0].boxes:
            confidence = float(box.conf[0])
            if confidence < OBJECT_DETECTION_CONFIDENCE:
                continue

            class_id = int(box.cls[0])
            class_name = object_detector.names[class_id].lower()
            detected.add(class_name)

    return list(detected)
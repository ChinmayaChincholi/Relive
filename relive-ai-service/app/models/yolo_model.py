from ultralytics import YOLO

print("Loading YOLO face model...")

model = YOLO("yolov8n-face-lindevs.pt")

print("YOLO loaded.")


def detect_faces(image):

    results = model(image)

    face_count = 0

    if results and len(results[0].boxes) > 0:
        face_count = len(results[0].boxes)

    return face_count
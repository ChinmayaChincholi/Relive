
# =============================================================================
# RELIVE ML MODEL CONFIGURATION
# Change model names here to switch between quality/speed tradeoffs.
# All models run fully offline after first download.
# =============================================================================

# ── CAPTIONING MODEL ─────────────────────────────────────────────────────────
# Options (best to fastest):
#   "Salesforce/blip-image-captioning-large"   ~900MB  ~35-45s/image  best quality
#   "Salesforce/blip-image-captioning-base"    ~450MB  ~8-12s/image   good quality
CAPTIONING_MODEL = "Salesforce/blip-image-captioning-large"

# Max caption length (tokens). Increase for more detailed captions.
CAPTIONING_MAX_LENGTH = 100

# Number of beams for beam search. Higher = better quality, slower.
# 5 is a good balance. Use 1 for fastest (greedy decoding).
CAPTIONING_NUM_BEAMS = 5

# ── CLIP EMBEDDING MODEL ──────────────────────────────────────────────────────
# Options (best to fastest):
#   "openai/clip-vit-large-patch14"    ~900MB   ~8-12s/image   best search quality
#   "openai/clip-vit-base-patch32"     ~350MB   ~2-4s/image    good search quality
CLIP_MODEL = "openai/clip-vit-base-patch32"

# ── FACE RECOGNITION MODEL ───────────────────────────────────────────────────
# Options (best to fastest):
#   "Facenet512"    512-dim embeddings, best clustering accuracy
#   "Facenet"       128-dim embeddings, faster, slightly less accurate
FACE_RECOGNITION_MODEL = "Facenet512"

# ── FACE DETECTION (for counting faces in photos) ────────────────────────────
# YOLO face model file — must exist in relive-ai-service root
YOLO_FACE_MODEL = "yolov8n-face-lindevs.pt"

# Minimum YOLO face detection confidence (0.0 to 1.0)
YOLO_FACE_CONFIDENCE = 0.5

# ── OBJECT DETECTION MODEL ───────────────────────────────────────────────────
# Options (best to fastest):
#   "yolov8m.pt"   medium model, ~50MB, better accuracy
#   "yolov8n.pt"   nano model,   ~6MB,  fastest
OBJECT_DETECTION_MODEL = "yolov8n.pt"

# Minimum object detection confidence (0.0 to 1.0)
OBJECT_DETECTION_CONFIDENCE = 0.4

# ── FACE EMBEDDING SETTINGS ──────────────────────────────────────────────────
# Minimum face crop size in pixels to process (smaller = more false positives)
MIN_FACE_SIZE = 30

# Maximum image dimension before resizing (larger = more detail, more memory)
MAX_IMAGE_DIMENSION = 1280

# ── CLUSTERING SETTINGS ──────────────────────────────────────────────────────
# DBSCAN epsilon — controls how similar faces must be to cluster together.
# Lower = stricter (fewer false matches), Higher = looser (more grouping)
# Recommended range: 0.15 (strict) to 0.40 (loose)
FACE_CLUSTER_EPS = 0.35
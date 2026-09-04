from app.hardware import Tier, describe as describe_hardware

# ---------------------------------------------------------------------------
# VISION model — image processing step 9 (22-category vocabulary generation).
# Qwen2.5-VL specifically, because it's the newest Qwen-VL family with
# OFFICIAL support in llama-cpp-python 0.3.35 (Qwen25VLChatHandler, added
# upstream). Qwen3.5/3.6-VL vision support only exists in a third-party fork
# as of this build — not used here to avoid depending on an unofficial fork.
# ---------------------------------------------------------------------------
VLM_MODEL_BY_TIER = {
    Tier.LOW:  "Qwen2.5-VL-3B",
    Tier.MID:  "Qwen2.5-VL-7B",
    Tier.HIGH: "Qwen2.5-VL-72B",
}
VLM_REPO_BY_MODEL = {
    "Qwen2.5-VL-3B":  "unsloth/Qwen2.5-VL-3B-Instruct-GGUF",
    "Qwen2.5-VL-7B":  "unsloth/Qwen2.5-VL-7B-Instruct-GGUF",
    "Qwen2.5-VL-72B": "unsloth/Qwen2.5-VL-72B-Instruct-GGUF",
}
VLM_GGUF_FILE_BY_MODEL = {
    "Qwen2.5-VL-3B":  "Qwen2.5-VL-3B-Instruct-Q4_K_M.gguf",
    "Qwen2.5-VL-7B":  "Qwen2.5-VL-7B-Instruct-Q4_K_M.gguf",
    "Qwen2.5-VL-72B": "Qwen2.5-VL-72B-Instruct-Q4_K_M.gguf",
}
# Pinned to one exact file instead of a "*mmproj*.gguf" wildcard — each repo
# ships THREE mmproj variants (BF16/F16/F32), which made the wildcard
# ambiguous and crashed loading. F16 is the standard middle-ground choice.
VLM_MMPROJ_FILE_BY_MODEL = {
    "Qwen2.5-VL-3B":  "mmproj-F16.gguf",
    "Qwen2.5-VL-7B":  "mmproj-F16.gguf",
    "Qwen2.5-VL-72B": "mmproj-F16.gguf",
}
VLM_CONTEXT_WINDOW = 8192
VLM_MAX_NEW_TOKENS_VOCAB = 2500

# ---------------------------------------------------------------------------
# TEXT-ONLY model — image retrieval step 2 (query -> expression tree) and
# image processing step 10 (synonym generation, text-only, no image needed).
# Qwen3.5/3.6, used in plain text mode (no chat_handler) — this part never
# depended on Qwen3-VL vision support, so it's unaffected by the fork issue
# that pushed the vision model above to Qwen2.5-VL instead.
# ---------------------------------------------------------------------------
LLM_MODEL_BY_TIER = {
    Tier.LOW:  "Qwen3.5-4B",
    Tier.MID:  "Qwen3.5-9B",
    Tier.HIGH: "Qwen3.6-27B",
}
LLM_REPO_BY_MODEL = {
    "Qwen3.5-4B":  "unsloth/Qwen3.5-4B-GGUF",
    "Qwen3.5-9B":  "unsloth/Qwen3.5-9B-GGUF",
    "Qwen3.6-27B": "unsloth/Qwen3.6-27B-GGUF",
}
# NOTE: these are still wildcards and may hit the exact same "multiple files
# matched" crash the VLM glob just hit — waiting on your file listing for
# these three repos (see previous message) to pin them to exact filenames
# the same way. Try running it; if it crashes, paste the "Available Files"
# list here the same way you did for the VLM repos and I'll pin these too.
LLM_GGUF_FILE_BY_MODEL = {
    "Qwen3.5-4B":  "*Q4_K_M.gguf",
    "Qwen3.5-9B":  "*Q4_K_M.gguf",
    "Qwen3.6-27B": "*Q4_K_M.gguf",
}
LLM_CONTEXT_WINDOW = 8192
LLM_MAX_NEW_TOKENS_SYNONYMS = 400
LLM_MAX_NEW_TOKENS_QUERY = 300

# ---------------------------------------------------------------------------
# Object detection fallback (image processing step 12) — RF-DETR (Apache 2.0)
# for LOW/MID, D-FINE-X (MIT) for HIGH.
# ---------------------------------------------------------------------------
OBJECT_DETECTION_FAMILY_BY_TIER = {
    Tier.LOW:  "rfdetr",
    Tier.MID:  "rfdetr",
    Tier.HIGH: "dfine",
}
OBJECT_DETECTION_MODEL_BY_TIER = {
    Tier.LOW:  "rfdetr-nano",
    Tier.MID:  "rfdetr-large",
    Tier.HIGH: "dfine-xlarge",
}
D_FINE_HF_REPO_BY_MODEL = {
    "dfine-xlarge": "ustc-community/dfine_x_coco",
    "dfine-large":  "ustc-community/dfine-large-coco",
}
OBJECT_DETECTION_CONFIDENCE = 0.4

# ---------------------------------------------------------------------------
# Face detection / recognition — unchanged, InsightFace remains best-in-class
# for offline use.
# ---------------------------------------------------------------------------
FACE_MODEL_PACK_BY_TIER = {
    Tier.LOW:  "buffalo_s",
    Tier.MID:  "buffalo_l",
    Tier.HIGH: "buffalo_l",
}
FACE_DETECTION_CONFIDENCE = 0.5
MIN_FACE_SIZE = 30
MAX_IMAGE_DIMENSION = 1280

FACE_CLUSTER_MIN_CLUSTER_SIZE = 2
FACE_CLUSTER_MIN_SAMPLES = 1
FACE_CLUSTER_METRIC = "euclidean"

# ---------------------------------------------------------------------------
# Hardware tiers are informational only. Every model loader always ATTEMPTS
# the HIGH tier first regardless of what tier detection guesses, and only
# steps down on an actual load failure.
# ---------------------------------------------------------------------------
print(f"[config] Detected hardware (informational only): {describe_hardware()}")
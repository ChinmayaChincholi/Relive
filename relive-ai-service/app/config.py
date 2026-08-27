from app.hardware import Tier, cpu_ram_tier, gpu_tier

VLM_MODEL_BY_TIER = {
    Tier.LOW:  "vikhyatk/moondream2",
    Tier.MID:  "Qwen/Qwen2-VL-2B-Instruct",
    Tier.HIGH: "Qwen/Qwen2-VL-7B-Instruct",
}
VLM_MAX_NEW_TOKENS = 400

CLIP_MODEL_BY_TIER = {
    Tier.LOW:  "openai/clip-vit-base-patch32",
    Tier.MID:  "openai/clip-vit-base-patch32",
    Tier.HIGH: "openai/clip-vit-large-patch14",
}

TEXT_EMBEDDING_MODEL_BY_TIER = {
    Tier.LOW:  "BAAI/bge-small-en-v1.5",
    Tier.MID:  "BAAI/bge-base-en-v1.5",
    Tier.HIGH: "BAAI/bge-large-en-v1.5",
}
TEXT_EMBEDDING_DIM_BY_TIER = {
    Tier.LOW: 384,
    Tier.MID: 768,
    Tier.HIGH: 1024,
}

OBJECT_DETECTION_MODEL_BY_TIER = {
    Tier.LOW:  "rfdetr-nano",
    Tier.MID:  "rfdetr-medium",
    Tier.HIGH: "rfdetr-large",
}
OBJECT_DETECTION_CONFIDENCE = 0.4

OBJECT_DETECTION_ALLOW_PML_XL = False

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

QUERY_LLM_MODEL_BY_TIER = {
    Tier.LOW:  "Qwen3.5-4B-Q4_K_M.gguf",
    Tier.MID:  "Qwen3.5-9B-Q4_K_M.gguf",
    Tier.HIGH: "Qwen3.8-27B-Instruct-Q4_K_M.gguf",
}
QUERY_LLM_CONTEXT_WINDOW = 4096
QUERY_LLM_MAX_NEW_TOKENS = 512

QUERY_LLM_APPROX_GB_BY_TIER = {
    Tier.LOW: 3.0,
    Tier.MID: 6.0,
    Tier.HIGH: 17.0,
}

QUERY_VERIFICATION_DEFAULT_ENABLED = False
QUERY_VERIFICATION_TOP_K = 30

SEMANTIC_SEARCH_TOP_K = 50
SEMANTIC_SEARCH_MIN_SCORE = 0.15

VLM_TIER = gpu_tier()
CLIP_TIER = gpu_tier()
TEXT_EMBEDDING_TIER = cpu_ram_tier()
OBJECT_DETECTION_TIER = gpu_tier()
FACE_TIER = gpu_tier()
QUERY_LLM_TIER = cpu_ram_tier()
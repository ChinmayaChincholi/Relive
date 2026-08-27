import torch
from sentence_transformers import SentenceTransformer

from app.config import TEXT_EMBEDDING_MODEL_BY_TIER
from app.hardware import Tier, cpu_ram_tier, describe as describe_hardware

_TIER_ORDER = [Tier.HIGH, Tier.MID, Tier.LOW]


def _load_with_fallback():
    start_tier = cpu_ram_tier()
    start_idx = _TIER_ORDER.index(start_tier)
    last_error = None

    for tier in _TIER_ORDER[start_idx:]:
        model_name = TEXT_EMBEDDING_MODEL_BY_TIER[tier]
        try:
            print(f"Loading text-embedding model ({model_name})...")
            device = "cuda" if torch.cuda.is_available() else "cpu"
            model = SentenceTransformer(model_name, device=device)
            print(f"Text-embedding model loaded: {model_name}")
            return model
        except Exception as e:
            print(f"Text-embedding load failed for tier={tier} ({model_name}): {e}")
            last_error = e
            continue

    raise RuntimeError(
        f"Could not load any text-embedding tier. Hardware: {describe_hardware()}. "
        f"Last error: {last_error}"
    )


_model = _load_with_fallback()
EMBEDDING_DIM = _model.get_sentence_embedding_dimension()


def get_text_embedding(text: str):
    embedding = _model.encode(text, normalize_embeddings=True, convert_to_numpy=True)
    return embedding.tolist()
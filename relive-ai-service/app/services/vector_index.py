import faiss
import numpy as np
import os
import pickle

_INDEX_CONFIGS = {
    "image": {"path": "vector_image.index", "id_path": "vector_image_ids.pkl", "dim": 512},
    "text":  {"path": "vector_text.index",  "id_path": "vector_text_ids.pkl",  "dim": None},
}

_state = {}


def _load_or_create(name: str, dim: int):
    cfg = _INDEX_CONFIGS[name]
    index_path = cfg["path"]
    id_path = cfg["id_path"]

    if os.path.exists(index_path):
        print(f"Loading FAISS '{name}' index...")
        index = faiss.read_index(index_path)
        with open(id_path, "rb") as f:
            id_map = pickle.load(f)
    else:
        print(f"Creating new FAISS '{name}' index (dim={dim})...")
        index = faiss.IndexFlatIP(dim)
        id_map = []

    _state[name] = {"index": index, "id_map": id_map}


def init_indexes(text_embedding_dim: int):
    _load_or_create("image", _INDEX_CONFIGS["image"]["dim"])
    _load_or_create("text", text_embedding_dim)


def add_vector(name: str, media_id, embedding):
    state = _state[name]
    vector = np.array([embedding]).astype("float32")

    if media_id not in state["id_map"]:
        state["index"].add(vector)
        state["id_map"].append(media_id)
        _save(name)


def search_vectors(name: str, query_embedding, k=20, min_score=0.15):
    state = _state[name]
    vector = np.array([query_embedding]).astype("float32")

    scores, indices = state["index"].search(vector, k)

    results = []
    for score, idx in zip(scores[0], indices[0]):
        if idx < len(state["id_map"]) and score >= min_score:
            results.append({
                "media_id": state["id_map"][idx],
                "score": float(score),
            })

    return results


def _save(name: str):
    cfg = _INDEX_CONFIGS[name]
    state = _state[name]
    faiss.write_index(state["index"], cfg["path"])
    with open(cfg["id_path"], "wb") as f:
        pickle.dump(state["id_map"], f)

def add_image_vector(media_id, embedding):
    add_vector("image", media_id, embedding)


def add_text_vector(media_id, embedding):
    add_vector("text", media_id, embedding)


def search_image_vectors(query_embedding, k=20, min_score=0.15):
    return search_vectors("image", query_embedding, k=k, min_score=min_score)


def search_text_vectors(query_embedding, k=20, min_score=0.15):
    return search_vectors("text", query_embedding, k=k, min_score=min_score)

def remove_vector(name: str, media_id):
    state = _state[name]
    if media_id not in state["id_map"]:
        return
    position = state["id_map"].index(media_id)
    state["index"].remove_ids(np.array([position], dtype="int64"))
    del state["id_map"][position]
    _save(name)


def remove_image_vector(media_id):
    remove_vector("image", media_id)


def remove_text_vector(media_id):
    remove_vector("text", media_id)
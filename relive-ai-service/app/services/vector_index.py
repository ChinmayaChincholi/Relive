import faiss
import numpy as np
import os
import pickle

INDEX_PATH = "vector.index"
ID_PATH = "vector_ids.pkl"

dimension = 512

if os.path.exists(INDEX_PATH):

    print("Loading FAISS index...")

    index = faiss.read_index(INDEX_PATH)

    with open(ID_PATH, "rb") as f:
        id_map = pickle.load(f)

else:

    print("Creating new FAISS index...")

    index = faiss.IndexFlatIP(dimension)
    id_map = []


def add_vector(media_id, embedding):

    global index, id_map

    vector = np.array([embedding]).astype("float32")

    if media_id not in id_map:

        index.add(vector)

        id_map.append(media_id)

        save_index()


# FIXED FUNCTION (WITH RELEVANCE FILTER)
def search_vectors(query_embedding, k=20, min_score=0.25):

    vector = np.array([query_embedding]).astype("float32")

    scores, indices = index.search(vector, k)

    results = []

    for score, idx in zip(scores[0], indices[0]):

        if idx < len(id_map) and score >= min_score:

            results.append({
                "media_id": id_map[idx],
                "score": float(score)
            })

    return results


def save_index():

    faiss.write_index(index, INDEX_PATH)

    with open(ID_PATH, "wb") as f:
        pickle.dump(id_map, f)
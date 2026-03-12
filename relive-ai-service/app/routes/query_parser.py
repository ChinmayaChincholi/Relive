from fastapi import APIRouter
from pydantic import BaseModel
import spacy
import re

from app.utils.text_utils import normalize_object

router = APIRouter()

nlp = spacy.load("en_core_web_sm")


class QueryRequest(BaseModel):
    query: str


@router.post("/parse_query")
def parse_query(request: QueryRequest):

    query = request.query.lower()

    year_match = re.search(r"\b(19|20)\d{2}\b", query)

    year = int(year_match.group()) if year_match else None

    doc = nlp(query)

    ignored_words = {"photo", "photos", "image", "images", "picture", "pictures"}

    objects = []

    for token in doc:

        if token.pos_ == "NOUN":

            lemma = token.lemma_.lower()

            if lemma not in ignored_words:
                objects.append(normalize_object(lemma))

    objects = list(set(objects))

    return {
        "objects": objects,
        "year": year
    }
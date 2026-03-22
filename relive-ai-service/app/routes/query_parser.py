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

    query = request.query.lower().strip()

    # ── Year extraction ──────────────────────────────────────────────
    year_match = re.search(r"\b(19|20)\d{2}\b", query)
    year = int(year_match.group()) if year_match else None

    # ── Month extraction ─────────────────────────────────────────────
    months = {
        "january": 1, "february": 2, "march": 3, "april": 4,
        "may": 5, "june": 6, "july": 7, "august": 8,
        "september": 9, "october": 10, "november": 11, "december": 12,
        "jan": 1, "feb": 2, "mar": 3, "apr": 4, "jun": 6,
        "jul": 7, "aug": 8, "sep": 9, "oct": 10, "nov": 11, "dec": 12
    }
    month = None
    for month_name, month_num in months.items():
        if month_name in query:
            month = month_num
            break

    # ── Time of day extraction ───────────────────────────────────────
    time_of_day = None
    if any(w in query for w in ["night", "dark", "evening", "midnight"]):
        time_of_day = "night"
    elif any(w in query for w in ["day", "morning", "afternoon", "noon",
                                  "outdoor", "outside", "daytime"]):
        time_of_day = "day"

    # ── Face count hints ─────────────────────────────────────────────
    min_faces = None
    if any(w in query for w in ["two people", "2 people", "couple", "both"]):
        min_faces = 2
    elif any(w in query for w in ["group", "crowd", "many people",
                                  "everyone", "team", "family", "friends"]):
        min_faces = 2
    elif any(w in query for w in ["alone", "solo", "just me",
                                  "only me", "selfie", "myself"]):
        min_faces = 0

    # ── spaCy NLP processing ─────────────────────────────────────────
    doc = nlp(query)

    # Words to ignore for search purposes
    ignored_words = {
        "photo", "photos", "image", "images", "picture", "pictures",
        "show", "find", "get", "give", "want", "need", "look", "looking"
    }

    # ── Nouns — for object matching in media_objects table ──────────
    # These are the most important for keyword search
    nouns = []
    for token in doc:
        if token.pos_ == "NOUN" and not token.is_stop:
            lemma = token.lemma_.lower()
            if lemma not in ignored_words and len(lemma) > 1:
                nouns.append(normalize_object(lemma))

    nouns = list(set(nouns))

    # ── Verbs — for caption matching ────────────────────────────────
    # Verbs describe activities — useful for matching against captions
    # e.g. "eating", "standing", "sitting", "holding"
    verbs = []
    for token in doc:
        if token.pos_ == "VERB" and not token.is_stop:
            lemma = token.lemma_.lower()
            if lemma not in ignored_words and len(lemma) > 2:
                verbs.append(lemma)

    verbs = list(set(verbs))

    # ── Adjectives — for caption matching ───────────────────────────
    # e.g. "colorful", "outdoor", "indoor", "large", "small"
    adjectives = []
    for token in doc:
        if token.pos_ == "ADJ" and not token.is_stop:
            lemma = token.lemma_.lower()
            if len(lemma) > 2:
                adjectives.append(lemma)

    adjectives = list(set(adjectives))

    # ── Named entities ───────────────────────────────────────────────
    # GPE/LOC = places, PERSON = person names, ORG = organizations
    location_hints = []
    person_hints = []

    for ent in doc.ents:
        if ent.label_ in ("GPE", "LOC", "FAC"):
            location_hints.append(ent.text.lower())
        elif ent.label_ == "PERSON":
            person_hints.append(ent.text.lower())

    # ── Proper nouns not caught by NER ──────────────────────────────
    # Sometimes spaCy misses proper nouns as named entities
    for token in doc:
        if token.pos_ == "PROPN" and not token.is_stop:
            text = token.text.lower()
            if text not in ignored_words and len(text) > 2:
                # If not already in location or person hints, add to nouns
                if text not in location_hints and text not in person_hints:
                    nouns.append(normalize_object(text))

    nouns = list(set(nouns))

    # ── All search terms combined (for caption full-text matching) ───
    # Includes nouns + verbs + adjectives for broad caption search
    all_terms = list(set(nouns + verbs + adjectives))

    return {
        "objects": nouns,           # For media_objects table matching
        "verbs": verbs,             # For caption activity matching
        "adjectives": adjectives,   # For caption description matching
        "all_terms": all_terms,     # Combined for caption search
        "year": year,
        "month": month,
        "time_of_day": time_of_day,
        "min_faces": min_faces,
        "location_hints": location_hints,
        "person_hints": person_hints
    }
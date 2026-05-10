from fastapi import APIRouter
from pydantic import BaseModel
import spacy
import re
from difflib import get_close_matches

from app.utils.text_utils import normalize_object

router = APIRouter()

nlp = spacy.load("en_core_web_sm")

# ── Common vocabulary for spell correction ────────────────────────────────────
KNOWN_WORDS = [
    # Food & drink
    "pizza", "pasta", "burger", "sandwich", "coffee", "cake", "salad", "rice",
    "noodles", "sushi", "ramen", "steak", "chicken", "fish", "soup", "bread",
    "dessert", "ice cream", "chocolate", "wine", "beer", "cocktail", "juice",
    # Objects
    "car", "bike", "dog", "cat", "tree", "flower", "house", "building", "phone",
    "laptop", "book", "bag", "hat", "glasses", "chair", "table", "couch", "bed",
    # Activities
    "eating", "drinking", "running", "walking", "playing", "smiling", "dancing",
    "cooking", "reading", "sitting", "standing", "swimming", "hiking", "working",
    # People (generic)
    "person", "people", "man", "woman", "child", "baby", "family", "friends",
    "group", "couple", "crowd", "team",
    # Places
    "beach", "mountain", "park", "restaurant", "cafe", "office", "school",
    "garden", "street", "city", "village", "forest", "lake", "river", "sea",
    # Time
    "morning", "afternoon", "evening", "night", "sunset", "sunrise",
    # Common photo descriptors
    "birthday", "wedding", "party", "vacation", "holiday", "trip", "selfie",
    "outdoor", "indoor", "nature", "urban",
]

KNOWN_WORDS_SET = set(KNOWN_WORDS)

# Words that are definitely NOT person names — filter these out before
# passing query_words to the backend for face DB lookup.
STOPWORDS_FOR_NAMES = {
    "photo", "photos", "image", "images", "picture", "pictures",
    "show", "find", "get", "give", "want", "need", "look", "looking",
    "all", "every", "some", "any", "of", "with", "in", "at", "the",
    "a", "an", "me", "my", "i", "where", "what", "who", "which",
    "from", "by", "for", "on", "that", "this", "is", "was", "are",
    "were", "have", "has", "had", "do", "did", "and", "or", "but",
    "not", "no", "yes", "can", "could", "would", "should", "will",
    "just", "only", "also", "very", "too", "so", "up", "out", "if",
}


def correct_spelling(word: str) -> str:
    if len(word) <= 3 or word in KNOWN_WORDS_SET:
        return word
    matches = get_close_matches(word, KNOWN_WORDS, n=1, cutoff=0.8)
    return matches[0] if matches else word


def correct_query_spelling(query: str) -> str:
    words = query.split()
    corrected = []
    for word in words:
        clean = re.sub(r"[^a-zA-Z]", "", word).lower()
        if not clean or len(clean) <= 3 or clean.isdigit():
            corrected.append(word)
            continue
        fixed = correct_spelling(clean)
        corrected.append(word.replace(clean, fixed))
    return " ".join(corrected)


def extract_negations(doc):
    negated_terms = []
    negation_triggers = {"not", "no", "without", "except", "excluding", "exclude", "never", "none"}

    tokens = list(doc)
    for i, tok in enumerate(tokens):
        if tok.text.lower() in negation_triggers or tok.dep_ == "neg":
            j = i + 1
            while j < len(tokens) and j <= i + 3:
                next_tok = tokens[j]
                if next_tok.pos_ in ("NOUN", "PROPN") and not next_tok.is_stop:
                    negated_terms.append(normalize_object(next_tok.lemma_.lower()))
                j += 1

    for token in doc:
        if token.dep_ == "neg":
            head = token.head
            if head.pos_ in ("NOUN", "VERB"):
                for child in head.children:
                    if child.dep_ in ("dobj", "nsubj", "attr") and not child.is_stop:
                        negated_terms.append(normalize_object(child.lemma_.lower()))

    return list(set(negated_terms))


class QueryRequest(BaseModel):
    query: str


@router.post("/parse_query")
def parse_query(request: QueryRequest):

    original_query = request.query.strip()

    # ── Run spaCy on original cased query for best POS/NER accuracy ──
    doc = nlp(original_query)

    # ── Lowercase for everything else ────────────────────────────────
    query = correct_query_spelling(original_query.lower().strip())

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

    # ── Time of day ──────────────────────────────────────────────────
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

    ignored_words = {
        "photo", "photos", "image", "images", "picture", "pictures",
        "show", "find", "get", "give", "want", "need", "look", "looking",
        "all", "every", "some", "any",
    }

    # ── Extract negated terms ────────────────────────────────────────
    negated_terms = extract_negations(doc)

    # ── Location hints from spaCy NER ────────────────────────────────
    location_hints = []
    for ent in doc.ents:
        if ent.label_ in ("GPE", "LOC", "FAC"):
            location_hints.append(ent.text.lower())

    # ── Nouns — common nouns only (pos_ == "NOUN"), never PROPN ─────
    # PROPN words are NOT added to nouns. Person-name detection is done
    # entirely in the backend by checking every query word against the
    # face DB — this is the only reliable approach regardless of case.
    nouns = []
    for token in doc:
        if token.pos_ == "NOUN" and not token.is_stop:
            lemma = token.lemma_.lower()
            if (lemma not in ignored_words
                    and len(lemma) > 1
                    and lemma not in location_hints):
                normalized = normalize_object(lemma)
                if normalized not in negated_terms:
                    nouns.append(normalized)

    nouns = list(set(nouns))

    # ── Verbs ────────────────────────────────────────────────────────
    verbs = []
    for token in doc:
        if token.pos_ == "VERB" and not token.is_stop:
            lemma = token.lemma_.lower()
            if lemma not in ignored_words and len(lemma) > 2:
                is_negated = any(child.dep_ == "neg" for child in token.children)
                if not is_negated:
                    verbs.append(lemma)

    verbs = list(set(verbs))

    # ── Adjectives ───────────────────────────────────────────────────
    adjectives = []
    for token in doc:
        if token.pos_ == "ADJ" and not token.is_stop:
            lemma = token.lemma_.lower()
            if len(lemma) > 2:
                adjectives.append(lemma)

    adjectives = list(set(adjectives))

    # ── All terms ────────────────────────────────────────────────────
    all_terms = list(set(nouns + verbs + adjectives))

    # ── query_words — ALL meaningful words from the query, lowercased ─
    # The backend will check each of these directly against the face DB
    # (case-insensitive). This is the ONLY reliable way to detect person
    # names regardless of capitalisation or spaCy's NER accuracy.
    query_words = [
        w for w in re.sub(r"[^a-zA-Z\s]", "", original_query).lower().split()
        if w not in STOPWORDS_FOR_NAMES and len(w) > 1
    ]

    return {
        "objects": nouns,
        "verbs": verbs,
        "adjectives": adjectives,
        "all_terms": all_terms,
        "year": year,
        "month": month,
        "time_of_day": time_of_day,
        "min_faces": min_faces,
        "location_hints": location_hints,
        "person_hints": [],       # Deprecated — backend uses query_words instead
        "negated_terms": negated_terms,
        "query_words": query_words,  # Every meaningful word — backend checks face DB
    }
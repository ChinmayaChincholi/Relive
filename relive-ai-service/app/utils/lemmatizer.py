"""
Lightweight rule-based lemmatizer — deliberately NOT spaCy/a full NLP
lemmatizer, to avoid a heavy new dependency for what is, for this app's
vocabulary (mostly common nouns/adjectives/verbs from VLM output), a mostly
regular problem. Mirrored by LemmatizerUtil.java on the query side so
import-time keys and query-time lookups land on the same canonical form.
If precision issues show up in practice with irregular words, swap this for
a real NLP lemmatizer later — this is a pragmatic starting point, not a
claim of linguistic completeness.
"""

_IRREGULAR = {
    "children": "child", "people": "person", "men": "man", "women": "woman",
    "mice": "mouse", "geese": "goose", "feet": "foot", "teeth": "tooth",
    "leaves": "leaf", "knives": "knife", "wolves": "wolf", "lives": "life",
    "went": "go", "gone": "go", "ate": "eat", "eaten": "eat",
    "ran": "run", "swam": "swim", "sat": "sit", "stood": "stand",
}


def lemmatize(word: str) -> str:
    w = word.strip().lower()
    if not w:
        return w
    if w in _IRREGULAR:
        return _IRREGULAR[w]

    if w.endswith("ies") and len(w) > 4:
        return w[:-3] + "y"
    if w.endswith(("ses", "xes", "zes", "ches", "shes")) and len(w) > 4:
        return w[:-2]
    if w.endswith("s") and not w.endswith("ss") and len(w) > 3:
        return w[:-1]

    if w.endswith("ing") and len(w) > 5:
        stem = w[:-3]
        if len(stem) >= 2 and stem[-1] == stem[-2] and stem[-1] not in "aeiou":
            return stem[:-1]  # running -> run
        return stem

    if w.endswith("ed") and len(w) > 4:
        stem = w[:-2]
        if len(stem) >= 2 and stem[-1] == stem[-2] and stem[-1] not in "aeiou":
            return stem[:-1]  # stopped -> stop
        return stem

    return w
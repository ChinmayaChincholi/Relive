"""
Lemmatizer used at image-import time to normalize VLM-generated words before
storage. This used to be a hand-rolled regex heuristic; it went through two
rounds of new exception rules after real production words kept exposing
gaps (silent-e restoration, doubled-consonant undo, "-ing" nouns that were
never gerunds — see git history for the two prior versions). Every fix
uncovered a new failure class, because English verb morphology genuinely
requires dictionary knowledge, not just phonological pattern-matching.

Replaced entirely with simplemma (MIT-licensed, offline dictionary lookup,
no heavy NLP dependency, no runtime download — its data ships inside the
pip package). Verified against every word that broke the old heuristic
across both production images, plus ~60 additional common English words
chosen to probe likely remaining gaps (see PR description / chat log for
the full test) — it resolves essentially all of them correctly, with the
one observed miss ("singing" -> "singe") landing on a real English word
in the wrong sense, never a non-existent fragment.

Only two things stay custom, because they aren't lemmatization at all:
- British/American spelling canonicalization, so "grey" and "gray" don't
  fragment the index into two different keys.
- A cheap early return for already-lemmatized-looking hyphenated compounds,
  kept mainly as a defensive no-op (simplemma already handles these safely
  on its own, verified against "orange-red", "blue-gray", "flip-flops").
"""

import simplemma

# Common British/American (and similar) spelling variants, canonicalized to
# one form so the index doesn't fragment — a photo tagged "grey" should be
# found by a search for "gray" and vice versa. Not something a dictionary
# lemmatizer handles, since these are two equally "correct" spellings of the
# same word, not an inflected/base-form relationship.
_SPELLING_VARIANTS = {
    "grey": "gray", "colour": "color", "favourite": "favorite",
    "aluminium": "aluminum", "mould": "mold", "jewellery": "jewelry",
    "tyre": "tire", "centre": "center", "theatre": "theater",
    "metre": "meter", "litre": "liter",
}


def lemmatize(word: str) -> str:
    w = word.strip().lower()
    if not w:
        return w

    w = _SPELLING_VARIANTS.get(w, w)

    if "-" in w:
        # Compound descriptors ("orange-red", "flip-flop"). simplemma already
        # handles these safely on its own — this is a defensive no-op, kept
        # so a future simplemma version change can't silently start mangling
        # these without a very visible diff here.
        return w

    return simplemma.lemmatize(w, lang="en")
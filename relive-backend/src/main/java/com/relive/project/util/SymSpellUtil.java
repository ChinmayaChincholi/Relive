package com.relive.project.util;

import java.util.*;

// Self-contained SymSpell-style corrector (deletion-based, max edit distance
// 2) — chosen over an external library since we're seeding the dictionary
// from this app's own data (person names, locations, vocabulary keywords),
// not a generic English wordlist, so a compact hand-rolled implementation
// avoids an unverified new dependency for a well-understood algorithm.
public class SymSpellUtil {

    private static final int MAX_EDIT_DISTANCE = 2;

    // Common English function words (articles, conjunctions, prepositions)
    // that should never be run through dictionary correction at all. This is
    // a closed, well-defined grammatical class, not a content judgment call
    // — confirmed bug: every one of these was getting silently rewritten
    // into an unrelated stored keyword ("of" -> "sofa", "and" -> "sand",
    // "or" -> "form", "but" -> "big", "not" -> "noon") because a 2-3 letter
    // word is within edit-distance 2 of a huge fraction of similarly-short
    // dictionary words. Mirrors llm_model.py's _FALLBACK_STOPWORDS on the
    // AI-service side — kept in sync manually since they're separate
    // languages/services; if one changes, check the other.
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "of", "in", "on", "at", "and", "or", "but", "not",
            "photo", "photos", "picture", "pictures", "image", "images", "pic", "pics",
            "me", "my", "give", "show", "find", "with", "from", "for", "to"
    );

    private final Map<String, List<String>> deletionIndex = new HashMap<>();
    private final Set<String> dictionary = new HashSet<>();

    public void rebuild(Collection<String> words) {
        deletionIndex.clear();
        dictionary.clear();
        for (String raw : words) {
            if (raw == null) continue;
            String w = raw.trim().toLowerCase();
            if (w.isEmpty()) continue;
            dictionary.add(w);
            for (String deletion : generateDeletions(w, MAX_EDIT_DISTANCE)) {
                deletionIndex.computeIfAbsent(deletion, k -> new ArrayList<>()).add(w);
            }
        }
    }

    /** Returns the best correction for a single word, or the word unchanged if
     *  it's already in the dictionary, a stopword, or no correction is found
     *  within the (length-aware) max edit distance. */
    public String correct(String word) {
        String w = word.trim().toLowerCase();
        if (w.isEmpty() || dictionary.contains(w) || STOPWORDS.contains(w)) return w;

        // Length-aware distance cap, as defense in depth beyond the fixed
        // stopword list above: a stopword list can only ever cover a known,
        // closed set of words, but the same over-correction risk applies to
        // ANY short word not in that list (a genuinely short vocab word, a
        // short mistyped name, etc.) — for a word this short, allowing 2
        // edits relative to its own length is close to meaningless, since
        // it can differ in most of its letters and still "match". Longer
        // words keep the full distance-2 tolerance, where it's actually a
        // meaningful signal of a real typo rather than noise.
        int maxDistance = (w.length() <= 3) ? 1 : MAX_EDIT_DISTANCE;

        Set<String> candidates = new HashSet<>();
        if (deletionIndex.containsKey(w)) candidates.addAll(deletionIndex.get(w));

        for (String deletion : generateDeletions(w, maxDistance)) {
            if (dictionary.contains(deletion)) candidates.add(deletion);
            if (deletionIndex.containsKey(deletion)) candidates.addAll(deletionIndex.get(deletion));
        }

        if (candidates.isEmpty()) return w;

        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = levenshtein(w, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return (best != null && bestDistance <= maxDistance) ? best : w;
    }

    /** Correct every whitespace-separated token in a query independently. */
    public String correctQuery(String query) {
        String[] tokens = query.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) result.append(" ");
            String token = tokens[i];
            // Only correct a token that is ENTIRELY letters. A token mixing
            // letters with digits/punctuation (an ordinal like "3rd", a
            // hyphenated word, etc.) can't be safely corrected by
            // substituting back a letters-only candidate — that silently
            // discards the non-letter characters. Confirmed bug: "3rd" was
            // being corrected as "rd" and the "3" vanished, corrupting date
            // queries like "3rd June 2023" into unparseable text. Any token
            // that isn't purely letters is left completely untouched, exactly
            // like a purely-numeric token ("2023") already was.
            if (token.matches("[a-zA-Z]+")) {
                result.append(correct(token));
            } else {
                result.append(token);
            }
        }
        return result.toString();
    }

    private Set<String> generateDeletions(String word, int maxDistance) {
        Set<String> result = new HashSet<>();
        Set<String> current = new HashSet<>();
        current.add(word);
        for (int d = 0; d < maxDistance; d++) {
            Set<String> next = new HashSet<>();
            for (String w : current) {
                for (int i = 0; i < w.length(); i++) {
                    next.add(w.substring(0, i) + w.substring(i + 1));
                }
            }
            result.addAll(next);
            current = next;
        }
        return result;
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
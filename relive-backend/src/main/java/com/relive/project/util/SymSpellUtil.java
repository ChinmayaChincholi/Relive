package com.relive.project.util;

import java.util.*;

// Self-contained SymSpell-style corrector (deletion-based, max edit distance
// 2) — chosen over an external library since we're seeding the dictionary
// from this app's own data (person names, locations, vocabulary keywords),
// not a generic English wordlist, so a compact hand-rolled implementation
// avoids an unverified new dependency for a well-understood algorithm.
public class SymSpellUtil {

    private static final int MAX_EDIT_DISTANCE = 2;

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
     *  it's already in the dictionary or no correction is found within
     *  MAX_EDIT_DISTANCE. */
    public String correct(String word) {
        String w = word.trim().toLowerCase();
        if (w.isEmpty() || dictionary.contains(w)) return w;

        Set<String> candidates = new HashSet<>();
        if (deletionIndex.containsKey(w)) candidates.addAll(deletionIndex.get(w));

        for (String deletion : generateDeletions(w, MAX_EDIT_DISTANCE)) {
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
        return (best != null && bestDistance <= MAX_EDIT_DISTANCE) ? best : w;
    }

    /** Correct every whitespace-separated token in a query independently. */
    public String correctQuery(String query) {
        String[] tokens = query.split("\\s+");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) result.append(" ");
            String token = tokens[i];
            String stripped = token.replaceAll("[^a-zA-Z]", "");
            if (stripped.isEmpty()) {
                result.append(token);
            } else {
                result.append(correct(stripped));
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
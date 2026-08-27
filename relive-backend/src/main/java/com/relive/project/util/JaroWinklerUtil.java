package com.relive.project.util;

public final class JaroWinklerUtil {

    private JaroWinklerUtil() {
    }

    public static final double DEFAULT_THRESHOLD = 0.85;

    public static double similarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;

        String a = s1.toLowerCase().trim();
        String b = s2.toLowerCase().trim();

        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        double jaro = jaroSimilarity(a, b);

        int prefixLength = 0;
        int maxPrefix = Math.min(4, Math.min(a.length(), b.length()));
        while (prefixLength < maxPrefix && a.charAt(prefixLength) == b.charAt(prefixLength)) {
            prefixLength++;
        }

        return jaro + (prefixLength * 0.1 * (1 - jaro));
    }

    private static double jaroSimilarity(String a, String b) {
        int aLen = a.length();
        int bLen = b.length();

        if (aLen == 0 && bLen == 0) return 1.0;
        if (aLen == 0 || bLen == 0) return 0.0;

        int matchDistance = Math.max(aLen, bLen) / 2 - 1;
        if (matchDistance < 0) matchDistance = 0;

        boolean[] aMatches = new boolean[aLen];
        boolean[] bMatches = new boolean[bLen];

        int matches = 0;
        for (int i = 0; i < aLen; i++) {
            int start = Math.max(0, i - matchDistance);
            int end = Math.min(i + matchDistance + 1, bLen);

            for (int j = start; j < end; j++) {
                if (bMatches[j]) continue;
                if (a.charAt(i) != b.charAt(j)) continue;
                aMatches[i] = true;
                bMatches[j] = true;
                matches++;
                break;
            }
        }

        if (matches == 0) return 0.0;

        double transpositions = 0;
        int k = 0;
        for (int i = 0; i < aLen; i++) {
            if (!aMatches[i]) continue;
            while (!bMatches[k]) k++;
            if (a.charAt(i) != b.charAt(k)) transpositions++;
            k++;
        }
        transpositions /= 2.0;

        return ((double) matches / aLen
                + (double) matches / bLen
                + (matches - transpositions) / matches) / 3.0;
    }

    public static boolean containsFuzzyToken(String haystack, String needle, double threshold) {
        if (haystack == null || needle == null) return false;
        String[] tokens = haystack.split("[,\\s()]+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            if (similarity(token, needle) >= threshold) return true;
        }
        return false;
    }

    public static String bestMatch(String query, Iterable<String> candidates, double threshold) {
        String best = null;
        double bestScore = threshold;
        for (String candidate : candidates) {
            double score = similarity(query, candidate);
            if (score >= bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }
}
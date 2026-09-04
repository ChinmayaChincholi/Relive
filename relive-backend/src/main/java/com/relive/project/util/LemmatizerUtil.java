package com.relive.project.util;

import java.util.Map;

public final class LemmatizerUtil {

    private LemmatizerUtil() {}

    private static final Map<String, String> IRREGULAR = Map.ofEntries(
            Map.entry("children", "child"), Map.entry("people", "person"),
            Map.entry("men", "man"), Map.entry("women", "woman"),
            Map.entry("mice", "mouse"), Map.entry("geese", "goose"),
            Map.entry("feet", "foot"), Map.entry("teeth", "tooth"),
            Map.entry("leaves", "leaf"), Map.entry("knives", "knife"),
            Map.entry("wolves", "wolf"), Map.entry("lives", "life"),
            Map.entry("went", "go"), Map.entry("gone", "go"),
            Map.entry("ate", "eat"), Map.entry("eaten", "eat"),
            Map.entry("ran", "run"), Map.entry("swam", "swim"),
            Map.entry("sat", "sit"), Map.entry("stood", "stand")
    );

    public static String lemmatize(String word) {
        if (word == null) return null;
        String w = word.trim().toLowerCase();
        if (w.isEmpty()) return w;
        if (IRREGULAR.containsKey(w)) return IRREGULAR.get(w);

        if (w.endsWith("ies") && w.length() > 4) return w.substring(0, w.length() - 3) + "y";
        if ((w.endsWith("ses") || w.endsWith("xes") || w.endsWith("zes")
                || w.endsWith("ches") || w.endsWith("shes")) && w.length() > 4) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && !w.endsWith("ss") && w.length() > 3) {
            return w.substring(0, w.length() - 1);
        }

        if (w.endsWith("ing") && w.length() > 5) {
            String stem = w.substring(0, w.length() - 3);
            if (stem.length() >= 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)
                    && "aeiou".indexOf(stem.charAt(stem.length() - 1)) == -1) {
                return stem.substring(0, stem.length() - 1); // running -> run
            }
            return stem;
        }

        if (w.endsWith("ed") && w.length() > 4) {
            String stem = w.substring(0, w.length() - 2);
            if (stem.length() >= 2 && stem.charAt(stem.length() - 1) == stem.charAt(stem.length() - 2)
                    && "aeiou".indexOf(stem.charAt(stem.length() - 1)) == -1) {
                return stem.substring(0, stem.length() - 1); // stopped -> stop
            }
            return stem;
        }

        return w;
    }
}
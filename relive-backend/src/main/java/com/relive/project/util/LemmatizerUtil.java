package com.relive.project.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Query-time word normalization. This used to independently DECIDE an
 * inflected word's base form (e.g. undo "-ing"/"-ed" and guess whether a
 * dropped silent "e" needed restoring) — that approach produced real,
 * observed bugs across two rounds of fixes ("smiling" -> "smil", "letter"
 * (via "lettering") -> "lettere"), because doing this correctly for
 * arbitrary English words genuinely requires dictionary knowledge that a
 * suffix-stripping heuristic doesn't have. Python now handles storage-time
 * lemmatization with a real dictionary lookup (simplemma) — see
 * app/utils/lemmatizer.py.
 * <p>
 * Java has no equivalently lightweight dictionary lemmatizer available, so
 * instead of writing a third heuristic that will eventually find its own
 * failure class, this class now only does two things:
 * <p>
 * 1. Safe, letter-REMOVING-ONLY normalization (lowercase/trim, spelling
 * variants, plural stripping with the same non-plural-suffix guard as
 * before) that can be applied with confidence and never invents a word.
 * 2. candidateForms(word) — a list of PLAUSIBLE additional forms (including
 * some of the same doubling/silent-e patterns from the old heuristic),
 * offered as candidates, not decisions. SearchService checks each
 * candidate against the actual stored keyword index and uses whichever
 * one, if any, is a real stored word — the database (populated only by
 * the correct Python lemma) acts as the dictionary Java doesn't have.
 * A wrong candidate here can never corrupt data or reach the user: it's
 * only ever used for a read-time lookup, and simply returns no matches
 * if it isn't real.
 */
public final class LemmatizerUtil {

    private LemmatizerUtil() {
    }

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

    private static final Map<String, String> SPELLING_VARIANTS = Map.ofEntries(
            Map.entry("grey", "gray"), Map.entry("colour", "color"),
            Map.entry("favourite", "favorite"), Map.entry("aluminium", "aluminum"),
            Map.entry("mould", "mold"), Map.entry("jewellery", "jewelry"),
            Map.entry("tyre", "tire"), Map.entry("centre", "center"),
            Map.entry("theatre", "theater"), Map.entry("metre", "meter"),
            Map.entry("litre", "liter")
    );

    // Suffixes where a trailing "s" is part of the word itself, not a plural
    // marker (e.g. "enormous", "spacious") — this rule only ever REMOVES a
    // letter and is safe to apply with confidence, unlike -ing/-ed handling.
    private static final String[] NON_PLURAL_S_SUFFIXES =
            {"ous", "ious", "eous", "us", "is", "os", "as", "ss"};

    private static final String VOWELS = "aeiou";
    private static final Set<String> FLOSS_DOUBLE_EXCEPTIONS = Set.of("s", "l", "f");

    private static boolean endsWithAny(String w, String[] suffixes) {
        for (String suffix : suffixes) {
            if (w.endsWith(suffix)) return true;
        }
        return false;
    }

    /**
     * Safe, letter-removing-only normalization. Used directly for storage
     * keys (though storage now comes pre-lemmatized from Python) and as the
     * first, most-likely candidate at query time.
     */
    public static String normalize(String word) {
        if (word == null) return null;
        String w = word.trim().toLowerCase();
        if (w.isEmpty()) return w;

        w = SPELLING_VARIANTS.getOrDefault(w, w);

        if (IRREGULAR.containsKey(w)) return IRREGULAR.get(w);

        if (w.contains("-")) {
            return w; // compound descriptors — leave untouched
        }

        if (w.endsWith("ies") && w.length() > 4) return w.substring(0, w.length() - 3) + "y";
        if (endsWithAny(w, new String[]{"ses", "xes", "zes", "ches", "shes"}) && w.length() > 4) {
            return w.substring(0, w.length() - 2);
        }
        if (w.endsWith("s") && !endsWithAny(w, NON_PLURAL_S_SUFFIXES) && w.length() > 3) {
            return w.substring(0, w.length() - 1);
        }

        return w;
    }

    /**
     * Ordered list of plausible forms for a query word, most-likely-correct
     * first, for SearchService to test against the real keyword index.
     * Deliberately generous — includes the same doubled-consonant-undo and
     * silent-e-restoration patterns the old heuristic used to apply
     * unconditionally — but here they're only ever proposals. If a wrong
     * guess isn't a real stored word, the DB check simply skips it; nothing
     * downstream ever treats a candidate as correct just because it was
     * generated.
     */
    public static List<String> candidateForms(String word) {
        Set<String> candidates = new LinkedHashSet<>();
        String base = normalize(word);
        candidates.add(base);

        addIngEdCandidates(base, "ing", candidates);
        addIngEdCandidates(base, "ed", candidates);

        return new ArrayList<>(candidates);
    }

    private static void addIngEdCandidates(String w, String suffix, Set<String> out) {
        if (!w.endsWith(suffix) || w.length() <= suffix.length() + 2) {
            return;
        }
        String stem = w.substring(0, w.length() - suffix.length());
        out.add(stem);                       // e.g. "smiling" -> "smil"
        out.add(stem + "e");                 // e.g. "smiling" -> "smile"

        int len = stem.length();
        if (len >= 2 && stem.charAt(len - 1) == stem.charAt(len - 2)
                && VOWELS.indexOf(stem.charAt(len - 1)) == -1) {
            String lastChar = String.valueOf(stem.charAt(len - 1));
            if (!FLOSS_DOUBLE_EXCEPTIONS.contains(lastChar)) {
                out.add(stem.substring(0, len - 1)); // e.g. "running" -> "run"
            }
        }
    }
}
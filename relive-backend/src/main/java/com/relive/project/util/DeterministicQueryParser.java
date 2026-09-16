package com.relive.project.util;

import com.relive.project.dto.SearchExpression;
import com.relive.project.dto.TermLeaf;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Instant Search's query -> SearchExpression tree builder.
 *
 * No LLM call, no AI-service round trip --- this runs entirely in-process
 * against the already spell-corrected query text (same SymSpellUtil pass
 * Advanced Search uses) and hands its output to the EXACT SAME evaluator
 * (SearchService.classifyAndEvaluate / resolveAndLookupLeaf) that Advanced
 * Search uses. Only tree CONSTRUCTION differs between the two pipelines;
 * entity resolution (fuzzy person/location matching, lemmatized vocab
 * lookup, date/time range math) is fully shared, so a query this class CAN
 * parse behaves identically to Advanced Search.
 *
 * Deliberately mirrors QueryTreeRepair's own conservative trigger-word
 * design (same NEGATION_TRIGGERS set, same "do nothing / degrade safely
 * when ambiguous" philosophy) instead of inventing a second, divergent
 * grammar that could silently drift from Advanced Search's behavior.
 *
 * Known, intentional limitations:
 * - Only a fixed, closed trigger-word vocabulary is understood (see
 *   NOT_TRIGGERS / OR_TRIGGERS / AND_TRIGGERS below). Phrasing outside
 *   this list is read as plain VOCAB text, not an operator --- e.g. "aside
 *   from X" won't be recognized as negation the way an LLM would infer it.
 * - Once a query enters NOT or OR mode, a bare "and"/"or" immediately
 *   after does NOT switch the mode back --- it's read as "also this, same
 *   bucket". This is what makes "sasha and abraham" both land in
 *   must_not for "... without sasha and abraham". Only clause punctuation
 *   (, ; .) resets back to the default AND mode. Mixing OR and NOT in one
 *   clause with no punctuation between them is a rare, genuinely
 *   ambiguous case; the conservative choice is to keep whatever mode was
 *   already active rather than guess at a switch.
 * - A date/time expression needs a year (for dates) somewhere in it to be
 *   treated as DATE; otherwise it's left as ordinary VOCAB text, matching
 *   the same fallback Advanced Search's LLM prompt is instructed to use.
 * - Cross-month/cross-year ranges ("June to August 2024", "2020 to 2023")
 *   ARE computed with real calendar arithmetic here (java.time), which is
 *   actually something Advanced Search's LLM is explicitly told NOT to
 *   attempt itself (it's told never to compute exact month lengths) --- so
 *   this pipeline is, if anything, more precise for that specific case.
 */
public final class DeterministicQueryParser {

    private DeterministicQueryParser() {
    }

    // ------------------------------------------------------------------
    // Word lists
    // ------------------------------------------------------------------

    // Never becomes a search term and never changes must/should/must_not
    // mode --- pure noise words. Deliberately a SEPARATE list from
    // SymSpellUtil.STOPWORDS: that list exists purely to protect spelling
    // correction and includes "and"/"or"/"not"/"but", which we need as
    // live structural signals here, not noise to discard.
    private static final Set<String> FILLER_WORDS = Set.of(
            "photo", "photos", "picture", "pictures", "image", "images", "pic", "pics",
            "me", "my", "give", "show", "find", "of", "the", "a", "an",
            "in", "on", "at", "with", "for", "to", "from", "either", "but", "also"
    );

    // Deliberately IDENTICAL to QueryTreeRepair.NEGATION_TRIGGERS --- kept
    // in sync on purpose so a query that Advanced Search's repair step
    // would have to fix instead parses correctly here from the start, and
    // so both pipelines agree on what counts as negation.
    private static final Set<String> NOT_TRIGGERS = Set.of("without", "excluding", "except", "not");

    private static final Set<String> OR_TRIGGERS = Set.of("or");

    private static final Set<String> AND_TRIGGERS = Set.of("and");

    private static final Set<String> CLAUSE_BOUNDARIES = Set.of(",", ";", ".");

    private static final Set<String> MONTHS = Set.of(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
    );

    private static final Map<String, String> MONTH_TO_NUMBER = buildMonthMap();

    private static Map<String, String> buildMonthMap() {
        String[] names = {"january", "february", "march", "april", "may", "june",
                "july", "august", "september", "october", "november", "december"};
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            map.put(names[i], String.format("%02d", i + 1));
        }
        return map;
    }

    // Fixed clock windows for words that describe WHEN in the day a photo
    // was taken --- the same judgment call Advanced Search's LLM prompt
    // makes, just hard-coded here instead of inferred per-query.
    private static final Map<String, String[]> TIME_OF_DAY = Map.of(
            "morning", new String[]{"05:00", "11:59"},
            "afternoon", new String[]{"12:00", "16:59"},
            "evening", new String[]{"17:00", "20:59"},
            "night", new String[]{"21:00", "04:59"}
    );

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9]+|[,;.]");
    private static final Pattern DAY_PATTERN = Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)?");

    private static final int MODE_AND = 0;
    private static final int MODE_OR = 1;
    private static final int MODE_NOT = 2;

    // Sentinel used internally to blank out the 2nd..nth token of a
    // matched multi-word entity span. Never leaks past extractMultiWordEntities.
    private static final Object CONSUMED_MARKER = new Object();

    // ------------------------------------------------------------------
    // Entry point
    // ------------------------------------------------------------------

    public static SearchExpression parse(String correctedQuery, List<String> knownNames, List<String> knownLocations) {
        List<String> tokens = tokenize(correctedQuery);
        List<Object> afterDates = extractDateTimeSpans(tokens);
        List<Object> afterEntities = extractMultiWordEntities(afterDates, knownNames, knownLocations);
        return buildTree(afterEntities);
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null) return tokens;
        Matcher m = TOKEN_PATTERN.matcher(text.toLowerCase());
        while (m.find()) tokens.add(m.group());
        return tokens;
    }

    // ------------------------------------------------------------------
    // Pass 1 --- date / time extraction (including ranges)
    //
    // Runs BEFORE anything else touches "from"/"to"/"between"/"and",
    // since those words mean something different inside a date phrase
    // than they do as boolean operators.
    // ------------------------------------------------------------------

    private static List<Object> extractDateTimeSpans(List<String> tokens) {
        List<Object> out = new ArrayList<>();
        int i = 0;
        while (i < tokens.size()) {
            RangeMatch range = tryParseRange(tokens, i);
            if (range != null) {
                out.add(range.leaf);
                i = range.endIndex;
                continue;
            }

            DateAtom atom = tryParseDateAtom(tokens, i);
            if (atom != null && atom.hasYear()) {
                out.add(atomToLeaf(atom));
                i = atom.endIndex;
                continue;
            }

            String word = tokens.get(i);
            if (TIME_OF_DAY.containsKey(word)) {
                String[] window = TIME_OF_DAY.get(word);
                out.add(timeLeaf(window[0], window[1]));
                i++;
                continue;
            }

            out.add(word);
            i++;
        }
        return out;
    }

    private static final class DateAtom {
        String day;
        String month;
        String year;
        int endIndex;

        boolean hasYear() {
            return year != null;
        }
    }

    private static final class RangeMatch {
        TermLeaf leaf;
        int endIndex;
    }

    private static RangeMatch rangeMatch(TermLeaf leaf, int endIndex) {
        RangeMatch rm = new RangeMatch();
        rm.leaf = leaf;
        rm.endIndex = endIndex;
        return rm;
    }

    /** Tries "between A and B" / "from A to B" / "A to B", for both date and time-of-day atoms. */
    private static RangeMatch tryParseRange(List<String> tokens, int start) {
        if (start >= tokens.size()) return null;

        if (tokens.get(start).equals("between")) {
            RangeMatch time = tryParseTimeRange(tokens, start + 1, "and");
            if (time != null) return time;

            DateAtom a = tryParseDateAtom(tokens, start + 1);
            if (a != null && a.endIndex < tokens.size() && tokens.get(a.endIndex).equals("and")) {
                DateAtom b = tryParseDateAtom(tokens, a.endIndex + 1);
                if (b != null) {
                    TermLeaf leaf = combineRangeAtoms(a, b);
                    if (leaf != null) return rangeMatch(leaf, b.endIndex);
                }
            }
            return null;
        }

        int atomStart = tokens.get(start).equals("from") ? start + 1 : start;

        RangeMatch time = tryParseTimeRange(tokens, atomStart, "to");
        if (time != null) return time;

        DateAtom a = tryParseDateAtom(tokens, atomStart);
        if (a != null && a.endIndex < tokens.size() && tokens.get(a.endIndex).equals("to")) {
            DateAtom b = tryParseDateAtom(tokens, a.endIndex + 1);
            if (b != null) {
                TermLeaf leaf = combineRangeAtoms(a, b);
                if (leaf != null) return rangeMatch(leaf, b.endIndex);
            }
        }
        return null;
    }

    private static RangeMatch tryParseTimeRange(List<String> tokens, int start, String joiner) {
        if (start >= tokens.size()) return null;
        String w0 = tokens.get(start);
        if (!TIME_OF_DAY.containsKey(w0)) return null;

        int joinerIdx = start + 1;
        if (joinerIdx >= tokens.size() || !tokens.get(joinerIdx).equals(joiner)) return null;

        int secondIdx = joinerIdx + 1;
        if (secondIdx >= tokens.size()) return null;
        String w1 = tokens.get(secondIdx);
        if (!TIME_OF_DAY.containsKey(w1)) return null;

        TermLeaf leaf = timeLeaf(TIME_OF_DAY.get(w0)[0], TIME_OF_DAY.get(w1)[1]);
        return rangeMatch(leaf, secondIdx + 1);
    }

    private static DateAtom tryParseDateAtom(List<String> tokens, int start) {
        if (start >= tokens.size()) return null;
        String t0 = tokens.get(start);

        Integer day0 = parseDay(t0);
        if (day0 != null && start + 1 < tokens.size() && MONTHS.contains(tokens.get(start + 1))) {
            DateAtom atom = new DateAtom();
            atom.day = String.format("%02d", day0);
            atom.month = MONTH_TO_NUMBER.get(tokens.get(start + 1));
            if (start + 2 < tokens.size() && isYear(tokens.get(start + 2))) {
                atom.year = tokens.get(start + 2);
                atom.endIndex = start + 3;
            } else {
                atom.endIndex = start + 2;
            }
            return atom;
        }

        if (MONTHS.contains(t0)) {
            String month = MONTH_TO_NUMBER.get(t0);
            if (start + 1 < tokens.size()) {
                Integer day1 = parseDay(tokens.get(start + 1));
                if (day1 != null) {
                    DateAtom atom = new DateAtom();
                    atom.day = String.format("%02d", day1);
                    atom.month = month;
                    if (start + 2 < tokens.size() && isYear(tokens.get(start + 2))) {
                        atom.year = tokens.get(start + 2);
                        atom.endIndex = start + 3;
                    } else {
                        atom.endIndex = start + 2;
                    }
                    return atom;
                }
                if (isYear(tokens.get(start + 1))) {
                    DateAtom atom = new DateAtom();
                    atom.month = month;
                    atom.year = tokens.get(start + 1);
                    atom.endIndex = start + 2;
                    return atom;
                }
            }
            DateAtom atom = new DateAtom();
            atom.month = month;
            atom.endIndex = start + 1;
            return atom; // no year found nearby --- caller discards this (see hasYear() check)
        }

        if (isYear(t0)) {
            DateAtom atom = new DateAtom();
            atom.year = t0;
            atom.endIndex = start + 1;
            return atom;
        }

        return null;
    }

    private static Integer parseDay(String token) {
        Matcher m = DAY_PATTERN.matcher(token);
        if (!m.matches()) return null;
        int day = Integer.parseInt(m.group(1));
        return (day >= 1 && day <= 31) ? day : null;
    }

    private static boolean isYear(String token) {
        return token.matches("(19|20)\\d{2}");
    }

    private static TermLeaf atomToLeaf(DateAtom atom) {
        if (atom.day != null && atom.month != null) {
            return dateLeaf(atom.day + "-" + atom.month + "-" + atom.year, null);
        }
        if (atom.month != null) {
            return dateLeaf("01-" + atom.month + "-" + atom.year, atom.month + "-" + atom.year);
        }
        return dateLeaf("01-01-" + atom.year, atom.year);
    }

    /**
     * Combines two date atoms into one DATE leaf spanning both. Unlike the
     * LLM (deliberately told never to do calendar arithmetic itself), this
     * is plain Java and can safely compute a real month length, so a
     * genuine cross-month range like "June to August 2024" gets a true
     * 01-06-2024 -> 31-08-2024 span, not just the same-month shortcut
     * Advanced Search's prompt documents.
     */
    private static TermLeaf combineRangeAtoms(DateAtom a, DateAtom b) {
        String yearA = a.year != null ? a.year : b.year;
        String yearB = b.year != null ? b.year : a.year;
        if (yearA == null || yearB == null) return null; // no year anywhere --- can't build a reliable range

        // Pure year-to-year range (neither side named a month) --- reuses
        // the existing single-value/"YYYY" range_end branch in
        // SearchService.lookupDate() as-is; it already spans Jan 1 of the
        // start year to Dec 31 of whatever year range_end names, so this
        // works correctly even when the two years differ.
        if (a.month == null && b.month == null) {
            return dateLeaf("01-01-" + yearA, yearB);
        }

        String startDay = a.day != null ? a.day : "01";
        String startMonth = a.month != null ? a.month : (b.month != null ? b.month : "01");
        String startValue = startDay + "-" + startMonth + "-" + yearA;

        String endValue;
        if (b.day != null) {
            String endMonth = b.month != null ? b.month : startMonth;
            endValue = b.day + "-" + endMonth + "-" + yearB;
        } else if (b.month != null) {
            endValue = lastDayOfMonth(b.month, yearB);
        } else {
            endValue = "31-12-" + yearB;
        }
        return dateLeaf(startValue, endValue);
    }

    private static String lastDayOfMonth(String mm, String yyyy) {
        YearMonth ym = YearMonth.of(Integer.parseInt(yyyy), Integer.parseInt(mm));
        return String.format("%02d-%s-%s", ym.lengthOfMonth(), mm, yyyy);
    }

    private static TermLeaf dateLeaf(String value, String rangeEnd) {
        TermLeaf leaf = new TermLeaf();
        leaf.setDomain("DATE");
        leaf.setValue(value);
        leaf.setRangeEnd(rangeEnd);
        return leaf;
    }

    private static TermLeaf timeLeaf(String start, String end) {
        TermLeaf leaf = new TermLeaf();
        leaf.setDomain("TIME");
        leaf.setValue(start);
        leaf.setRangeEnd(end); // always a real window, even for a single time-of-day word
        return leaf;
    }

    // ------------------------------------------------------------------
    // Pass 2 --- multi-word known-entity matching ("kabir singh", "tamil
    // nadu", "los angeles"). Single-word names/locations need no special
    // handling here: resolveAndLookupLeaf() downstream already fuzzy-
    // matches every leaf's value against the full known-name/location
    // lists regardless of the domain this class assigns, so a lone token
    // like "riya" or "bangalore" resolves correctly on its own. This pass
    // exists ONLY so a multi-word entity doesn't get split into several
    // separately-ANDed single-word leaves.
    // ------------------------------------------------------------------

    private static List<Object> extractMultiWordEntities(List<Object> stream, List<String> knownNames,
                                                         List<String> knownLocations) {
        List<String> phrases = new ArrayList<>();
        for (String n : knownNames) {
            if (n != null && n.trim().contains(" ")) phrases.add(n.trim().toLowerCase());
        }
        for (String l : knownLocations) {
            if (l != null && l.trim().contains(" ")) phrases.add(l.trim().toLowerCase());
        }
        if (phrases.isEmpty()) return stream;

        // Longest phrases first, so a 3-word name is preferred over
        // accidentally matching just part of it against something else.
        phrases.sort((a, b) -> Integer.compare(wordCount(b), wordCount(a)));

        List<Object> result = new ArrayList<>(stream);
        boolean[] consumed = new boolean[result.size()];

        for (String phrase : phrases) {
            int n = wordCount(phrase);
            if (n < 2) continue;
            for (int i = 0; i + n <= result.size(); i++) {
                if (!isConsumableStringRun(result, consumed, i, n)) continue;

                StringBuilder joined = new StringBuilder();
                for (int j = 0; j < n; j++) {
                    if (j > 0) joined.append(' ');
                    joined.append((String) result.get(i + j));
                }

                if (JaroWinklerUtil.similarity(joined.toString(), phrase) >= JaroWinklerUtil.DEFAULT_THRESHOLD) {
                    TermLeaf leaf = new TermLeaf();
                    leaf.setDomain("VOCAB"); // re-classified correctly downstream, same as every other leaf
                    leaf.setValue(joined.toString());
                    result.set(i, leaf);
                    consumed[i] = true;
                    for (int j = 1; j < n; j++) {
                        consumed[i + j] = true;
                        result.set(i + j, CONSUMED_MARKER);
                    }
                    break; // don't try to re-match this phrase overlapping the span it just consumed
                }
            }
        }

        result.removeIf(o -> o == CONSUMED_MARKER);
        return result;
    }

    private static int wordCount(String phrase) {
        return phrase.trim().split("\\s+").length;
    }

    private static boolean isConsumableStringRun(List<Object> stream, boolean[] consumed, int start, int n) {
        for (int j = 0; j < n; j++) {
            if (consumed[start + j]) return false;
            if (!(stream.get(start + j) instanceof String)) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Pass 3 --- must / should / must_not tree construction
    // ------------------------------------------------------------------

    private static SearchExpression buildTree(List<Object> stream) {
        int mode = MODE_AND;
        List<SearchExpression> mustList = new ArrayList<>();
        List<SearchExpression> shouldList = new ArrayList<>();
        List<SearchExpression> mustNotList = new ArrayList<>();

        for (Object element : stream) {
            if (element instanceof TermLeaf) {
                addToBucket(mode, wrap((TermLeaf) element), mustList, shouldList, mustNotList);
                continue;
            }

            String token = (String) element;

            if (CLAUSE_BOUNDARIES.contains(token)) {
                mode = MODE_AND;
                continue;
            }
            if (NOT_TRIGGERS.contains(token)) {
                mode = MODE_NOT;
                continue;
            }
            if (OR_TRIGGERS.contains(token)) {
                if (mode == MODE_AND) mode = MODE_OR;
                continue;
            }
            if (AND_TRIGGERS.contains(token)) {
                continue; // deliberately no mode change --- see class-level note
            }
            if (FILLER_WORDS.contains(token)) {
                continue;
            }

            TermLeaf leaf = new TermLeaf();
            leaf.setDomain("VOCAB");
            leaf.setValue(token);
            addToBucket(mode, wrap(leaf), mustList, shouldList, mustNotList);
        }

        return assembleRoot(mustList, shouldList, mustNotList);
    }

    private static void addToBucket(int mode, SearchExpression node, List<SearchExpression> must,
                                    List<SearchExpression> should, List<SearchExpression> mustNot) {
        switch (mode) {
            case MODE_OR:
                should.add(node);
                break;
            case MODE_NOT:
                mustNot.add(node);
                break;
            default:
                must.add(node);
                break;
        }
    }

    private static SearchExpression wrap(TermLeaf leaf) {
        SearchExpression node = new SearchExpression();
        node.setTerm(leaf);
        return node;
    }

    private static SearchExpression assembleRoot(List<SearchExpression> must, List<SearchExpression> should,
                                                 List<SearchExpression> mustNot) {
        // Flatten to a single bare leaf for a plain one-term query --- same
        // shape Advanced Search's own single-term example produces.
        if (must.size() == 1 && should.isEmpty() && mustNot.isEmpty()) {
            return must.get(0);
        }
        if (must.isEmpty() && should.size() == 1 && mustNot.isEmpty()) {
            return should.get(0);
        }
        SearchExpression root = new SearchExpression();
        root.getMust().addAll(must);
        root.getShould().addAll(should);
        root.getMustNot().addAll(mustNot);
        return root;
    }
}
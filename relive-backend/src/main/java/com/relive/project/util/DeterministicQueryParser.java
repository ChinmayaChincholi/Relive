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


public final class DeterministicQueryParser {

    private DeterministicQueryParser() {
    }
    private static final Set<String> FILLER_WORDS = Set.of(
            "photo", "photos", "picture", "pictures", "image", "images", "pic", "pics",
            "me", "my", "give", "show", "find", "of", "the", "a", "an",
            "in", "on", "at", "with", "for", "to", "from", "either", "but", "also",
            "taken", "between"
    );

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

    private static final Map<String, String[]> TIME_OF_DAY = Map.of(
            "morning", new String[]{"05:00", "11:59"},
            "afternoon", new String[]{"12:00", "16:59"},
            "evening", new String[]{"17:00", "20:59"},
            "night", new String[]{"21:00", "04:59"}
    );


    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9:]+|[,;.]");
    private static final Pattern DAY_PATTERN = Pattern.compile("(\\d{1,2})(?:st|nd|rd|th)?");

    private static final Pattern LITERAL_TIME_PATTERN = Pattern.compile("(\\d{1,2})(?::(\\d{2}))?(am|pm)?");

    private static final int MODE_AND = 0;
    private static final int MODE_OR = 1;
    private static final int MODE_NOT = 2;

    private static final Object CONSUMED_MARKER = new Object();

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

            TimeAtom literalTime = tryParseLiteralTimeAtom(tokens, i);
            if (literalTime != null) {
                out.add(timeLeaf(literalTime.value, literalTime.value));
                i = literalTime.endIndex;
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

    // A parsed literal clock time, e.g. "9:25 am" -> value="09:25".
    private static final class TimeAtom {
        String value;   // "HH:MM", 24-hour
        int endIndex;
    }

    private static RangeMatch tryParseRange(List<String> tokens, int start) {
        if (start >= tokens.size()) return null;

        if (tokens.get(start).equals("between")) {
            RangeMatch time = tryParseTimeRange(tokens, start + 1, "and");
            if (time != null) return time;

            RangeMatch literalTime = tryParseLiteralTimeRange(tokens, start + 1, "and");
            if (literalTime != null) return literalTime;

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

        RangeMatch literalTime = tryParseLiteralTimeRange(tokens, atomStart, "to");
        if (literalTime != null) return literalTime;

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

    private static RangeMatch tryParseLiteralTimeRange(List<String> tokens, int start, String joiner) {
        TimeAtom a = tryParseLiteralTimeAtom(tokens, start);
        if (a == null) return null;
        if (a.endIndex >= tokens.size() || !tokens.get(a.endIndex).equals(joiner)) return null;
        TimeAtom b = tryParseLiteralTimeAtom(tokens, a.endIndex + 1);
        if (b == null) return null;
        TermLeaf leaf = timeLeaf(a.value, b.value);
        return rangeMatch(leaf, b.endIndex);
    }

    private static TimeAtom tryParseLiteralTimeAtom(List<String> tokens, int start) {
        if (start >= tokens.size()) return null;
        Matcher m = LITERAL_TIME_PATTERN.matcher(tokens.get(start));
        if (!m.matches()) return null;

        int hour;
        try {
            hour = Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        Integer minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : null;
        String meridiem = m.group(3); // fused "am"/"pm", or null
        int endIndex = start + 1;

        if (meridiem == null && start + 1 < tokens.size()) {
            String next = tokens.get(start + 1);
            if (next.equals("am") || next.equals("pm")) {
                meridiem = next;
                endIndex = start + 2;
            }
        }

        if (minute == null && meridiem == null) return null;
        if (minute == null) minute = 0;
        if (minute < 0 || minute > 59) return null;

        int hour24;
        if (meridiem != null) {
            if (hour < 1 || hour > 12) return null;
            if (meridiem.equals("am")) {
                hour24 = (hour == 12) ? 0 : hour;
            } else {
                hour24 = (hour == 12) ? 12 : hour + 12;
            }
        } else {
            if (hour < 0 || hour > 23) return null;
            hour24 = hour;
        }

        TimeAtom atom = new TimeAtom();
        atom.value = String.format("%02d:%02d", hour24, minute);
        atom.endIndex = endIndex;
        return atom;
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
            return atom;
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

    private static TermLeaf combineRangeAtoms(DateAtom a, DateAtom b) {
        String yearA = a.year != null ? a.year : b.year;
        String yearB = b.year != null ? b.year : a.year;
        if (yearA == null || yearB == null) return null; // no year anywhere --- can't build a reliable range

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
        leaf.setRangeEnd(end); // always a real window, even for a single time-of-day word / literal time
        return leaf;
    }

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
                    break;
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
                if (mode == MODE_AND) {
                    mode = MODE_OR;
                    if (!mustList.isEmpty()) {
                        shouldList.add(mustList.remove(mustList.size() - 1));
                    }
                }
                continue;
            }
            if (AND_TRIGGERS.contains(token)) {
                continue;
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
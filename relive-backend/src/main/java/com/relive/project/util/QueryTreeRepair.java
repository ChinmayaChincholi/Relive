package com.relive.project.util;

import com.relive.project.dto.SearchExpression;
import com.relive.project.dto.TermLeaf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Change 6 — deterministic post-parse structural repair for the query
 * expression tree.
 *
 * Qwen occasionally gets the LOGICAL STRUCTURE of a query wrong even when
 * every individual term (domain/value) is extracted correctly — most
 * notably, "photos of X without Y" coming back with Y inside "must" instead
 * of "must_not". This class re-checks the tree Qwen returned against the
 * actual words of the (spell-corrected) query text and, ONLY where the
 * evidence is unambiguous, moves a misplaced term into the bucket the query
 * text actually implies. It fixes three kinds of misplacement:
 *   1. NOT misplacement — a term explicitly negated in the text (see
 *      NEGATION_TRIGGERS) sitting in "must" or "should" instead of
 *      "must_not".
 *   2. OR misplacement — two terms explicitly joined by a bare "or" in the
 *      text sitting together in "must" instead of "should".
 *   3. AND misplacement — the converse: two terms explicitly joined by a
 *      bare "and" sitting together in "should" instead of "must".
 *
 * SAFETY IS THE ENTIRE DESIGN PRINCIPLE HERE, not a footnote: every check
 * below is deliberately conservative, because a wrong automatic move is
 * worse than the bug it's trying to fix — it would silently turn a
 * correctly-parsed query into an incorrectly-parsed one, indistinguishable
 * from a "fixed" query to anyone reading the result. Concretely, that means:
 *   - A term is only ever acted on if its value can be located as an
 *     EXACT, UNIQUE (single-occurrence) span in the query text. Zero
 *     matches or more than one possible match both mean "do nothing, leave
 *     Qwen's placement as-is" — never guess which occurrence applies.
 *   - Negation scope is bounded by nearby clause boundaries ("and", "or",
 *     comma, semicolon, period) so a trigger word from a different clause
 *     can never be misread as governing this term.
 *   - The OR/AND sibling-swap only fires when the two terms are (a)
 *     already siblings in the same list, and (b) separated in the text by
 *     the conjunction word and NOTHING else — this makes an incorrect move
 *     essentially impossible, at the cost of not catching longer chains
 *     (e.g. comma-separated "X, Y, or Z" beyond a pairwise adjacent case),
 *     which are deliberately left untouched rather than guessed at.
 * Doing nothing is always a safe fallback — it reproduces exactly today's
 * (buggy) behavior for any case this class can't confidently resolve, never
 * something worse.
 */
public final class QueryTreeRepair {

    private QueryTreeRepair() {
    }

    // Words that, immediately governing a term within the same clause, mean
    // that term must be excluded rather than required/optional.
    // Deliberately narrow: bare "no" is excluded because in casual phrasing
    // it's too often part of a name or a filler word ("oh no") to trust
    // without more sentence context than a single-word check gives.
    private static final Set<String> NEGATION_TRIGGERS =
            Set.of("without", "excluding", "except", "not");

    // Words/punctuation that close off the "reach" of a negation trigger —
    // once one of these is hit while scanning backward from a term, scanning
    // stops, because anything on the other side of a clause boundary belongs
    // to a different clause and can't govern this term.
    private static final Set<String> CLAUSE_BOUNDARIES =
            Set.of("and", "or", ",", ";", ".");

    private static final int MAX_LOOKBACK = 4;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9]+|[,;.]");
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9]+");

    public static void repair(SearchExpression root, String queryText) {
        if (root == null || queryText == null || queryText.isBlank()) return;
        List<String> tokens = tokenize(queryText);
        if (tokens.isEmpty()) return;

        // A root that is ENTIRELY a single negated leaf (e.g. "photos
        // without a dog", nothing else) needs special handling: there is no
        // parent list to move it out of, since it's not living inside any
        // must/should array — it IS the whole tree.
        if (root.isLeaf() && isNegatedInText(root.getTerm(), tokens)) {
            SearchExpression movedLeaf = new SearchExpression();
            movedLeaf.setTerm(root.getTerm());
            root.setTerm(null);
            root.getMustNot().add(movedLeaf);
            return;
        }

        repairNegation(root, tokens);
        repairConjunctionGrouping(root, tokens);
    }

    // ------------------------------------------------------------------
    // Tokenizing
    // ------------------------------------------------------------------

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(text.toLowerCase());
        while (m.find()) tokens.add(m.group());
        return tokens;
    }

    private static List<String> valueWords(String value) {
        List<String> words = new ArrayList<>();
        if (value == null) return words;
        Matcher m = WORD_PATTERN.matcher(value.toLowerCase());
        while (m.find()) words.add(m.group());
        return words;
    }

    /**
     * Finds every contiguous span in `tokens` that exactly matches `words`
     * (case-insensitive; both lists are already lowercased). Callers only
     * ever act when exactly one span is found — zero or multiple both mean
     * "ambiguous or absent, don't touch this term".
     */
    private static List<int[]> findSpans(List<String> tokens, List<String> words) {
        List<int[]> spans = new ArrayList<>();
        if (words.isEmpty()) return spans;
        outer:
        for (int i = 0; i <= tokens.size() - words.size(); i++) {
            for (int j = 0; j < words.size(); j++) {
                if (!tokens.get(i + j).equals(words.get(j))) continue outer;
            }
            spans.add(new int[]{i, i + words.size() - 1});
        }
        return spans;
    }

    private static int[] uniqueSpan(TermLeaf term, List<String> tokens) {
        if (term == null || term.getValue() == null) return null;
        List<String> words = valueWords(term.getValue());
        if (words.isEmpty()) return null;
        List<int[]> spans = findSpans(tokens, words);
        return spans.size() == 1 ? spans.get(0) : null;
    }

    // ------------------------------------------------------------------
    // NOT (must/should -> must_not) repair
    // ------------------------------------------------------------------

    private static void repairNegation(SearchExpression node, List<String> tokens) {
        processListForNegation(node.getMust(), node, tokens, false);
        processListForNegation(node.getShould(), node, tokens, false);
        processListForNegation(node.getMustNot(), node, tokens, true);
    }

    private static void processListForNegation(List<SearchExpression> siblings,
                                               SearchExpression parent,
                                               List<String> tokens,
                                               boolean isAlreadyMustNot) {
        // Snapshot copy: matched leaves get removed from `siblings` (the
        // live list) mid-loop.
        for (SearchExpression child : new ArrayList<>(siblings)) {
            if (child.isLeaf()) {
                if (!isAlreadyMustNot && isNegatedInText(child.getTerm(), tokens)) {
                    siblings.remove(child);
                    parent.getMustNot().add(child);
                }
            } else {
                repairNegation(child, tokens);
            }
        }
    }

    private static boolean isNegatedInText(TermLeaf term, List<String> tokens) {
        int[] span = uniqueSpan(term, tokens);
        if (span == null) return false; // zero or ambiguous matches -> do nothing
        int start = span[0];
        int scanned = 0;
        for (int i = start - 1; i >= 0 && scanned < MAX_LOOKBACK; i--, scanned++) {
            String t = tokens.get(i);
            if (NEGATION_TRIGGERS.contains(t)) return true;
            if (CLAUSE_BOUNDARIES.contains(t)) return false; // scope closed, nothing found
        }
        return false;
    }

    // ------------------------------------------------------------------
    // OR / AND sibling-grouping repair
    // ------------------------------------------------------------------

    /**
     * Handles the converse structural mistake: two terms that the query
     * text explicitly joins with a bare "or"/"and" ended up siblings in the
     * WRONG bucket (an "or" pair stuck together in "must", or an "and" pair
     * stuck together in "should"). Recurses into every level of the tree
     * (including inside must_not, in case a nested sub-node there has its
     * own internal OR/AND mistake).
     */
    private static void repairConjunctionGrouping(SearchExpression node, List<String> tokens) {
        moveMisjoinedPairs(node.getMust(), node.getShould(), tokens, "or");
        moveMisjoinedPairs(node.getShould(), node.getMust(), tokens, "and");
        for (SearchExpression child : node.getMust()) repairConjunctionGrouping(child, tokens);
        for (SearchExpression child : node.getShould()) repairConjunctionGrouping(child, tokens);
        for (SearchExpression child : node.getMustNot()) repairConjunctionGrouping(child, tokens);
    }

    /**
     * Scans `wrongList` for leaf pairs whose text spans are separated by
     * exactly the single token `conjunction` and nothing else, and moves
     * both leaves of any such pair from `wrongList` into `correctList`.
     */
    private static void moveMisjoinedPairs(List<SearchExpression> wrongList,
                                           List<SearchExpression> correctList,
                                           List<String> tokens,
                                           String conjunction) {
        if (wrongList.size() < 2) return;
        List<SearchExpression> toMove = new ArrayList<>();
        for (SearchExpression a : wrongList) {
            if (!a.isLeaf()) continue;
            int[] spanA = uniqueSpan(a.getTerm(), tokens);
            if (spanA == null) continue;
            for (SearchExpression b : wrongList) {
                if (a == b || !b.isLeaf()) continue;
                int[] spanB = uniqueSpan(b.getTerm(), tokens);
                if (spanB == null) continue;
                // Exactly one token — the conjunction itself — between the
                // two spans, nothing else in between.
                if (spanB[0] == spanA[1] + 2 && tokens.get(spanA[1] + 1).equals(conjunction)) {
                    if (!toMove.contains(a)) toMove.add(a);
                    if (!toMove.contains(b)) toMove.add(b);
                }
            }
        }
        if (toMove.isEmpty()) return;
        wrongList.removeAll(toMove);
        correctList.addAll(toMove);
    }
}
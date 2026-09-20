package com.relive.project.util;

import com.relive.project.dto.SearchExpression;
import com.relive.project.dto.TermLeaf;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-parse cleanup of the query expression tree built by
 * DeterministicQueryParser.
 *
 * The only thing left here is mergeAdjacentVocab(): when spell-correction or
 * tokenizing has split one real keyword into two adjacent VOCAB terms (e.g.
 * "sun" + "set" for the stored keyword "sunset"), and the concatenation is a
 * genuinely known keyword, the two terms are merged back into one.
 *
 * (The old NOT/OR/AND "repair" passes that used to live here only existed to
 * fix mistakes in the LLM-produced tree of the removed Advanced Search.)
 *
 * SAFETY IS THE DESIGN PRINCIPLE: a term is only ever acted on if its value
 * can be located as an EXACT, UNIQUE (single-occurrence) span in the query
 * text. Zero matches or more than one possible match both mean "do nothing".
 */
public final class QueryTreeRepair {

    private QueryTreeRepair() {
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9]+|[,;.]");
    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9]+");

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
     * ever act when exactly one span is found --- zero or multiple both mean
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
    // Adjacent VOCAB merge
    // ------------------------------------------------------------------

    public static void mergeAdjacentVocab(SearchExpression root, String queryText, Set<String> knownKeywords) {
        if (root == null || queryText == null || queryText.isBlank() || knownKeywords.isEmpty()) return;
        List<String> tokens = tokenize(queryText);
        mergeInNode(root, tokens, knownKeywords);
    }

    private static void mergeInNode(SearchExpression node, List<String> tokens, Set<String> knownKeywords) {
        mergeInList(node.getMust(), tokens, knownKeywords);
        mergeInList(node.getShould(), tokens, knownKeywords);
        mergeInList(node.getMustNot(), tokens, knownKeywords);
        for (SearchExpression c : node.getMust()) mergeInNode(c, tokens, knownKeywords);
        for (SearchExpression c : node.getShould()) mergeInNode(c, tokens, knownKeywords);
        for (SearchExpression c : node.getMustNot()) mergeInNode(c, tokens, knownKeywords);
    }

    private static void mergeInList(List<SearchExpression> siblings, List<String> tokens, Set<String> knownKeywords) {
        boolean mergedAny = true;
        while (mergedAny) {
            mergedAny = false;
            outer:
            for (SearchExpression a : siblings) {
                if (!isPlainVocabLeaf(a)) continue;
                int[] spanA = uniqueSpan(a.getTerm(), tokens);
                if (spanA == null) continue;
                for (SearchExpression b : siblings) {
                    if (a == b || !isPlainVocabLeaf(b)) continue;
                    int[] spanB = uniqueSpan(b.getTerm(), tokens);
                    if (spanB == null || spanB[0] != spanA[1] + 1) continue; // must be text-adjacent
                    String concat = a.getTerm().getValue().replace(" ", "") + b.getTerm().getValue().replace(" ", "");
                    String resolved = resolveKnownKeyword(concat, knownKeywords);
                    if (resolved == null) continue;
                    a.getTerm().setValue(resolved);
                    siblings.remove(b);
                    mergedAny = true;
                    break outer;
                }
            }
        }
    }

    private static boolean isPlainVocabLeaf(SearchExpression n) {
        return n.isLeaf() && "VOCAB".equals(n.getTerm().getDomain());
    }

    private static String resolveKnownKeyword(String rawConcat, Set<String> knownKeywords) {
        for (String candidate : LemmatizerUtil.candidateForms(rawConcat)) {
            if (knownKeywords.contains(candidate)) return candidate;
        }
        return null;
    }
}
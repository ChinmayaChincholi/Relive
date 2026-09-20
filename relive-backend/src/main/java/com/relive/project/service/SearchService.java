package com.relive.project.service;

import com.relive.project.client.QueryParserClient;
import com.relive.project.dto.SearchExpression;
import com.relive.project.dto.TermLeaf;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaKeyword;
import com.relive.project.repository.LocationRepository;
import com.relive.project.repository.MediaKeywordRepository;
import com.relive.project.repository.MediaRepository;
import com.relive.project.util.DeterministicQueryParser;
import com.relive.project.util.JaroWinklerUtil;
import com.relive.project.util.LemmatizerUtil;
import com.relive.project.util.QueryTreeRepair;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class SearchService {
    private final MediaRepository mediaRepository;
    private final MediaKeywordRepository mediaKeywordRepository;
    private final LocationRepository locationRepository;
    private final FaceService faceService;
    private final QueryParserClient queryParserClient;
    private final SpellCorrectionService spellCorrectionService;
    private final AtomicLong searchGeneration = new AtomicLong(0);

    private static final double FUZZY_THRESHOLD = JaroWinklerUtil.DEFAULT_THRESHOLD;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MM-yyyy");

    public List<Media> searchByNaturalQuery(String rawQuery) {
        long myGeneration = searchGeneration.incrementAndGet();

        String corrected = spellCorrectionService.correctQuery(rawQuery);
        List<String> knownNames = faceService.getAllPersonNames();
        List<String> knownLocations = locationRepository.findDistinctLocationNames();

        // Change 4 --- a newer query has already started; abandon this one
        // before even paying for the AI-service round trip.
        if (myGeneration != searchGeneration.get()) {
            System.out.println("[SearchService] query=\"" + rawQuery + "\" superseded before parsing --- skipping");
            return Collections.emptyList();
        }

        SearchExpression tree;
        try {
            tree = queryParserClient.parseQuery(corrected);
        } catch (Exception e) {
            System.out.println("Query parsing failed: " + e.getMessage());
            return Collections.emptyList();
        }
        if (tree == null) return Collections.emptyList();

        // Change 4 --- a newer query started while this one was blocked on
        // the AI service. The parse result is now stale; don't spend the
        // rest of this method evaluating a query the user already moved on
        // from, and don't let it clobber the newer search's result.
        if (myGeneration != searchGeneration.get()) {
            System.out.println("[SearchService] query=\"" + rawQuery + "\" superseded after parsing --- discarding result");
            return Collections.emptyList();
        }

        logTree("before repair", tree);
        QueryTreeRepair.repair(tree, corrected);
        Set<String> knownKeywords = new HashSet<>(mediaKeywordRepository.findDistinctKeywords());
        QueryTreeRepair.mergeAdjacentVocab(tree, corrected, knownKeywords);
        logTree("after repair", tree);

        return evaluate(tree, knownNames, knownLocations, rawQuery);
    }

    /**
     * Instant Search --- same spell-corrected text, same known-entity
     * lists, same evaluator (evaluate() / classifyAndEvaluate() /
     * resolveAndLookupLeaf()) as Advanced Search. The only difference is
     * how the SearchExpression tree gets built: DeterministicQueryParser
     * runs entirely in this JVM, with no AI-service call and no LLM
     * involved at all, so this method never blocks on anything but the
     * database.
     */
    public List<Media> searchInstant(String rawQuery) {
        long myGeneration = searchGeneration.incrementAndGet();

        String corrected = spellCorrectionService.correctQuery(rawQuery);
        List<String> knownNames = faceService.getAllPersonNames();
        List<String> knownLocations = locationRepository.findDistinctLocationNames();

        if (myGeneration != searchGeneration.get()) {
            System.out.println("[SearchService][instant] query=\"" + rawQuery + "\" superseded --- skipping");
            return Collections.emptyList();
        }

        SearchExpression tree = DeterministicQueryParser.parse(corrected, knownNames, knownLocations);
        Set<String> knownKeywords = new HashSet<>(mediaKeywordRepository.findDistinctKeywords());
        QueryTreeRepair.mergeAdjacentVocab(tree, corrected, knownKeywords);
        logTree("instant tree", tree);

        return evaluate(tree, knownNames, knownLocations, rawQuery);
    }

    private List<Media> evaluate(SearchExpression tree, List<String> knownNames, List<String> knownLocations,
                                 String rawQuery) {
        Set<Long> universe = mediaRepository.findAll().stream()
                .filter(m -> "COMPLETED".equals(m.getStatus()))
                .map(Media::getId)
                .collect(Collectors.toSet());

        Set<Long> resultIds = classifyAndEvaluate(tree, knownNames, knownLocations, universe);
        System.out.println("[SearchService] query=\"" + rawQuery + "\" final result media ids: " + resultIds);
        if (resultIds.isEmpty()) return Collections.emptyList();
        return mediaRepository.findAllById(resultIds).stream()
                .filter(m -> "COMPLETED".equals(m.getStatus()))
                .collect(Collectors.toList());
    }

    private void logTree(String label, SearchExpression tree) {
        try {
            System.out.println("[SearchService] tree " + label + ": "
                    + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tree));
        } catch (Exception e) {
            System.out.println("[SearchService] tree " + label + ": <failed to serialize> " + e.getMessage());
        }
    }

    private Set<Long> classifyAndEvaluate(SearchExpression node, List<String> knownNames,
                                          List<String> knownLocations, Set<Long> universe) {
        Set<Long> result = null;

        if (node.getTerm() != null) {
            result = new HashSet<>(resolveAndLookupLeaf(node.getTerm(), knownNames, knownLocations));
        }

        boolean hasMust = !node.getMust().isEmpty();
        if (hasMust) {
            // Evaluate every direct child first, remembering which ones are
            // bare VOCAB leaves (checked AFTER resolution, so a name/place
            // that started out tagged VOCAB but got reclassified to
            // PERSON/LOCATION by resolveAndLookupLeaf is correctly treated
            // as an anchor here, not a soft term).
            List<Set<Long>> vocabResults = new ArrayList<>();
            List<Set<Long>> anchorResults = new ArrayList<>();
            Set<Long> strict = null;

            for (SearchExpression child : node.getMust()) {
                Set<Long> childResult = classifyAndEvaluate(child, knownNames, knownLocations, universe);
                if (strict == null) {
                    strict = new HashSet<>(childResult);
                } else {
                    strict.retainAll(childResult);
                }

                boolean isVocabLeaf = child.isLeaf() && "VOCAB".equals(child.getTerm().getDomain());
                if (isVocabLeaf) {
                    vocabResults.add(childResult);
                } else {
                    anchorResults.add(childResult);
                }
            }

            if (strict.isEmpty() && vocabResults.size() >= 2) {
                // Strict AND across this must-group came back empty, and
                // there are 2+ plain descriptive terms in it -- these are
                // inherently the least reliable part of the query (image
                // captioning/keyword generation is not exhaustive per
                // photo), so as a LAST-RESORT fallback (never triggered
                // when the strict result was already non-empty, so nothing
                // that currently works can regress) relax just those
                // descriptive terms to "any one of them is enough", while
                // any PERSON/LOCATION/DATE/TIME anchor in the same group
                // stays a hard, fully-required filter.
                Set<Long> vocabUnion = new HashSet<>();
                for (Set<Long> r : vocabResults) vocabUnion.addAll(r);

                Set<Long> fallback = new HashSet<>(vocabUnion);
                for (Set<Long> anchor : anchorResults) fallback.retainAll(anchor);

                System.out.println("[SearchService] strict AND across " + node.getMust().size()
                        + " term(s) was empty (" + vocabResults.size()
                        + " were plain descriptive terms) -- falling back to \"any one of them\" "
                        + "(any PERSON/LOCATION/DATE/TIME term stays required); fallback matches="
                        + fallback.size());

                result = fallback;
            } else {
                result = strict;
            }
        }

        boolean hasPositive = node.getTerm() != null || hasMust;
        if (result == null) result = new HashSet<>();

        if (!node.getShould().isEmpty()) {
            Set<Long> shouldUnion = new HashSet<>();
            for (SearchExpression child : node.getShould()) {
                shouldUnion.addAll(classifyAndEvaluate(child, knownNames, knownLocations, universe));
            }
            if (!hasPositive) {
                result = shouldUnion;
                hasPositive = true;
            } else {
                result.retainAll(shouldUnion);
            }
        }

        if (!node.getMustNot().isEmpty()) {
            if (!hasPositive) {
                // Pure negation --- nothing else in "must"/"should" to anchor
                // the search to. Without this, an empty base set minus
                // anything would still be empty, so a bare "without X"
                // query would always incorrectly return zero results
                // instead of "everything except X".
                result = new HashSet<>(universe);
            }
            Set<Long> mustNotUnion = new HashSet<>();
            for (SearchExpression child : node.getMustNot()) {
                mustNotUnion.addAll(classifyAndEvaluate(child, knownNames, knownLocations, universe));
            }
            result.removeAll(mustNotUnion);
        }

        return result;
    }

    private Set<Long> resolveAndLookupLeaf(TermLeaf term, List<String> knownNames, List<String> knownLocations) {
        if (term == null || term.getDomain() == null || term.getValue() == null) {
            return Collections.emptySet();
        }
        String domain = term.getDomain().toUpperCase();
        if (domain.equals("DATE")) {
            Set<Long> result = lookupDate(term.getValue(), term.getRangeEnd());
            System.out.println("[SearchService] term value=\"" + term.getValue() + "\" resolved domain=DATE matches=" + result.size());
            return result;
        }
        if (domain.equals("TIME")) {
            Set<Long> result = lookupTime(term.getValue(), term.getRangeEnd());
            System.out.println("[SearchService] term value=\"" + term.getValue() + "\" resolved domain=TIME matches=" + result.size());
            return result;
        }

        String value = term.getValue();

        String matchedName = JaroWinklerUtil.bestMatch(value, knownNames, FUZZY_THRESHOLD);
        if (matchedName != null) {
            term.setDomain("PERSON");
            Set<Long> result = new HashSet<>(faceService.getMediaIdsForPersonExact(matchedName));
            System.out.println("[SearchService] term value=\"" + value + "\" resolved domain=PERSON (matched \"" + matchedName + "\") matches=" + result.size());
            return result;
        }

        String matchedLocation = JaroWinklerUtil.bestMatch(value, knownLocations, FUZZY_THRESHOLD);
        if (matchedLocation != null) {
            term.setDomain("LOCATION");
            Set<Long> result = lookupLocationExact(matchedLocation);
            System.out.println("[SearchService] term value=\"" + value + "\" resolved domain=LOCATION (matched \"" + matchedLocation + "\") matches=" + result.size());
            return result;
        }

        term.setDomain("VOCAB");
        Set<Long> result = lookupVocab(value);
        System.out.println("[SearchService] term value=\"" + value + "\" resolved domain=VOCAB matches=" + result.size());
        return result;
    }

    private Set<Long> lookupLocationExact(String locationName) {
        return locationRepository.findByLocationName(locationName).stream()
                .map(l -> l.getMedia().getId())
                .collect(Collectors.toSet());
    }

    private Set<Long> lookupVocab(String rawValue) {
        for (String candidate : LemmatizerUtil.candidateForms(rawValue)) {
            List<MediaKeyword> exact = mediaKeywordRepository.findByKeyword(candidate);
            if (!exact.isEmpty()) {
                return exact.stream().map(k -> k.getMedia().getId()).collect(Collectors.toSet());
            }
        }
        return lookupKeywordFuzzy(LemmatizerUtil.normalize(rawValue));
    }

    private Set<Long> lookupKeywordFuzzy(String value) {
        List<MediaKeyword> exact = mediaKeywordRepository.findByKeyword(value);
        if (!exact.isEmpty()) {
            return exact.stream().map(k -> k.getMedia().getId()).collect(Collectors.toSet());
        }
        List<String> allKeywords = mediaKeywordRepository.findDistinctKeywords();
        String resolved = JaroWinklerUtil.bestMatch(value, allKeywords, FUZZY_THRESHOLD);
        if (resolved == null) return Collections.emptySet();
        return mediaKeywordRepository.findByKeyword(resolved).stream()
                .map(k -> k.getMedia().getId())
                .collect(Collectors.toSet());
    }

    private Set<Long> lookupDate(String value, String rangeEnd) {
        try {
            LocalDate startDate = LocalDate.parse(value, DATE_FORMAT);
            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end;
            if (rangeEnd == null) {
                end = startDate.atTime(LocalTime.MAX);
            } else if (rangeEnd.matches("\\d{2}-\\d{4}")) {
                YearMonth ym = YearMonth.parse(rangeEnd, MONTH_YEAR_FORMAT);
                end = ym.atEndOfMonth().atTime(LocalTime.MAX);
            } else if (rangeEnd.matches("\\d{4}")) {
                int year = Integer.parseInt(rangeEnd);
                end = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX);
            } else {
                LocalDate endDate = LocalDate.parse(rangeEnd, DATE_FORMAT);
                end = endDate.atTime(LocalTime.MAX);
            }
            return mediaRepository.findByDateTakenBetween(start, end).stream()
                    .map(Media::getId)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            System.out.println("Invalid date term: " + value + " / " + rangeEnd + " --- " + e.getMessage());
            return Collections.emptySet();
        }
    }

    private Set<Long> lookupTime(String value, String rangeEnd) {
        String start = value;
        String end = rangeEnd != null ? rangeEnd : value;
        return new HashSet<>(mediaRepository.findIdsByTimeOfDayBetween(start, end));
    }
}
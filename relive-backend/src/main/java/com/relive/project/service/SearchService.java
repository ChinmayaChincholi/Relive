package com.relive.project.service;

import com.relive.project.client.QueryParserClient;
import com.relive.project.dto.SearchExpression;
import com.relive.project.dto.TermLeaf;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaKeyword;
import com.relive.project.repository.LocationRepository;
import com.relive.project.repository.MediaKeywordRepository;
import com.relive.project.repository.MediaRepository;
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

@Service
@RequiredArgsConstructor
public class SearchService {
    private final MediaRepository mediaRepository;
    private final MediaKeywordRepository mediaKeywordRepository;
    private final LocationRepository locationRepository;
    private final FaceService faceService;
    private final QueryParserClient queryParserClient;
    private final SpellCorrectionService spellCorrectionService;

    private static final double FUZZY_THRESHOLD = JaroWinklerUtil.DEFAULT_THRESHOLD;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MM-yyyy");

    public List<Media> searchByNaturalQuery(String rawQuery) {
        String corrected = spellCorrectionService.correctQuery(rawQuery);
        List<String> knownNames = faceService.getAllPersonNames();
        List<String> knownLocations = locationRepository.findDistinctLocationNames();

        SearchExpression tree;
        try {
            tree = queryParserClient.parseQuery(corrected);
        } catch (Exception e) {
            System.out.println("Query parsing failed: " + e.getMessage());
            return Collections.emptyList();
        }
        if (tree == null) return Collections.emptyList();

        try {
            System.out.println("[SearchService] tree before repair: "
                    + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tree));
        } catch (Exception e) {
            System.out.println("[SearchService] tree before repair: <failed to serialize> " + e.getMessage());
        }
        QueryTreeRepair.repair(tree, corrected);
        try {
            System.out.println("[SearchService] tree after repair: "
                    + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tree));
        } catch (Exception e) {
            System.out.println("[SearchService] tree after repair: <failed to serialize> " + e.getMessage());
        }

        Set<Long> universe = mediaRepository.findAll().stream()
                .filter(m -> "COMPLETED".equals(m.getStatus()))
                .map(Media::getId)
                .collect(Collectors.toSet());

        Set<Long> resultIds = classifyAndEvaluate(tree, knownNames, knownLocations, universe);
        System.out.println("[SearchService] final result media ids: " + resultIds);
        if (resultIds.isEmpty()) return Collections.emptyList();
        return mediaRepository.findAllById(resultIds).stream()
                .filter(m -> "COMPLETED".equals(m.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Change 4 — merges what used to be two separate tree walks
     * (resolveDomains(), which fuzzy-matched a term just to relabel its
     * domain, then evaluate()->lookup(), which fuzzy-matched the SAME value
     * again to actually fetch results) into one. For PERSON/LOCATION/VOCAB
     * terms, a single fuzzy match against face_persons/locations now serves
     * as both the corrected domain label AND the result set, instead of
     * being computed twice. DATE/TIME are trusted completely and go
     * straight to their own deterministic parsers, exactly as before — this
     * priority-lookup logic never touches them.
     *
     * A node's own "term" is ANDed into the result alongside
     * must/should/must_not, instead of a term-bearing node short-circuiting
     * straight to a lookup and silently discarding any children.
     */
    private Set<Long> classifyAndEvaluate(SearchExpression node, List<String> knownNames,
                                          List<String> knownLocations, Set<Long> universe) {
        Set<Long> result = null;
        if (node.getTerm() != null) {
            result = new HashSet<>(resolveAndLookupLeaf(node.getTerm(), knownNames, knownLocations));
        }

        boolean hasMust = !node.getMust().isEmpty();
        for (SearchExpression child : node.getMust()) {
            Set<Long> childResult = classifyAndEvaluate(child, knownNames, knownLocations, universe);
            if (result == null) {
                result = new HashSet<>(childResult);
            } else {
                result.retainAll(childResult);
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
                // Pure negation — nothing else in "must"/"should" to anchor
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
            System.out.println("Invalid date term: " + value + " / " + rangeEnd + " — " + e.getMessage());
            return Collections.emptySet();
        }
    }

    private Set<Long> lookupTime(String value, String rangeEnd) {
        String start = value;
        String end = rangeEnd != null ? rangeEnd : value;
        return new HashSet<>(mediaRepository.findIdsByTimeOfDayBetween(start, end));
    }
}
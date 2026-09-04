package com.relive.project.service;

import com.relive.project.client.QueryParserClient;
import com.relive.project.dto.SearchExpression;
import com.relive.project.dto.TermLeaf;
import com.relive.project.entity.Domain;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaKeyword;
import com.relive.project.repository.MediaKeywordRepository;
import com.relive.project.repository.MediaRepository;
import com.relive.project.util.JaroWinklerUtil;
import com.relive.project.util.LemmatizerUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final MediaRepository mediaRepository;
    private final MediaKeywordRepository mediaKeywordRepository;
    private final FaceService faceService;
    private final QueryParserClient queryParserClient;
    private final SpellCorrectionService spellCorrectionService;

    private static final double FUZZY_THRESHOLD = JaroWinklerUtil.DEFAULT_THRESHOLD;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public List<Media> searchByNaturalQuery(String rawQuery) {
        String corrected = spellCorrectionService.correctQuery(rawQuery);

        SearchExpression tree;
        try {
            tree = queryParserClient.parseQuery(corrected);
        } catch (Exception e) {
            System.out.println("Query parsing failed: " + e.getMessage());
            return Collections.emptyList();
        }

        if (tree == null) return Collections.emptyList();

        Set<Long> resultIds = evaluate(tree);
        if (resultIds.isEmpty()) return Collections.emptyList();

        return mediaRepository.findAllById(resultIds).stream()
                .filter(m -> "COMPLETED".equals(m.getStatus()))
                .collect(Collectors.toList());
    }

    private Set<Long> evaluate(SearchExpression node) {
        if (node.isLeaf()) {
            return lookup(node.getTerm());
        }

        Set<Long> result = null;
        for (SearchExpression child : node.getMust()) {
            Set<Long> childResult = evaluate(child);
            if (result == null) {
                result = new HashSet<>(childResult);
            } else {
                result.retainAll(childResult);
            }
        }
        if (result == null) result = new HashSet<>();

        if (!node.getShould().isEmpty()) {
            Set<Long> shouldUnion = new HashSet<>();
            for (SearchExpression child : node.getShould()) {
                shouldUnion.addAll(evaluate(child));
            }
            if (node.getMust().isEmpty()) {
                result = shouldUnion;
            } else {
                result.retainAll(shouldUnion);
            }
        }

        if (!node.getMustNot().isEmpty()) {
            Set<Long> mustNotUnion = new HashSet<>();
            for (SearchExpression child : node.getMustNot()) {
                mustNotUnion.addAll(evaluate(child));
            }
            result.removeAll(mustNotUnion);
        }

        return result;
    }

    private Set<Long> lookup(TermLeaf term) {
        if (term == null || term.getDomain() == null || term.getValue() == null) {
            return Collections.emptySet();
        }

        switch (term.getDomain().toUpperCase()) {
            case "PERSON":
                return lookupPerson(term.getValue());
            case "LOCATION":
                return lookupKeyword(Domain.LOCATION, term.getValue().toLowerCase());
            case "VOCAB":
                return lookupKeyword(Domain.VOCAB, LemmatizerUtil.lemmatize(term.getValue()));
            case "DATE":
                return lookupDate(term.getValue(), term.getRangeEnd());
            case "TIME":
                return lookupTime(term.getValue(), term.getRangeEnd());
            default:
                return Collections.emptySet();
        }
    }

    private Set<Long> lookupPerson(String value) {
        List<String> allNames = faceService.getAllPersonNames();
        String resolved = JaroWinklerUtil.bestMatch(value, allNames, FUZZY_THRESHOLD);
        if (resolved == null) return Collections.emptySet();
        return new HashSet<>(faceService.getMediaIdsForPersonExact(resolved));
    }

    private Set<Long> lookupKeyword(Domain domain, String value) {
        List<MediaKeyword> exact = mediaKeywordRepository.findByDomainAndKeyword(domain, value);
        if (!exact.isEmpty()) {
            return exact.stream().map(k -> k.getMedia().getId()).collect(Collectors.toSet());
        }

        List<String> allKeywords = mediaKeywordRepository.findDistinctKeywordsByDomain(domain);
        String resolved = JaroWinklerUtil.bestMatch(value, allKeywords, FUZZY_THRESHOLD);
        if (resolved == null) return Collections.emptySet();

        return mediaKeywordRepository.findByDomainAndKeyword(domain, resolved).stream()
                .map(k -> k.getMedia().getId())
                .collect(Collectors.toSet());
    }

    private Set<Long> lookupDate(String value, String rangeEnd) {
        try {
            LocalDate startDate = LocalDate.parse(value, DATE_FORMAT);
            LocalDate endDate = rangeEnd != null ? LocalDate.parse(rangeEnd, DATE_FORMAT) : startDate;

            LocalDateTime start = startDate.atStartOfDay();
            LocalDateTime end = endDate.atTime(LocalTime.MAX);

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
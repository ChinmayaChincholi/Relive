package com.relive.project.service;

import com.relive.project.client.QueryParserClient;
import com.relive.project.client.SemanticSearchClient;
import com.relive.project.client.VerificationClient;
import com.relive.project.dto.ParsedQueryResponse;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaObject;
import com.relive.project.repository.MediaObjectRepository;
import com.relive.project.repository.MediaRepository;
import com.relive.project.util.JaroWinklerUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaSearchService {

    private final MediaRepository mediaRepository;
    private final MediaObjectRepository mediaObjectRepository;
    private final SemanticSearchClient semanticSearchClient;
    private final FaceService faceService;
    private final QueryParserClient queryParserClient;
    private final VerificationClient verificationClient;

    private static final double FUZZY_THRESHOLD = JaroWinklerUtil.DEFAULT_THRESHOLD;
    private static final int RRF_K = 60;
    private static final int REFINE_TOP_K = 30;

    public List<Media> searchByNaturalQuery(String query) {
        return searchByNaturalQuery(query, false);
    }

    public List<Media> searchByNaturalQuery(String query, boolean refine) {
        ParsedQueryResponse parsed = null;
        try {
            parsed = queryParserClient.parseQuery(query);
        } catch (Exception e) {
            System.out.println("Query parser failed: " + e.getMessage());
        }

        List<String> mustInclude = safe(parsed != null ? parsed.getMust_include() : null);
        List<String> mustExclude = safe(parsed != null ? parsed.getMust_exclude() : null);
        List<List<String>> anyOf = parsed != null && parsed.getAny_of() != null
                ? parsed.getAny_of() : Collections.emptyList();
        List<String> personCandidates = safe(parsed != null ? parsed.getPersons() : null);
        List<String> locationCandidates = safe(parsed != null ? parsed.getLocations() : null);
        Integer year = parsed != null ? parsed.getYear() : null;
        Integer month = parsed != null ? parsed.getMonth() : null;
        Integer minPeople = parsed != null ? parsed.getMin_people() : null;
        String timeOfDay = parsed != null ? parsed.getTime_of_day() : null;
        String freeTextSemantic = (parsed != null
                && parsed.getFree_text_semantic() != null
                && !parsed.getFree_text_semantic().isBlank())
                ? parsed.getFree_text_semantic()
                : query;

        System.out.println("=== SEARCH DEBUG ===");
        System.out.println("Query: " + query);
        System.out.println("must_include=" + mustInclude + " must_exclude=" + mustExclude + " any_of=" + anyOf);
        System.out.println("persons=" + personCandidates + " locations=" + locationCandidates);
        System.out.println("year=" + year + " month=" + month + " minPeople=" + minPeople + " timeOfDay=" + timeOfDay);

        List<Media> allMedia = mediaRepository.findByStatus("COMPLETED");

        List<String> allPersonNames = faceService.getAllPersonNames();
        Set<Long> personMediaIds = new HashSet<>();
        boolean hasPersonMatch = false;
        for (String candidate : personCandidates) {
            String resolved = JaroWinklerUtil.bestMatch(candidate, allPersonNames, FUZZY_THRESHOLD);
            if (resolved != null) {
                List<Long> ids = faceService.getMediaIdsForPersonExact(resolved);
                System.out.println("Person '" + candidate + "' fuzzy-resolved to '" + resolved + "' -> " + ids.size() + " photos");
                personMediaIds.addAll(ids);
                hasPersonMatch = true;
            }
        }

        List<String> allLocations = mediaRepository.findDistinctLocations();
        Set<String> matchedLocationStrings = new HashSet<>();
        for (String candidate : locationCandidates) {
            for (String loc : allLocations) {
                if (loc != null && JaroWinklerUtil.containsFuzzyToken(loc, candidate, FUZZY_THRESHOLD)) {
                    matchedLocationStrings.add(loc);
                }
            }
        }
        boolean hasLocationFilter = !matchedLocationStrings.isEmpty();

        List<String> allTagNames = mediaObjectRepository.findDistinctObjectNames();

        Map<String, Set<Long>> mustIncludeToMediaIds = resolveTagsToMediaIds(mustInclude, allTagNames);
        Map<String, Set<Long>> mustExcludeToMediaIds = resolveTagsToMediaIds(mustExclude, allTagNames);

        List<Map<String, Set<Long>>> anyOfResolved = new ArrayList<>();
        for (List<String> group : anyOf) {
            anyOfResolved.add(resolveTagsToMediaIds(group, allTagNames));
        }

        Set<Long> negatedMediaIds = mustExcludeToMediaIds.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
        for (String term : mustExclude) {
            for (Media m : allMedia) {
                if (m.getSceneCaption() != null
                        && m.getSceneCaption().toLowerCase().contains(term.toLowerCase())) {
                    negatedMediaIds.add(m.getId());
                }
            }
        }

        Map<Long, Double> clipScores = safeMap(() -> semanticSearchClient.clipSearch(freeTextSemantic));
        Map<Long, Double> textScores = safeMap(() -> semanticSearchClient.textSearch(freeTextSemantic));
        Map<Long, Integer> clipRanks = toRankMap(clipScores);
        Map<Long, Integer> textRanks = toRankMap(textScores);

        boolean hasAnyMustInclude = !mustIncludeToMediaIds.isEmpty();
        boolean hasHardFilters = year != null || month != null || hasLocationFilter
                || hasAnyMustInclude || hasPersonMatch || minPeople != null
                || !anyOfResolved.isEmpty() || timeOfDay != null;

        List<Media> candidates = new ArrayList<>();

        for (Media media : allMedia) {
            Long mediaId = media.getId();

            if (negatedMediaIds.contains(mediaId)) continue;

            if (year != null
                    && (media.getDateTaken() == null || media.getDateTaken().getYear() != year)) continue;

            if (month != null
                    && (media.getDateTaken() == null || media.getDateTaken().getMonthValue() != month)) continue;

            if (hasLocationFilter
                    && (media.getLocation() == null || !matchedLocationStrings.contains(media.getLocation()))) continue;

            if (hasAnyMustInclude) {
                boolean allMet = true;
                for (Set<Long> ids : mustIncludeToMediaIds.values()) {
                    if (!ids.contains(mediaId)) { allMet = false; break; }
                }
                if (!allMet) continue;
            }

            if (!anyOfResolved.isEmpty()) {
                boolean allGroupsSatisfied = true;
                for (Map<String, Set<Long>> group : anyOfResolved) {
                    boolean groupSatisfied = group.values().stream().anyMatch(ids -> ids.contains(mediaId));
                    if (!groupSatisfied) { allGroupsSatisfied = false; break; }
                }
                if (!allGroupsSatisfied) continue;
            }

            if (hasPersonMatch && !personMediaIds.contains(mediaId)) continue;

            if (minPeople != null
                    && (media.getFaceCount() == null || media.getFaceCount() < minPeople)) continue;

            if (timeOfDay != null && !timeOfDay.equalsIgnoreCase(media.getEventType())) continue;

            candidates.add(media);
        }

        Map<Long, Double> rrfScores = new HashMap<>();
        for (Media media : candidates) {
            Long id = media.getId();
            double score = 0.0;
            if (clipRanks.containsKey(id)) score += 1.0 / (RRF_K + clipRanks.get(id));
            if (textRanks.containsKey(id)) score += 1.0 / (RRF_K + textRanks.get(id));
            rrfScores.put(id, score);
        }

        List<Media> filtered = candidates;
        if (!hasHardFilters) {
            filtered = candidates.stream()
                    .filter(m -> rrfScores.getOrDefault(m.getId(), 0.0) > 0.0)
                    .collect(Collectors.toList());
        }

        filtered.sort((a, b) -> Double.compare(
                rrfScores.getOrDefault(b.getId(), 0.0),
                rrfScores.getOrDefault(a.getId(), 0.0)
        ));

        System.out.println("Final results before refine: " + filtered.size() + " photos");
        System.out.println("====================");

        if (refine && !filtered.isEmpty()) {
            filtered = applyVerificationRefine(query, filtered);
        }

        return filtered;
    }

    private List<Media> applyVerificationRefine(String query, List<Media> ranked) {
        int topK = Math.min(REFINE_TOP_K, ranked.size());
        List<Media> topCandidates = ranked.subList(0, topK);

        Map<Long, String> descriptions = new HashMap<>();
        for (Media m : topCandidates) {
            if (m.getSceneCaption() != null) descriptions.put(m.getId(), m.getSceneCaption());
        }

        try {
            Set<Long> verifiedIds = verificationClient.verifyCandidates(query, descriptions);

            List<Media> refined = new ArrayList<>();
            for (Media m : topCandidates) {
                if (verifiedIds.contains(m.getId())) refined.add(m);
            }

            for (int i = topK; i < ranked.size(); i++) refined.add(ranked.get(i));
            return refined;
        } catch (Exception e) {
            System.out.println("Verification refine failed, returning unrefined results: " + e.getMessage());
            return ranked;
        }
    }

    private Map<String, Set<Long>> resolveTagsToMediaIds(List<String> terms, List<String> allTagNames) {
        Map<String, Set<Long>> result = new LinkedHashMap<>();
        for (String term : terms) {
            if (term == null || term.length() < 2) continue;
            String resolvedTag = JaroWinklerUtil.bestMatch(term, allTagNames, FUZZY_THRESHOLD);
            String lookupTerm = resolvedTag != null ? resolvedTag : term;
            List<MediaObject> matches = mediaObjectRepository.findByObjectNameContainingIgnoreCase(lookupTerm);
            if (!matches.isEmpty()) {
                Set<Long> ids = new HashSet<>();
                for (MediaObject obj : matches) ids.add(obj.getMedia().getId());
                result.put(term, ids);
            }
        }
        return result;
    }

    private Map<Long, Integer> toRankMap(Map<Long, Double> scores) {
        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        Map<Long, Integer> ranks = new HashMap<>();
        int rank = 1;
        for (Map.Entry<Long, Double> entry : sorted) {
            ranks.put(entry.getKey(), rank++);
        }
        return ranks;
    }

    private Map<Long, Double> safeMap(Supplier<Map<Long, Double>> supplier) {
        try {
            Map<Long, Double> result = supplier.get();
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            System.out.println("Semantic search call failed: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<String> safe(List<String> list) {
        return list != null ? list : Collections.emptyList();
    }
}
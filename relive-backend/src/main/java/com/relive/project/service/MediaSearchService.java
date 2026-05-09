package com.relive.project.service;

import com.relive.project.client.QueryParserClient;
import com.relive.project.client.SemanticSearchClient;
import com.relive.project.dto.ParsedQueryResponse;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaObject;
import com.relive.project.repository.MediaObjectRepository;
import com.relive.project.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MediaSearchService {

    private final MediaRepository mediaRepository;
    private final MediaObjectRepository mediaObjectRepository;
    private final SemanticSearchClient semanticSearchClient;
    private final FaceService faceService;
    private final QueryParserClient queryParserClient;

    public List<Media> searchByNaturalQuery(String query) {

        // ── Parse query using spaCy ───────────────────────────────────
        ParsedQueryResponse parsed = null;
        try {
            parsed = queryParserClient.parseQuery(query);
        } catch (Exception e) {
            System.out.println("Query parser failed: " + e.getMessage());
        }

        List<String> nouns         = parsed != null && parsed.getObjects()       != null ? parsed.getObjects()       : Collections.emptyList();
        List<String> verbs         = parsed != null && parsed.getVerbs()         != null ? parsed.getVerbs()         : Collections.emptyList();
        List<String> allTerms      = parsed != null && parsed.getAll_terms()     != null ? parsed.getAll_terms()     : Collections.emptyList();
        List<String> locationHints = parsed != null && parsed.getLocation_hints()!= null ? parsed.getLocation_hints(): Collections.emptyList();
        List<String> personHints   = parsed != null && parsed.getPerson_hints()  != null ? parsed.getPerson_hints()  : Collections.emptyList();
        Integer year       = parsed != null ? parsed.getYear()        : null;
        Integer month      = parsed != null ? parsed.getMonth()       : null;
        Integer minFaces   = parsed != null ? parsed.getMin_faces()   : null;
        String  timeOfDay  = parsed != null ? parsed.getTime_of_day() : null;

        boolean requiresYear = year != null;

        // ── Get all completed media ───────────────────────────────────
        List<Media> allMedia = mediaRepository.findByStatus("COMPLETED");
        Map<Long, Media> mediaMap = new HashMap<>();
        for (Media m : allMedia) {
            mediaMap.put(m.getId(), m);
        }

        // ── Build noun → mediaId index ────────────────────────────────
        // For each noun in the query, find which media IDs contain it
        // as a detected object. Used for AND logic (all nouns must be present).
        Map<String, Set<Long>> nounToMediaIds = new HashMap<>();
        boolean hasAnyObjectMatch = false;

        for (String noun : nouns) {
            if (noun.length() < 2) continue;
            List<MediaObject> matches = mediaObjectRepository
                    .findByObjectNameContainingIgnoreCase(noun);
            if (!matches.isEmpty()) {
                hasAnyObjectMatch = true;
                Set<Long> matchingIds = new HashSet<>();
                for (MediaObject obj : matches) {
                    matchingIds.add(obj.getMedia().getId());
                }
                nounToMediaIds.put(noun, matchingIds);
            }
        }

        // ── Detect location requirement dynamically ───────────────────
        String requiredLocation = null;
        List<String> allLocations = mediaRepository.findDistinctLocations();

        outer:
        for (String hint : locationHints) {
            for (String loc : allLocations) {
                if (loc != null && loc.toLowerCase().contains(hint)) {
                    requiredLocation = hint;
                    break outer;
                }
            }
        }
        if (requiredLocation == null) {
            outer:
            for (String term : allTerms) {
                if (term.length() < 3) continue;
                for (String loc : allLocations) {
                    if (loc != null && loc.toLowerCase().contains(term)) {
                        requiredLocation = term;
                        break outer;
                    }
                }
            }
        }

        // ── CLIP semantic scores — influence ranking, never exclude ───
        Map<Long, Double> clipScores = new HashMap<>();
        Map<Long, Double> semanticResults = semanticSearchClient.semanticSearch(query);
        if (semanticResults != null) {
            clipScores.putAll(semanticResults);
        }

        // ── Hard filters — strict exclusion ──────────────────────────
        Set<Long> hardExclude = new HashSet<>();

        for (Media media : allMedia) {
            Long mediaId = media.getId();

            // Hard year filter
            if (requiresYear) {
                java.time.LocalDateTime dateRef =
                        media.getDateTaken() != null ? media.getDateTaken() : media.getUploadedAt();
                if (dateRef == null || dateRef.getYear() != year) {
                    hardExclude.add(mediaId);
                    continue;
                }
            }

            // Hard location filter
            if (requiredLocation != null) {
                if (media.getLocation() == null || media.getLocation().isEmpty()) {
                    hardExclude.add(mediaId);
                    continue;
                }
                String locationLower = media.getLocation().toLowerCase();
                boolean locationMatches = locationLower.contains(requiredLocation);
                if (!locationMatches) {
                    for (String hint : locationHints) {
                        if (locationLower.contains(hint)) {
                            locationMatches = true;
                            break;
                        }
                    }
                }
                if (!locationMatches) {
                    hardExclude.add(mediaId);
                    continue;
                }
            }

            // Hard AND object filter — every matched noun must be present
            if (hasAnyObjectMatch) {
                boolean allNounsMet = true;
                for (Map.Entry<String, Set<Long>> entry : nounToMediaIds.entrySet()) {
                    if (!entry.getValue().contains(mediaId)) {
                        allNounsMet = false;
                        break;
                    }
                }
                if (!allNounsMet) {
                    hardExclude.add(mediaId);
                    continue;
                }
            }

            // Hard face count filter
            if (minFaces != null && media.getFaceCount() != null) {
                if (media.getFaceCount() < minFaces) {
                    hardExclude.add(mediaId);
                }
            }
        }

        // ── Build candidate pool with base CLIP scores ────────────────
        Map<Long, Double> scoreMap = new HashMap<>();
        for (Media media : allMedia) {
            Long mediaId = media.getId();
            if (hardExclude.contains(mediaId)) continue;
            scoreMap.put(mediaId, clipScores.getOrDefault(mediaId, 0.0));
        }

        // ── Score boosts ──────────────────────────────────────────────
        for (Long mediaId : scoreMap.keySet()) {
            Media media = mediaMap.get(mediaId);
            if (media == null) continue;

            double score = scoreMap.get(mediaId);

            // Object match boost (+0.6 per matching noun)
            for (Map.Entry<String, Set<Long>> entry : nounToMediaIds.entrySet()) {
                if (entry.getValue().contains(mediaId)) score += 0.6;
            }

            // Caption boost — all terms and verbs
            if (media.getSceneCaption() != null) {
                String captionLower = media.getSceneCaption().toLowerCase();
                for (String term : allTerms) {
                    if (term.length() > 2 && captionLower.contains(term)) score += 0.4;
                }
                for (String verb : verbs) {
                    if (captionLower.contains(verb)) score += 0.3;
                }
            }

            // Temporal boosts
            if (requiresYear) score += 0.8;
            if (month != null) {
                java.time.LocalDateTime dateRef =
                        media.getDateTaken() != null ? media.getDateTaken() : media.getUploadedAt();
                if (dateRef != null && dateRef.getMonthValue() == month) score += 0.5;
            }

            // Location boost
            if (requiredLocation != null) score += 1.0;

            // Time of day boost
            if (timeOfDay != null && timeOfDay.equalsIgnoreCase(media.getEventType())) {
                score += 0.3;
            }

            // People / face boost
            boolean wantsPeople = nouns.stream().anyMatch(n ->
                    n.equals("people") || n.equals("person") || n.equals("man")
                            || n.equals("woman") || n.equals("boy") || n.equals("girl")
                            || n.equals("family") || n.equals("friend") || n.equals("couple")
                            || n.equals("group") || n.equals("crowd") || n.equals("team")
                            || n.equals("child") || n.equals("kid") || n.equals("baby")
                            || n.equals("men") || n.equals("women")
            );
            if (wantsPeople && media.getFaceCount() != null && media.getFaceCount() > 0) {
                score += Math.min(media.getFaceCount() * 0.15, 0.6);
            }

            // Named person boost — highest-priority signal
            List<String> personCandidates = new ArrayList<>(personHints);
            for (String noun : nouns) {
                if (!personCandidates.contains(noun)) personCandidates.add(noun);
            }
            for (String candidate : personCandidates) {
                if (candidate.length() < 2) continue;
                List<Long> personMediaIds = faceService.getMediaIdsForPersonName(candidate);
                if (personMediaIds.contains(mediaId)) score += 2.0;
            }

            scoreMap.put(mediaId, score);
        }

        // ── Minimum score threshold ───────────────────────────────────
        boolean hasHardFilters = requiresYear || requiredLocation != null || hasAnyObjectMatch;
        double minThreshold;
        if (hasHardFilters) {
            minThreshold = 0.1;
        } else {
            long meaningfulTerms = allTerms.stream().filter(t -> t.length() > 3).count();
            if (meaningfulTerms >= 3)      minThreshold = 0.8;
            else if (meaningfulTerms == 2) minThreshold = 0.6;
            else                           minThreshold = 0.4;
        }

        // ── Sort and return ───────────────────────────────────────────
        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(scoreMap.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        List<Media> results = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : sorted) {
            if (entry.getValue() < minThreshold) continue;
            mediaRepository.findById(entry.getKey())
                    .filter(m -> "COMPLETED".equals(m.getStatus()))
                    .ifPresent(results::add);
        }

        return results;
    }
}
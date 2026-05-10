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

        // ── Parse query ───────────────────────────────────────────────
        ParsedQueryResponse parsed = null;
        try {
            parsed = queryParserClient.parseQuery(query);
        } catch (Exception e) {
            System.out.println("Query parser failed: " + e.getMessage());
        }

        List<String> nouns         = safe(parsed != null ? parsed.getObjects()        : null);
        List<String> verbs         = safe(parsed != null ? parsed.getVerbs()          : null);
        List<String> allTerms      = safe(parsed != null ? parsed.getAll_terms()      : null);
        List<String> locationHints = safe(parsed != null ? parsed.getLocation_hints() : null);
        List<String> negatedTerms  = safe(parsed != null ? parsed.getNegated_terms()  : null);
        List<String> queryWords    = safe(parsed != null ? parsed.getQuery_words()    : null);
        Integer year      = parsed != null ? parsed.getYear()        : null;
        Integer month     = parsed != null ? parsed.getMonth()       : null;
        Integer minFaces  = parsed != null ? parsed.getMin_faces()   : null;
        String  timeOfDay = parsed != null ? parsed.getTime_of_day() : null;

        System.out.println("=== SEARCH DEBUG ===");
        System.out.println("Query: " + query);
        System.out.println("Query words: " + queryWords);
        System.out.println("Nouns: " + nouns);
        System.out.println("Location hints (spaCy): " + locationHints);

        // ── Get all completed media ───────────────────────────────────
        List<Media> allMedia = mediaRepository.findByStatus("COMPLETED");
        Map<Long, Media> mediaMap = new HashMap<>();
        for (Media m : allMedia) mediaMap.put(m.getId(), m);

        // ── Person name resolution ────────────────────────────────────
        // Check every query word directly against the face DB.
        // Case-insensitive partial match — "nitin" matches "Nitin", "chimu" matches "Chimu".
        Set<Long> personNameMediaIds = new HashSet<>();
        Set<String> wordsMatchedAsPersons = new HashSet<>();
        boolean hasPersonNameMatch = false;

        for (String word : queryWords) {
            if (word.length() < 2) continue;
            List<Long> ids = faceService.getMediaIdsForPersonName(word);
            System.out.println("Face lookup '" + word + "' → " + ids.size() + " photos");
            if (!ids.isEmpty()) {
                personNameMediaIds.addAll(ids);
                wordsMatchedAsPersons.add(word);
                hasPersonNameMatch = true;
            }
        }

        // ── Location resolution ───────────────────────────────────────
        // Strategy:
        // 1. Check spaCy location hints first (most reliable)
        // 2. Then check query_words against ACTUAL stored location strings
        //    (so "ooty" matches "Ooty, Tamil Nadu, IN (11.4102,76.6950)")
        // 3. A word matched as a person name is NOT used for location matching
        //    (disambiguation: if "ooty" is both a face name and a location, person wins)
        List<String> allStoredLocations = mediaRepository.findDistinctLocations();

        String requiredLocation = null;

        // Step 1: spaCy NER location hints
        for (String hint : locationHints) {
            for (String loc : allStoredLocations) {
                if (loc != null && loc.toLowerCase().contains(hint.toLowerCase())) {
                    requiredLocation = hint;
                    System.out.println("Location matched via spaCy NER: '" + hint + "'");
                    break;
                }
            }
            if (requiredLocation != null) break;
        }

        // Step 2: Check query_words against stored location strings
        // Skip words that were already matched as person names
        if (requiredLocation == null) {
            for (String word : queryWords) {
                if (word.length() < 2) continue;
                if (wordsMatchedAsPersons.contains(word)) continue; // person takes priority

                for (String loc : allStoredLocations) {
                    if (loc != null && loc.toLowerCase().contains(word.toLowerCase())) {
                        requiredLocation = word;
                        System.out.println("Location matched via query_words: '" + word + "'");
                        break;
                    }
                }
                if (requiredLocation != null) break;
            }
        }

        System.out.println("Required location: " + requiredLocation);
        System.out.println("Person match: " + hasPersonNameMatch + " (" + personNameMediaIds.size() + " photos)");
        System.out.println("====================");

        // ── Object filter (common nouns only) ────────────────────────
        Map<String, Set<Long>> nounToMediaIds = new HashMap<>();
        boolean hasAnyObjectMatch = false;

        for (String noun : nouns) {
            if (noun.length() < 2) continue;
            List<MediaObject> matches = mediaObjectRepository
                    .findByObjectNameContainingIgnoreCase(noun);
            if (!matches.isEmpty()) {
                hasAnyObjectMatch = true;
                Set<Long> matchingIds = new HashSet<>();
                for (MediaObject obj : matches) matchingIds.add(obj.getMedia().getId());
                nounToMediaIds.put(noun, matchingIds);
            }
        }

        // ── Negation exclusion ────────────────────────────────────────
        Set<Long> negatedMediaIds = new HashSet<>();
        for (String negTerm : negatedTerms) {
            if (negTerm.length() < 2) continue;
            for (MediaObject obj : mediaObjectRepository.findByObjectNameContainingIgnoreCase(negTerm)) {
                negatedMediaIds.add(obj.getMedia().getId());
            }
            for (Media m : allMedia) {
                if (m.getSceneCaption() != null &&
                        m.getSceneCaption().toLowerCase().contains(negTerm)) {
                    negatedMediaIds.add(m.getId());
                }
            }
        }

        // ── CLIP semantic scores ──────────────────────────────────────
        Map<Long, Double> clipScores = new HashMap<>();
        try {
            Map<Long, Double> semanticResults = semanticSearchClient.semanticSearch(query);
            if (semanticResults != null) clipScores.putAll(semanticResults);
        } catch (Exception e) {
            System.out.println("Semantic search failed: " + e.getMessage());
        }

        // ── Hard filters ──────────────────────────────────────────────
        Set<Long> hardExclude = new HashSet<>();

        for (Media media : allMedia) {
            Long mediaId = media.getId();

            // 1. Negation
            if (negatedMediaIds.contains(mediaId)) {
                hardExclude.add(mediaId);
                continue;
            }

            // 2. Year (dateTaken only)
            if (year != null) {
                if (media.getDateTaken() == null || media.getDateTaken().getYear() != year) {
                    hardExclude.add(mediaId);
                    continue;
                }
            }

            // 3. Location — check against stored location string
            if (requiredLocation != null) {
                if (media.getLocation() == null || media.getLocation().isEmpty()) {
                    hardExclude.add(mediaId);
                    continue;
                }
                if (!media.getLocation().toLowerCase().contains(requiredLocation.toLowerCase())) {
                    hardExclude.add(mediaId);
                    continue;
                }
            }

            // 4. Object AND filter — skip when person-name query
            if (hasAnyObjectMatch && !hasPersonNameMatch) {
                boolean allNounsMet = true;
                for (Map.Entry<String, Set<Long>> entry : nounToMediaIds.entrySet()) {
                    if (!entry.getValue().contains(mediaId)) {
                        allNounsMet = false;
                        break;
                    }
                }
                if (!allNounsMet) { hardExclude.add(mediaId); continue; }
            }

            // 5. Person name hard filter
            if (hasPersonNameMatch && !personNameMediaIds.contains(mediaId)) {
                hardExclude.add(mediaId);
                continue;
            }

            // 6. Face count
            if (minFaces != null && media.getFaceCount() != null
                    && media.getFaceCount() < minFaces) {
                hardExclude.add(mediaId);
            }
        }

        // ── Score candidates ──────────────────────────────────────────
        Map<Long, Double> scoreMap = new HashMap<>();
        for (Media media : allMedia) {
            Long mediaId = media.getId();
            if (hardExclude.contains(mediaId)) continue;
            scoreMap.put(mediaId, clipScores.getOrDefault(mediaId, 0.0));
        }

        for (Long mediaId : new HashSet<>(scoreMap.keySet())) {
            Media media = mediaMap.get(mediaId);
            if (media == null) continue;
            double score = scoreMap.get(mediaId);

            // Person name — highest priority
            if (hasPersonNameMatch && personNameMediaIds.contains(mediaId)) score += 3.0;

            // Location match boost
            if (requiredLocation != null) score += 1.5;

            // Object match boosts
            for (Map.Entry<String, Set<Long>> entry : nounToMediaIds.entrySet()) {
                if (entry.getValue().contains(mediaId)) score += 0.6;
            }

            // Caption boosts
            if (media.getSceneCaption() != null) {
                String cap = media.getSceneCaption().toLowerCase();
                for (String term : allTerms) {
                    if (term.length() > 2 && cap.contains(term)) score += 0.4;
                }
                for (String verb : verbs) {
                    if (cap.contains(verb)) score += 0.3;
                }
            }

            // Temporal
            if (year != null) score += 0.8;
            if (month != null && media.getDateTaken() != null
                    && media.getDateTaken().getMonthValue() == month) score += 0.5;

            // Time of day
            if (timeOfDay != null && timeOfDay.equalsIgnoreCase(media.getEventType())) score += 0.3;

            // Face/people boost
            boolean wantsPeople = nouns.stream().anyMatch(n ->
                    n.equals("people") || n.equals("person") || n.equals("man")
                            || n.equals("woman") || n.equals("family") || n.equals("friend")
                            || n.equals("couple") || n.equals("group") || n.equals("crowd")
                            || n.equals("child") || n.equals("kid") || n.equals("baby")
                            || n.equals("men") || n.equals("women") || n.equals("boy") || n.equals("girl")
            );
            if (wantsPeople && media.getFaceCount() != null && media.getFaceCount() > 0) {
                score += Math.min(media.getFaceCount() * 0.15, 0.6);
            }

            scoreMap.put(mediaId, score);
        }

        // ── Threshold ─────────────────────────────────────────────────
        boolean hasHardFilters = year != null || requiredLocation != null
                || hasAnyObjectMatch || hasPersonNameMatch;
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

        System.out.println("Final results: " + results.size() + " photos");
        return results;
    }

    private List<String> safe(List<String> list) {
        return list != null ? list : Collections.emptyList();
    }
}
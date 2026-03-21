package com.relive.project.service;

import com.relive.project.client.SemanticSearchClient;
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

    public List<Media> searchByNaturalQuery(String query, String email) {

        // mediaId -> score
        Map<Long, Double> scoreMap = new HashMap<>();

        // -------------------------------
        // 1. Semantic Search (CLIP)
        // -------------------------------
        Map<Long, Double> semanticResults =
                semanticSearchClient.semanticSearch(query);

        if (semanticResults != null) {
            for (Map.Entry<Long, Double> entry : semanticResults.entrySet()) {
                scoreMap.put(entry.getKey(), entry.getValue()); // base score
            }
        }

        // -------------------------------
        // 2. Keyword Extraction
        // -------------------------------
        String cleaned = query.toLowerCase()
                .replaceAll("[^a-z ]", "");

        String[] words = cleaned.split("\\s+");

        Set<String> stopwords = Set.of(
                "a","an","the","in","on","at","of","with","and","is",
                "are","to","for","his","her","their","photos","photo"
        );

        // -------------------------------
        // 3. Object-based Boost
        // -------------------------------
        for (String word : words) {

            if (word.length() < 3 || stopwords.contains(word)) {
                continue;
            }

            List<MediaObject> matches =
                    mediaObjectRepository
                            .findByObjectNameContainingIgnoreCaseAndMedia_User_Email(
                                    word,
                                    email
                            );

            for (MediaObject obj : matches) {

                Long mediaId = obj.getMedia().getId();

                // boost score if already present
                scoreMap.put(
                        mediaId,
                        scoreMap.getOrDefault(mediaId, 0.0) + 0.5
                );
            }
        }

        // -------------------------------
        // 4. Sort by score
        // -------------------------------
        List<Map.Entry<Long, Double>> sorted =
                new ArrayList<>(scoreMap.entrySet());

        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        // -------------------------------
        // 5. Fetch Media
        // -------------------------------
        List<Media> results = new ArrayList<>();

        for (Map.Entry<Long, Double> entry : sorted) {

            Long id = entry.getKey();

            mediaRepository.findById(id)
                    .filter(m ->
                            m.getUser().getEmail().equals(email)
                                    && "COMPLETED".equals(m.getStatus())
                    )
                    .ifPresent(results::add);
        }

        return results;
    }
}
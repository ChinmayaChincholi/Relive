package com.relive.project.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@RequiredArgsConstructor
public class SemanticSearchClient {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    public Map<Long, Double> semanticSearch(String query) {

        String url = aiServiceUrl + "/semantic_search";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("query", query);

        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<Map> response =
                restTemplate.postForEntity(
                        url,
                        entity,
                        Map.class
                );

        List<?> rawResults = (List<?>) response.getBody().get("results");

        Map<Long, Double> results = new LinkedHashMap<>();

        if (rawResults != null) {

            for (Object obj : rawResults) {

                Map<?, ?> item = (Map<?, ?>) obj;

                Number mediaId = (Number) item.get("media_id");
                Number score = (Number) item.get("score");

                results.put(mediaId.longValue(), score.doubleValue());
            }
        }

        return results;
    }
}
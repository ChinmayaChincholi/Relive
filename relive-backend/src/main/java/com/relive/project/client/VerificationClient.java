package com.relive.project.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@RequiredArgsConstructor
public class VerificationClient {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    public Set<Long> verifyCandidates(String query, Map<Long, String> candidates) {

        String url = aiServiceUrl + "/verify_candidates";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        List<Map<String, Object>> candidateList = new ArrayList<>();
        for (Map.Entry<Long, String> entry : candidates.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("media_id", entry.getKey());
            item.put("rich_description", entry.getValue());
            candidateList.add(item);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("query", query);
        body.put("candidates", candidateList);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        Set<Long> verified = new HashSet<>();
        Map responseBody = response.getBody();
        if (responseBody == null) return verified;

        List<?> rawResults = (List<?>) responseBody.get("results");
        if (rawResults == null) return verified;

        for (Object obj : rawResults) {
            Map<?, ?> item = (Map<?, ?>) obj;
            Number mediaId = (Number) item.get("media_id");
            Boolean isVerified = (Boolean) item.get("verified");
            if (Boolean.TRUE.equals(isVerified)) {
                verified.add(mediaId.longValue());
            }
        }

        return verified;
    }
}
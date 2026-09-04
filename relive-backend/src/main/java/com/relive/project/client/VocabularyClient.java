package com.relive.project.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@RequiredArgsConstructor
public class VocabularyClient {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    public AnalyzeResult analyzeImage(String imagePath, Long mediaId) {
        String url = aiServiceUrl + "/analyze";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("image_path", imagePath);
        body.put("media_id", mediaId);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        Map<String, Object> responseBody = response.getBody();

        AnalyzeResult result = new AnalyzeResult();
        if (responseBody == null) return result;

        Object words = responseBody.get("vocabulary_words");
        if (words instanceof List) {
            for (Object w : (List<?>) words) result.vocabularyWords.add(String.valueOf(w));
        }
        result.dateTaken = (String) responseBody.get("date_taken");

        Object locationObj = responseBody.get("location");
        if (locationObj instanceof Map) {
            Map<?, ?> loc = (Map<?, ?>) locationObj;
            result.locationCity = (String) loc.get("city");
            result.locationRegion = (String) loc.get("region");
            result.locationCountry = (String) loc.get("country");
            result.locationDisplay = (String) loc.get("display");
        }

        return result;
    }

    public static class AnalyzeResult {
        public List<String> vocabularyWords = new ArrayList<>();
        public String dateTaken;
        public String locationCity;
        public String locationRegion;
        public String locationCountry;
        public String locationDisplay;
    }
}
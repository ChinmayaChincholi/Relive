package com.relive.project.client;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
@RequiredArgsConstructor
public class FaceClient {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    public Map<String, Object> extractFaces(String imagePath, Long mediaId) {

        String url = aiServiceUrl + "/extract_faces";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("image_path", imagePath);
        body.put("media_id", mediaId);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        return response.getBody();
    }

    public List<Integer> clusterFaces(List<List<Double>> embeddings) {

        String url = aiServiceUrl + "/cluster_faces";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();
        body.put("embeddings", embeddings);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

        Map responseBody = response.getBody();
        if (responseBody == null) {
            System.out.println("cluster_faces returned null body");
            return Collections.emptyList();
        }

        Object raw = responseBody.get("labels");
        if (raw == null) {
            System.out.println("cluster_faces response missing 'labels' key");
            return Collections.emptyList();
        }

        // Jackson deserializes JSON integers as Integer when using raw Map,
        // but the list elements come back as Object — cast each one individually.
        List<?> rawList = (List<?>) raw;
        List<Integer> labels = new ArrayList<>(rawList.size());
        for (Object item : rawList) {
            if (item instanceof Number) {
                labels.add(((Number) item).intValue());
            }
        }

        return labels;
    }
}
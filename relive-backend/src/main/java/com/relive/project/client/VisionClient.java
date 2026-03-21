package com.relive.project.client;

import com.relive.project.dto.VisionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class VisionClient {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    public VisionResponse analyzeImage(String imagePath, Long mediaId) {

        String url = aiServiceUrl + "/analyze";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new HashMap<>();

        body.put("image_path", imagePath);   // MUST match AI service
        body.put("media_id", mediaId);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<VisionResponse> response =
                restTemplate.postForEntity(
                        url,
                        entity,
                        VisionResponse.class
                );

        return response.getBody();
    }
}
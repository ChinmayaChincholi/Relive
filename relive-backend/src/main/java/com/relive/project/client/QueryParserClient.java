package com.relive.project.client;

import com.relive.project.dto.ParsedQueryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class QueryParserClient {

    private final RestTemplate restTemplate;

    @Value("${ai.service.url}")
    private String aiServiceUrl;

    public ParsedQueryResponse parseQuery(String query) {

        String url = aiServiceUrl + "/parse_query";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("query", query);

        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<ParsedQueryResponse> response =
                restTemplate.postForEntity(
                        url,
                        entity,
                        ParsedQueryResponse.class
                );

        return response.getBody();
    }
}
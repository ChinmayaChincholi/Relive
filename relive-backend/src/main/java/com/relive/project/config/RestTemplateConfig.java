package com.relive.project.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Snake_case mapper scoped ONLY to this RestTemplate (used for calls
        // to the Python AI service, which sends must_not/range_end-style
        // JSON) — NOT the app-wide default. An earlier version of this made
        // the snake_case mapper @Primary globally, which broke every
        // response the frontend receives (MediaResponseDTO, FacePersonDTO,
        // etc. all got serialized as snake_case, and the React code
        // expecting camelCase silently got undefined fields).
        ObjectMapper snakeCaseMapper = new ObjectMapper();
        snakeCaseMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(0,
                new MappingJackson2HttpMessageConverter(snakeCaseMapper));

        return restTemplate;
    }
}
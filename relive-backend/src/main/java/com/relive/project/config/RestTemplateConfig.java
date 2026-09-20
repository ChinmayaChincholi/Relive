package com.relive.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    // Used for calls to the Python AI service (/analyze, /extract_faces,
    // /cluster_faces). All of those exchange plain Maps, so no custom
    // Jackson naming strategy is needed.
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
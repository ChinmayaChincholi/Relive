package com.relive.project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Process one photo at a time to prevent AI service overload.
        // BLIP + CLIP + YOLO cannot handle concurrent requests reliably.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        // Queue up to 500 photos waiting to be processed.
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("media-processing-");
        executor.initialize();
        return executor;
    }
}
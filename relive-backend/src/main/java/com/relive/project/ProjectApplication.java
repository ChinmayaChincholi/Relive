package com.relive.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
public class ProjectApplication {

    public static void main(String[] args) {

        // PRE-BOOTSTRAP: ensure directory exists BEFORE Spring starts
        String home = System.getProperty("user.home");
        Path reliveDir = Paths.get(home, ".relive");

        try {
            Files.createDirectories(reliveDir); // safe, idempotent
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Relive data directory: " + reliveDir, e);
        }

        SpringApplication.run(ProjectApplication.class, args);
    }
}
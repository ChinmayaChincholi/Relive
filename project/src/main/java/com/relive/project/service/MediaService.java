package com.relive.project.service;

import com.relive.project.dto.ParsedQueryResponse;
import com.relive.project.dto.VisionResponse;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaObject;
import com.relive.project.entity.User;
import com.relive.project.repository.MediaRepository;
import com.relive.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import com.relive.project.repository.MediaObjectRepository;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final MediaProcessingService mediaProcessingService;
    private final MediaObjectRepository mediaObjectRepository;



    private final String uploadDir = System.getProperty("user.dir") + "/uploads/";

    public String uploadMedia(MultipartFile file, String email) throws IOException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {

            // 1️⃣ Calculate file hash
            String fileHash = calculateFileHash(file);

            Optional<Media> existingMedia =
                    mediaRepository.findByFileHashAndUser_Email(fileHash, email);

            if (existingMedia.isPresent()) {
                return "File already exists. Skipping reprocessing.";
            }

            // 2️⃣ Ensure upload directory exists
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String filePath = uploadDir + file.getOriginalFilename();
            File destination = new File(filePath);
            file.transferTo(destination);

            String mediaType = file.getContentType().startsWith("image")
                    ? "IMAGE"
                    : "VIDEO";

            Media media = Media.builder()
                    .fileName(file.getOriginalFilename())
                    .filePath(filePath)
                    .mediaType(mediaType)
                    .uploadedAt(LocalDateTime.now())
                    .status("PROCESSING")
                    .fileHash(fileHash)
                    .user(user)
                    .build();

            mediaRepository.save(media);

            mediaProcessingService.processMedia(media.getId(), filePath);

            return "File uploaded. Processing started.";

        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing file.";
        }
    }

    public List<Media> getUserMedia(String email) {
        return mediaRepository.findByUser_Email(email);
    }

    public String getStatus(Long id) {
        return mediaRepository.findById(id)
                .map(Media::getStatus)
                .orElse("NOT FOUND");
    }

    public String uploadMultiple(List<MultipartFile> files, String email) throws IOException {

        for (MultipartFile file : files) {
            uploadMedia(file, email);
        }

        return files.size() + " files uploaded. Processing started.";
    }

    public Map<String, Long> getProgress(String email) {

        long total = mediaRepository.countByUser_Email(email);
        long processing = mediaRepository.countByUser_EmailAndStatus(email, "PROCESSING");
        long completed = mediaRepository.countByUser_EmailAndStatus(email, "COMPLETED");
        long failed = mediaRepository.countByUser_EmailAndStatus(email, "FAILED");

        return Map.of(
                "total", total,
                "processing", processing,
                "completed", completed,
                "failed", failed
        );
    }

    public List<Media> searchByObject(String objectName, String email) {

        List<MediaObject> mediaObjects =
                mediaObjectRepository
                        .findByObjectNameContainingIgnoreCaseAndMedia_User_Email(
                                objectName,
                                email
                        );

        return mediaObjects.stream()
                .map(MediaObject::getMedia)
                .distinct()
                .toList();
    }

    private String calculateFileHash(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error calculating file hash", e);
        }
    }

    public List<Media> searchByObjectAndYear(
            String objectName,
            int year,
            String email
    ) {

        return mediaRepository.searchByObjectAndYear(
                objectName.toLowerCase(),
                year,
                email
        );
    }

    public List<Media> searchWithParsedQuery(
            List<String> objects,
            Integer year,
            String email
    ) {
        return mediaRepository.searchByObjectsAndYear(objects, year, email);
    }

    public List<Media> searchByNaturalQuery(String query, String email) {

        RestTemplate restTemplate = new RestTemplate();
        String parseUrl = "http://localhost:5000/parse_query";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("query", query);

        HttpEntity<Map<String, String>> entity =
                new HttpEntity<>(body, headers);

        ResponseEntity<ParsedQueryResponse> response =
                restTemplate.postForEntity(
                        parseUrl,
                        entity,
                        ParsedQueryResponse.class
                );

        ParsedQueryResponse parsed = response.getBody();

        if (parsed == null || parsed.getObjects() == null || parsed.getObjects().isEmpty()) {
            return List.of();
        }

        return searchWithParsedQuery(
                parsed.getObjects(),
                parsed.getYear(),
                email
        );
    }
}

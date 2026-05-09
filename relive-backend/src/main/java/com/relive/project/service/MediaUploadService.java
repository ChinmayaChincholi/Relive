package com.relive.project.service;

import com.relive.project.entity.Media;
import com.relive.project.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaUploadService {

    private final MediaRepository mediaRepository;
    private final MediaProcessingService mediaProcessingService;

    @Value("${media.upload.path}")
    private String uploadDir;

    public String uploadMedia(MultipartFile file) throws IOException {
        try {
            String fileHash = calculateFileHash(file);

            // Deduplication: skip if this exact file is already in the library.
            Optional<Media> existing = mediaRepository.findByFileHash(fileHash);
            if (existing.isPresent()) {
                return "File already exists. Skipping reprocessing.";
            }

            // Ensure uploads directory exists (also guaranteed at startup, but belt-and-suspenders).
            File directory = new File(uploadDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String uniqueFileName = UUID.randomUUID() + extension;

            // Store as absolute path so it is stable regardless of working directory.
            String absolutePath = directory.getAbsolutePath() + File.separator + uniqueFileName;

            File destination = new File(absolutePath);
            file.transferTo(destination);

            String mediaType = (file.getContentType() != null && file.getContentType().startsWith("image"))
                    ? "IMAGE"
                    : "VIDEO";

            Media media = Media.builder()
                    .fileName(originalName)
                    .filePath(absolutePath)
                    .mediaType(mediaType)
                    .uploadedAt(LocalDateTime.now())
                    .status("PROCESSING")
                    .fileHash(fileHash)
                    .build();

            mediaRepository.save(media);

            // Dispatch to single-threaded async executor queue.
            mediaProcessingService.processMedia(media.getId(), absolutePath);

            return "File uploaded. Processing started.";

        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing file: " + e.getMessage();
        }
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
}
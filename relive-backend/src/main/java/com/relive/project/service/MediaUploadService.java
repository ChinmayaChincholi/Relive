package com.relive.project.service;

import com.relive.project.entity.Media;
import com.relive.project.entity.User;
import com.relive.project.repository.MediaRepository;
import com.relive.project.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaUploadService {

    private final MediaRepository mediaRepository;
    private final UserRepository userRepository;
    private final MediaProcessingService mediaProcessingService;

    @Value("${media.upload.path}")
    private String uploadDir;

    public String uploadMedia(MultipartFile file, String email) throws IOException {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {

            String fileHash = calculateFileHash(file);

            Optional<Media> existingMedia =
                    mediaRepository.findByFileHashAndUser_Email(fileHash, email);

            if (existingMedia.isPresent()) {
                return "File already exists. Skipping reprocessing.";
            }

            // Absolute storage root
            String absoluteUploadRoot =
                    System.getProperty("user.dir") + "/" + uploadDir;

            // Relative path (stored in DB)
            String relativeUserFolder =
                    uploadDir + "/" + user.getId();

            // Absolute path (used for writing file)
            String absoluteUserFolder =
                    absoluteUploadRoot + "/" + user.getId();

            File directory = new File(absoluteUserFolder);

            if (!directory.exists()) {
                directory.mkdirs();
            }

            String originalName = file.getOriginalFilename();

            String extension = "";

            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String uniqueFileName = UUID.randomUUID() + extension;

            String relativePath =
                    relativeUserFolder + "/" + uniqueFileName;

            String absolutePath =
                    absoluteUserFolder + "/" + uniqueFileName;

            File destination = new File(absolutePath);

            file.transferTo(destination);

            String mediaType = file.getContentType().startsWith("image")
                    ? "IMAGE"
                    : "VIDEO";

            Media media = Media.builder()
                    .fileName(originalName)
                    .filePath(relativePath) // STORE RELATIVE PATH
                    .mediaType(mediaType)
                    .uploadedAt(LocalDateTime.now())
                    .status("PROCESSING")
                    .fileHash(fileHash)
                    .user(user)
                    .build();

            mediaRepository.save(media);

            mediaProcessingService.processMedia(media.getId(), absolutePath);

            return "File uploaded. Processing started.";

        } catch (Exception e) {

            e.printStackTrace();

            return "Error processing file.";
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
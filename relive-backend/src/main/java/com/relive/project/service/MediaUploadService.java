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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class MediaUploadService {

    private final MediaRepository mediaRepository;
    private final MediaProcessingService mediaProcessingService;
    private final FaceService faceService;
    private final SpellCorrectionService spellCorrectionService;

    @Value("${media.upload.path}")
    private String uploadDir;

    public String uploadMedia(MultipartFile file) throws IOException {
        Long mediaId = saveAndQueue(file);
        if (mediaId == null) return "File already exists. Skipping reprocessing.";

        // Batch of one — still waits for the single file, then clusters/rebuilds once.
        mediaProcessingService.processMedia(mediaId, mediaRepository.findById(mediaId).get().getFilePath())
                .thenRun(this::onBatchComplete);

        return "File uploaded. Processing started.";
    }

    public String uploadMultiple(List<MultipartFile> files) throws IOException {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (MultipartFile file : files) {
            Long mediaId = saveAndQueue(file);
            if (mediaId == null) continue; // duplicate, skipped
            String path = mediaRepository.findById(mediaId).get().getFilePath();
            futures.add(mediaProcessingService.processMedia(mediaId, path));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(this::onBatchComplete);

        return files.size() + " files uploaded. Processing started.";
    }

    /** Runs once after every file in a batch (including a batch of one) has
     *  finished processing — not per image. Re-clustering the unnamed face
     *  pool and rebuilding the spelling dictionary are both cheap operations
     *  meant to run once per batch, not once per photo. */
    private void onBatchComplete() {
        faceService.clusterUnnamedPool();
        spellCorrectionService.rebuildDictionary();
    }

    private Long saveAndQueue(MultipartFile file) throws IOException {
        try {
            String fileHash = calculateFileHash(file);

            Optional<Media> existing = mediaRepository.findByFileHash(fileHash);
            if (existing.isPresent()) return null;

            File directory = new File(uploadDir);
            if (!directory.exists()) directory.mkdirs();

            String originalName = file.getOriginalFilename();
            String extension = "";
            if (originalName != null && originalName.contains(".")) {
                extension = originalName.substring(originalName.lastIndexOf("."));
            }

            String uniqueFileName = UUID.randomUUID() + extension;
            String absolutePath = directory.getAbsolutePath() + File.separator + uniqueFileName;

            File destination = new File(absolutePath);
            file.transferTo(destination);

            String mediaType = (file.getContentType() != null && file.getContentType().startsWith("image"))
                    ? "IMAGE" : "VIDEO";

            Media media = Media.builder()
                    .fileName(originalName)
                    .filePath(absolutePath)
                    .mediaType(mediaType)
                    .uploadedAt(LocalDateTime.now())
                    .status("PROCESSING")
                    .fileHash(fileHash)
                    .build();

            mediaRepository.save(media);
            return media.getId();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
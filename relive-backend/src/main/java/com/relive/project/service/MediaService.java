package com.relive.project.service;

import com.relive.project.entity.Media;
import com.relive.project.repository.FaceEmbeddingRepository;
import com.relive.project.repository.MediaObjectRepository;
import com.relive.project.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaUploadService mediaUploadService;
    private final MediaSearchService mediaSearchService;
    private final MediaRepository mediaRepository;
    private final MediaObjectRepository mediaObjectRepository;
    private final FaceEmbeddingRepository faceEmbeddingRepository;

    public String uploadMedia(MultipartFile file) throws IOException {
        return mediaUploadService.uploadMedia(file);
    }

    public List<Media> getAllMedia() {
        return mediaRepository.findAll();
    }

    public String uploadMultiple(List<MultipartFile> files) throws IOException {
        for (MultipartFile file : files) {
            mediaUploadService.uploadMedia(file);
        }
        return files.size() + " files uploaded. Processing started.";
    }

    public Map<String, Long> getProgress() {
        long total      = mediaRepository.count();
        long processing = mediaRepository.countByStatus("PROCESSING");
        long completed  = mediaRepository.countByStatus("COMPLETED");
        long failed     = mediaRepository.countByStatus("FAILED");
        return Map.of(
                "total",      total,
                "processing", processing,
                "completed",  completed,
                "failed",     failed
        );
    }

    public List<Media> searchByNaturalQuery(String query) {
        return mediaSearchService.searchByNaturalQuery(query);
    }

    /**
     * Returns the absolute file path for a given media ID.
     * Returns null if the media does not exist.
     */
    public String getImagePath(Long id) {
        return mediaRepository.findById(id)
                .map(Media::getFilePath)
                .orElse(null);
    }

    /**
     * Deletes a media item and all associated data (objects, face embeddings).
     * Also attempts to delete the file from disk.
     */
    @Transactional
    public void deleteMedia(Long id) {
        Media media = mediaRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Media not found: " + id)
        );

        // Delete associated face embeddings (unlink from person first)
        faceEmbeddingRepository.findByMedia_Id(id).forEach(fe -> {
            fe.setPerson(null);
            faceEmbeddingRepository.save(fe);
        });
        faceEmbeddingRepository.deleteByMedia(media);

        // Delete associated object tags
        mediaObjectRepository.deleteByMedia(media);

        // Attempt to delete file from disk (best-effort)
        if (media.getFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(media.getFilePath()));
            } catch (IOException e) {
                System.out.println("Could not delete file from disk: " + media.getFilePath());
            }
        }

        mediaRepository.delete(media);
    }
}
package com.relive.project.service;

import com.relive.project.client.VectorDeleteClient;
import com.relive.project.entity.FaceEmbedding;
import com.relive.project.entity.Media;
import com.relive.project.repository.FaceEmbeddingRepository;
import com.relive.project.repository.FacePersonRepository;
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
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaUploadService mediaUploadService;
    private final MediaSearchService mediaSearchService;
    private final MediaRepository mediaRepository;
    private final MediaObjectRepository mediaObjectRepository;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FacePersonRepository facePersonRepository;
    private final VectorDeleteClient vectorDeleteClient;

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

    public List<Media> searchByNaturalQuery(String query, boolean refine) {
        return mediaSearchService.searchByNaturalQuery(query, refine);
    }

    public String getImagePath(Long id) {
        return mediaRepository.findById(id)
                .map(Media::getFilePath)
                .orElse(null);
    }

    @Transactional
    public void deleteMedia(Long id) {
        Media media = mediaRepository.findById(id).orElseThrow(
                () -> new RuntimeException("Media not found: " + id)
        );

        List<FaceEmbedding> embeddings = faceEmbeddingRepository.findByMedia_Id(id);
        Set<Long> affectedPersonIds = embeddings.stream()
                .filter(fe -> fe.getPerson() != null)
                .map(fe -> fe.getPerson().getId())
                .collect(Collectors.toSet());

        faceEmbeddingRepository.deleteByMedia(media);

        for (Long personId : affectedPersonIds) {
            facePersonRepository.findById(personId).ifPresent(person -> {
                if (faceEmbeddingRepository.findByPerson(person).isEmpty()) {
                    facePersonRepository.delete(person);
                }
            });
        }

        mediaObjectRepository.deleteByMedia(media);

        try {
            vectorDeleteClient.deleteVectors(id);
        } catch (Exception e) {
            System.out.println("Failed to delete vectors for media " + id + ": " + e.getMessage());
        }

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
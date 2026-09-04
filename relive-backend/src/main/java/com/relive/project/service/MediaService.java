package com.relive.project.service;

import com.relive.project.entity.FaceEmbedding;
import com.relive.project.entity.Media;
import com.relive.project.repository.FaceEmbeddingRepository;
import com.relive.project.repository.FacePersonRepository;
import com.relive.project.repository.MediaKeywordRepository;
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
    private final SearchService searchService;
    private final MediaRepository mediaRepository;
    private final MediaKeywordRepository mediaKeywordRepository;
    private final FaceEmbeddingRepository faceEmbeddingRepository;
    private final FacePersonRepository facePersonRepository;

    public String uploadMedia(MultipartFile file) throws IOException {
        return mediaUploadService.uploadMedia(file);
    }

    public List<Media> getAllMedia() {
        return mediaRepository.findAll();
    }

    public String uploadMultiple(List<MultipartFile> files) throws IOException {
        return mediaUploadService.uploadMultiple(files);
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
        return searchService.searchByNaturalQuery(query);
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

        mediaKeywordRepository.deleteByMedia(media);

        // No AI-service call needed anymore — there's no external vector
        // store; everything searchable lives in this database and is
        // already removed above.

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
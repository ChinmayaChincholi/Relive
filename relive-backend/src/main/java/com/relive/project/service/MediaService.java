package com.relive.project.service;

import com.relive.project.entity.FaceEmbedding;
import com.relive.project.entity.Media;
import com.relive.project.repository.FaceEmbeddingRepository;
import com.relive.project.repository.FacePersonRepository;
import com.relive.project.repository.MediaKeywordRepository;
import com.relive.project.repository.MediaRepository;
import com.relive.project.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.relive.project.dto.UploadResultDTO;
import com.relive.project.util.FileCleanup;
import org.springframework.beans.factory.annotation.Value;

import java.nio.file.Path;
import java.util.ArrayList;
import java.io.IOException;
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
    private final LocationRepository locationRepository;

    @Value("${relive.data.dir}")
    private String dataDir;

    public String uploadMedia(MultipartFile file) throws IOException {
        return mediaUploadService.uploadMedia(file);
    }

    public List<Media> getAllMedia() {
        return mediaRepository.findAll();
    }

    public UploadResultDTO uploadMultiple(List<MultipartFile> files) throws IOException {
        return mediaUploadService.uploadMultiple(files);
    }

    public Map<String, Long> getProgress() {
        long total = mediaRepository.count();
        long processing = mediaRepository.countByStatus("PROCESSING");
        long completed = mediaRepository.countByStatus("COMPLETED");
        long failed = mediaRepository.countByStatus("FAILED");
        return Map.of(
                "total", total,
                "processing", processing,
                "completed", completed,
                "failed", failed
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

        // Every file that must leave the disk with this photo: the photo itself
        // plus each face-crop image cut from it. Collected BEFORE the rows are
        // deleted (crop paths live in face_embeddings) but only deleted AFTER
        // the transaction commits, so a rolled-back DB delete can never leave
        // the database pointing at missing files.
        List<Path> filesToDelete = new ArrayList<>();
        if (media.getFilePath() != null) {
            filesToDelete.add(Paths.get(media.getFilePath()));
        }
        for (FaceEmbedding fe : faceEmbeddingRepository.findByMedia(media)) {
            Path crop = FileCleanup.resolveInside(dataDir, fe.getCropPath());
            if (crop != null) filesToDelete.add(crop);
        }

        // Orphaned FacePerson cleanup (a person left with zero embeddings
        // once this media's faces are removed) is handled automatically by
        // trg_face_embedding_delete_orphan_person (see
        // DatabaseIntegrityStartupProcessor) as part of the delete below.
        // Doing it again here raced against that trigger's own delete and
        // threw StaleObjectStateException, since Hibernate's persistence
        // context had no way to know the trigger had already removed the row.
        faceEmbeddingRepository.deleteByMedia(media);
        mediaKeywordRepository.deleteByMedia(media);
        locationRepository.deleteByMedia(media);

        mediaRepository.delete(media);

        FileCleanup.deleteAfterCommit(filesToDelete);
    }
}
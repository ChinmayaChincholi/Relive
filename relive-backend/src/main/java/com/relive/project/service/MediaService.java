package com.relive.project.service;

import com.relive.project.entity.Media;
import com.relive.project.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final MediaUploadService mediaUploadService;
    private final MediaSearchService mediaSearchService;
    private final MediaRepository mediaRepository;

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
}
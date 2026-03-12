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

    public String uploadMedia(MultipartFile file, String email) throws IOException {
        return mediaUploadService.uploadMedia(file, email);
    }

    public List<Media> getUserMedia(String email) {
        return mediaRepository.findByUser_Email(email);
    }

    public String uploadMultiple(List<MultipartFile> files, String email) throws IOException {

        for (MultipartFile file : files) {
            mediaUploadService.uploadMedia(file, email);
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

    public List<Media> searchByObject(String object, String email) {
        return mediaSearchService.searchByObject(object, email);
    }

    public List<Media> searchByObjectAndYear(String object, int year, String email) {
        return mediaSearchService.searchByObjectAndYear(object, year, email);
    }

    public List<Media> searchByNaturalQuery(String query, String email) {
        return mediaSearchService.searchByNaturalQuery(query, email);
    }

    public String getStatus(Long id) {
        return mediaRepository.findById(id)
                .map(Media::getStatus)
                .orElse("NOT FOUND");
    }
}
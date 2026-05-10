package com.relive.project.controller;

import com.relive.project.dto.ApiResponse;
import com.relive.project.dto.MediaResponseDTO;
import com.relive.project.mapper.MediaMapper;
import com.relive.project.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload")
    public ApiResponse<String> upload(
            @RequestParam("file") MultipartFile file
    ) throws Exception {
        String message = mediaService.uploadMedia(file);
        return new ApiResponse<>(true, message, null);
    }

    @PostMapping("/upload-folder")
    public ApiResponse<String> uploadFolder(
            @RequestParam("files") List<MultipartFile> files
    ) throws Exception {
        String message = mediaService.uploadMultiple(files);
        return new ApiResponse<>(true, message, null);
    }

    @GetMapping("/my")
    public ApiResponse<List<MediaResponseDTO>> getMyMedia() {
        List<MediaResponseDTO> media = mediaService.getAllMedia()
                .stream()
                .map(MediaMapper::toDTO)
                .toList();
        return new ApiResponse<>(true, "Media fetched successfully", media);
    }

    @GetMapping("/search-natural")
    public ApiResponse<List<MediaResponseDTO>> searchNatural(
            @RequestParam String query
    ) {
        List<MediaResponseDTO> results = mediaService.searchByNaturalQuery(query)
                .stream()
                .map(MediaMapper::toDTO)
                .toList();
        return new ApiResponse<>(true, "Search completed", results);
    }

    @GetMapping("/progress")
    public ApiResponse<Map<String, Long>> getProgress() {
        return new ApiResponse<>(true, "Progress fetched", mediaService.getProgress());
    }

    /**
     * Delete a media item by ID (removes from DB and optionally from disk).
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteMedia(@PathVariable Long id) {
        mediaService.deleteMedia(id);
        return new ApiResponse<>(true, "Media deleted", null);
    }

    /**
     * Serves the raw image bytes for a given media ID.
     * The stored path is absolute, so no path reconstruction is needed.
     */
    @GetMapping("/image/{id}")
    public ResponseEntity<byte[]> getImage(@PathVariable Long id) throws IOException {
        String absolutePath = mediaService.getImagePath(id);
        if (absolutePath == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(absolutePath);
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        byte[] imageBytes = Files.readAllBytes(filePath);
        String contentType = Files.probeContentType(filePath);
        if (contentType == null) contentType = "image/jpeg";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(imageBytes);
    }
}
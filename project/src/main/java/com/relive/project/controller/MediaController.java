package com.relive.project.controller;

import com.relive.project.entity.Media;
import com.relive.project.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {
    private final MediaService mediaService;

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws Exception {

        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        return mediaService.uploadMedia(file, email);
    }

    @GetMapping("/my")
    public List<Media> getMyMedia() {

        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        return mediaService.getUserMedia(email);
    }

    @GetMapping("/status/{id}")
    public String getStatus(@PathVariable Long id) {
        return mediaService.getStatus(id);
    }

    @PostMapping("/upload-folder")
    public String uploadFolder(@RequestParam("files") List<MultipartFile> files) throws Exception {

        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        return mediaService.uploadMultiple(files, email);
    }

    @GetMapping("/progress")
    public Map<String, Long> getProgress() {

        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        return mediaService.getProgress(email);
    }

    @GetMapping("/search")
    public List<Media> searchByObject(@RequestParam String object) {

        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        return mediaService.searchByObject(object, email);
    }

    @GetMapping("/search-advanced")
    public List<Media> searchByObjectAndYear(
            @RequestParam String object,
            @RequestParam int year
    ) {

        String email = (String) SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        return mediaService.searchByObjectAndYear(object, year, email);
    }

    @GetMapping("/search-natural")
    public ResponseEntity<List<Media>> searchNatural(
            @RequestParam String query,
            Authentication authentication
    ) {

        String email = authentication.getName();

        return ResponseEntity.ok(
                mediaService.searchByNaturalQuery(query, email)
        );
    }
}

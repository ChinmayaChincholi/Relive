package com.relive.project.controller;

import com.relive.project.dto.ApiResponse;
import com.relive.project.dto.MediaResponseDTO;
import com.relive.project.mapper.MediaMapper;
import com.relive.project.service.MediaService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaService mediaService;

    @PostMapping("/upload")
    public ApiResponse<String> upload(
            @RequestParam("file") MultipartFile file,
            Authentication auth
    ) throws Exception {

        String email = auth.getName();

        String message = mediaService.uploadMedia(file, email);

        return new ApiResponse<>(
                true,
                message,
                null
        );
    }

    @PostMapping("/upload-folder")
    public ApiResponse<String> uploadFolder(
            @RequestParam("files") List<MultipartFile> files,
            Authentication auth
    ) throws Exception {

        String email = auth.getName();

        String message = mediaService.uploadMultiple(files, email);

        return new ApiResponse<>(
                true,
                message,
                null
        );
    }

    @GetMapping("/my")
    public ApiResponse<List<MediaResponseDTO>> getMyMedia(Authentication auth) {

        String email = auth.getName();

        List<MediaResponseDTO> media =
                mediaService.getUserMedia(email)
                        .stream()
                        .map(MediaMapper::toDTO)
                        .toList();

        return new ApiResponse<>(
                true,
                "Media fetched successfully",
                media
        );
    }

    @GetMapping("/search-natural")
    public ApiResponse<List<MediaResponseDTO>> searchNatural(
            @RequestParam String query,
            Authentication auth
    ) {

        String email = auth.getName();

        List<MediaResponseDTO> results =
                mediaService.searchByNaturalQuery(query, email)
                        .stream()
                        .map(MediaMapper::toDTO)
                        .toList();

        return new ApiResponse<>(
                true,
                "Search completed",
                results
        );
    }

    @GetMapping("/progress")
    public ApiResponse<Map<String, Long>> getProgress(Authentication auth) {

        String email = auth.getName();

        Map<String, Long> progress =
                mediaService.getProgress(email);

        return new ApiResponse<>(
                true,
                "Progress fetched",
                progress
        );
    }

}
package com.relive.project.controller;

import com.relive.project.dto.ApiResponse;
import com.relive.project.dto.FacePersonDTO;
import com.relive.project.dto.MediaResponseDTO;
import com.relive.project.mapper.MediaMapper;
import com.relive.project.service.FaceService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/faces")
@RequiredArgsConstructor
public class FaceController {

    private final FaceService faceService;

    @GetMapping("/people")
    public ApiResponse<List<FacePersonDTO>> getPeople(Authentication auth) {

        String email = auth.getName();

        List<FacePersonDTO> people = faceService.getPeopleForUser(email);

        return new ApiResponse<>(true, "People fetched", people);
    }

    @PostMapping("/name/{personId}")
    public ApiResponse<String> namePerson(
            @PathVariable Long personId,
            @RequestBody Map<String, String> body,
            Authentication auth
    ) {

        String email = auth.getName();
        String name = body.get("name");

        faceService.namePerson(personId, name, email);

        return new ApiResponse<>(true, "Person named successfully", null);
    }

    @GetMapping("/person/{personId}/photos")
    public ApiResponse<List<MediaResponseDTO>> getPhotosForPerson(
            @PathVariable Long personId,
            Authentication auth
    ) {

        String email = auth.getName();

        List<MediaResponseDTO> photos = faceService.getPhotosForPerson(personId, email)
                .stream()
                .map(MediaMapper::toDTO)
                .toList();

        return new ApiResponse<>(true, "Photos fetched", photos);
    }

    // Serves face crop thumbnails securely
    @GetMapping("/crop")
    public ResponseEntity<byte[]> getCrop(
            @RequestParam String path,
            Authentication auth
    ) throws IOException {

        // Resolve relative to the AI service working directory
        // face_crops folder is inside relive-ai-service/
        String aiServiceDir = System.getProperty("user.dir")
                .replace("relive-backend", "relive-ai-service");

        Path filePath = Paths.get(aiServiceDir, path);

        if (!Files.exists(filePath)) {
            System.out.println("Crop not found at: " + filePath.toAbsolutePath());
            return ResponseEntity.notFound().build();
        }

        byte[] imageBytes = Files.readAllBytes(filePath);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(imageBytes);
    }
}

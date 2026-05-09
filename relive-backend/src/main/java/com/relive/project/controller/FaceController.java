package com.relive.project.controller;

import com.relive.project.dto.ApiResponse;
import com.relive.project.dto.FacePersonDTO;
import com.relive.project.dto.MediaResponseDTO;
import com.relive.project.mapper.MediaMapper;
import com.relive.project.service.FaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @Value("${relive.data.dir}")
    private String dataDir;

    @GetMapping("/people")
    public ApiResponse<List<FacePersonDTO>> getPeople() {
        List<FacePersonDTO> people = faceService.getPeople();
        return new ApiResponse<>(true, "People fetched", people);
    }

    @PostMapping("/name/{personId}")
    public ApiResponse<String> namePerson(
            @PathVariable Long personId,
            @RequestBody Map<String, String> body
    ) {
        String name = body.get("name");
        faceService.namePerson(personId, name);
        return new ApiResponse<>(true, "Person named successfully", null);
    }

    @GetMapping("/person/{personId}/photos")
    public ApiResponse<List<MediaResponseDTO>> getPhotosForPerson(
            @PathVariable Long personId
    ) {
        List<MediaResponseDTO> photos = faceService.getPhotosForPerson(personId)
                .stream()
                .map(MediaMapper::toDTO)
                .toList();
        return new ApiResponse<>(true, "Photos fetched", photos);
    }

    /**
     * Serves face crop thumbnail images.
     *
     * The AI service writes crop files under its own working directory.
     * The stored crop path is relative to the Relive data dir (e.g.
     * "face_crops/crop_123.jpg"). We resolve it against ${relive.data.dir}.
     *
     * If the crop path is already absolute, Paths.get(dataDir, path) still
     * works correctly because Paths.get resolves the last absolute segment.
     */
    @GetMapping("/crop")
    public ResponseEntity<byte[]> getCrop(@RequestParam String path) throws IOException {
        Path filePath = Paths.get(dataDir, path);

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
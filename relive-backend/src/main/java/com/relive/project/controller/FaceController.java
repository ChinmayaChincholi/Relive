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

    @PostMapping("/merge")
    public ApiResponse<String> mergePeople(@RequestBody Map<String, Object> body) {
        Long personId1 = Long.valueOf(body.get("personId1").toString());
        Long personId2 = Long.valueOf(body.get("personId2").toString());
        String name = body.get("name") != null ? body.get("name").toString() : null;
        faceService.mergePeople(personId1, personId2, name);
        return new ApiResponse<>(true, "People merged successfully", null);
    }

    @DeleteMapping("/person/{personId}")
    public ApiResponse<String> deletePerson(@PathVariable Long personId) {
        faceService.deletePerson(personId);
        return new ApiResponse<>(true, "Person deleted", null);
    }

    @GetMapping("/crop")
    public ResponseEntity<byte[]> getCrop(@RequestParam String path) throws IOException {
        Path base = Paths.get(dataDir).normalize().toAbsolutePath();
        Path filePath = base.resolve(path).normalize();

        // Reject any path that resolves outside dataDir (e.g. via "../" segments)
        // — this endpoint is unauthenticated, so it must not become an
        // arbitrary-file-read endpoint.
        if (!filePath.startsWith(base)) {
            return ResponseEntity.badRequest().build();
        }

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
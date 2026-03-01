package com.relive.project.service;

import com.relive.project.dto.VisionResponse;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaObject;
import com.relive.project.repository.MediaObjectRepository;
import com.relive.project.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.Map;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    private final MediaRepository mediaRepository;
    private final MediaObjectRepository mediaObjectRepository;

    @Async
    public void processMedia(Long mediaId, String filePath) {

        try {

            RestTemplate restTemplate = new RestTemplate();
            String visionUrl = "http://localhost:5000/analyze";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, String> requestBody = Map.of(
                    "file_path", filePath
            );

            HttpEntity<Map<String, String>> entity =
                    new HttpEntity<>(requestBody, headers);

            ResponseEntity<VisionResponse> response =
                    restTemplate.postForEntity(
                            visionUrl,
                            entity,
                            VisionResponse.class
                    );

            VisionResponse visionData = response.getBody();

            System.out.println("DATE FROM PYTHON: " + visionData.getDate_taken());

            // Fetch media FIRST
            Media media = mediaRepository.findById(mediaId)
                    .orElseThrow();

            // EXIF Date Handling
            if (visionData.getDate_taken() != null) {
                try {
                    DateTimeFormatter formatter =
                            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

                    LocalDateTime dateTaken =
                            LocalDateTime.parse(
                                    visionData.getDate_taken(),
                                    formatter
                            );

                    media.setDateTaken(dateTaken);

                } catch (Exception ignored) {
                }
            }

            // Save caption
            media.setSceneCaption(visionData.getCaption());

            // Save face count
            media.setFaceCount(visionData.getFace_count());

            // Save time of day
            media.setEventType(visionData.getTime_of_day());

            // Save objects
            List<String> objects = visionData.getSemantic_objects();

            if (objects != null) {
                for (String obj : objects) {

                    MediaObject mediaObject = MediaObject.builder()
                            .objectName(obj.toLowerCase())
                            .media(media)
                            .build();

                    mediaObjectRepository.save(mediaObject);
                }
            }

            media.setStatus("COMPLETED");
            mediaRepository.save(media);

        } catch (Exception e) {

            System.out.println("ERROR processing media ID: " + mediaId);
            e.printStackTrace();

            Media media = mediaRepository.findById(mediaId)
                    .orElseThrow();

            media.setStatus("FAILED");
            mediaRepository.save(media);
        }
    }
}
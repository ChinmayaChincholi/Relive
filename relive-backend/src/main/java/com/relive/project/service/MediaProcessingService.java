package com.relive.project.service;

import com.relive.project.client.VisionClient;
import com.relive.project.dto.VisionResponse;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaObject;
import com.relive.project.repository.MediaObjectRepository;
import com.relive.project.repository.MediaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    private final MediaRepository mediaRepository;
    private final MediaObjectRepository mediaObjectRepository;
    private final VisionClient visionClient;

    @Async
    public void processMedia(Long mediaId, String filePath) {

        try {

            VisionResponse visionData = visionClient.analyzeImage(filePath);

            Media media = mediaRepository.findById(mediaId).orElseThrow();

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

            media.setSceneCaption(visionData.getCaption());
            media.setFaceCount(visionData.getFace_count());
            media.setEventType(visionData.getTime_of_day());

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

            Media media = mediaRepository.findById(mediaId).orElseThrow();

            media.setStatus("FAILED");

            mediaRepository.save(media);
        }
    }
}
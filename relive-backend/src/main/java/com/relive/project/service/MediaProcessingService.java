package com.relive.project.service;

import com.relive.project.client.VisionClient;
import com.relive.project.dto.VisionResponse;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaObject;
import com.relive.project.repository.MediaObjectRepository;
import com.relive.project.repository.MediaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    private final MediaRepository mediaRepository;
    private final MediaObjectRepository mediaObjectRepository;
    private final VisionClient visionClient;
    private final FaceService faceService;

    // Self-injection to ensure @Transactional proxy is used when calling analyzeAndSave
    @Lazy
    @Autowired
    private MediaProcessingService self;

    @Async("taskExecutor")
    public void processMedia(Long mediaId, String filePath) {

        // Call via self to go through Spring proxy so @Transactional works
        boolean success = self.analyzeAndSave(mediaId, filePath);

        if (success) {
            faceService.extractAndStoreFaces(mediaId, filePath);
            System.out.println("Face extraction done for media ID: " + mediaId);
        }
    }

    @Transactional
    public boolean analyzeAndSave(Long mediaId, String filePath) {

        try {

            System.out.println("Processing media ID: " + mediaId);

            VisionResponse visionData = visionClient.analyzeImage(filePath, mediaId);

            Media media = mediaRepository.findById(mediaId).orElseThrow();

            if (visionData.getDate_taken() != null) {
                try {
                    DateTimeFormatter formatter =
                            DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
                    LocalDateTime dateTaken =
                            LocalDateTime.parse(visionData.getDate_taken(), formatter);
                    media.setDateTaken(dateTaken);
                } catch (Exception ignored) {}
            }

            if (visionData.getLocation() != null) {
                media.setLocation(visionData.getLocation());
            }

            media.setSceneCaption(visionData.getCaption());
            media.setFaceCount(visionData.getFace_count());
            media.setEventType(visionData.getTime_of_day());

            mediaObjectRepository.deleteByMedia(media);

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

            System.out.println("Completed media ID: " + mediaId);

            return true;

        } catch (Exception e) {

            System.out.println("ERROR processing media ID: " + mediaId);
            e.printStackTrace();

            mediaRepository.findById(mediaId).ifPresent(m -> {
                m.setStatus("FAILED");
                mediaRepository.save(m);
            });

            return false;
        }
    }
}
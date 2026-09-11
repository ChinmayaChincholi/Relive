package com.relive.project.service;

import com.relive.project.client.VocabularyClient;
import com.relive.project.entity.Location;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaKeyword;
import com.relive.project.repository.LocationRepository;
import com.relive.project.repository.MediaKeywordRepository;
import com.relive.project.repository.MediaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class MediaProcessingService {
    private final MediaRepository mediaRepository;
    private final MediaKeywordRepository mediaKeywordRepository;
    private final LocationRepository locationRepository;
    private final VocabularyClient vocabularyClient;
    private final FaceService faceService;

    @Lazy
    @Autowired
    private MediaProcessingService self;

    /** Returns a Future so callers (MediaUploadService) can wait for a whole
     * batch to finish before triggering face re-clustering and the spelling
     * dictionary rebuild once, instead of per image. */
    @Async("taskExecutor")
    public CompletableFuture<Void> processMedia(Long mediaId, String filePath) {
        long start = System.nanoTime();
        System.out.println("[MediaProcessingService] media_id=" + mediaId + " START processing");
        boolean success = self.analyzeAndSave(mediaId, filePath);
        if (success) {
            faceService.extractAndAssignFaces(mediaId, filePath);
        }
        double elapsed = (System.nanoTime() - start) / 1_000_000_000.0;
        System.out.printf("[MediaProcessingService] media_id=%d DONE success=%b total_took=%.3fs%n",
                mediaId, success, elapsed);
        return CompletableFuture.completedFuture(null);
    }


    @Transactional
    public boolean analyzeAndSave(Long mediaId, String filePath) {
        try {
            System.out.println("Processing media ID: " + mediaId);
            long analyzeStart = System.nanoTime();
            VocabularyClient.AnalyzeResult analysis = vocabularyClient.analyzeImage(filePath, mediaId);
            double analyzeElapsed = (System.nanoTime() - analyzeStart) / 1_000_000_000.0;
            System.out.printf("[MediaProcessingService] media_id=%d step=analyzeImage (AI service call) took=%.3fs%n",
                    mediaId, analyzeElapsed);

            long dbStart = System.nanoTime();
            Media media = mediaRepository.findById(mediaId).orElseThrow();

            if (analysis.dateTaken != null) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");
                    media.setDateTaken(LocalDateTime.parse(analysis.dateTaken, formatter));
                } catch (Exception ignored) {
                }
            }
            media.setLocation(analysis.locationDisplay); // display-only convenience field

            mediaKeywordRepository.deleteByMedia(media);
            for (String word : analysis.vocabularyWords) {
                String key = word.trim().toLowerCase();
                if (key.isBlank()) continue;
                mediaKeywordRepository.save(MediaKeyword.builder()
                        .keyword(key).media(media).build());
            }

            locationRepository.deleteByMedia(media);
            saveLocationKeyword(media, analysis.locationCity);
            saveLocationKeyword(media, analysis.locationRegion);
            saveLocationKeyword(media, analysis.locationCountry);

            media.setStatus("COMPLETED");
            mediaRepository.save(media);
            double dbElapsed = (System.nanoTime() - dbStart) / 1_000_000_000.0;
            System.out.printf("[MediaProcessingService] media_id=%d step=dbWrite (%d keywords) took=%.3fs%n",
                    mediaId, analysis.vocabularyWords.size(), dbElapsed);
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

    private void saveLocationKeyword(Media media, String value) {
        if (value == null || value.isBlank()) return;
        locationRepository.save(Location.builder()
                .locationName(value.toLowerCase().trim()).media(media).build());
    }
}
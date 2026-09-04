package com.relive.project.service;

import com.relive.project.client.VocabularyClient;
import com.relive.project.entity.Domain;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaKeyword;
import com.relive.project.repository.MediaKeywordRepository;
import com.relive.project.repository.MediaRepository;
import com.relive.project.util.LemmatizerUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
public class MediaProcessingService {

    private final MediaRepository mediaRepository;
    private final MediaKeywordRepository mediaKeywordRepository;
    private final VocabularyClient vocabularyClient;
    private final FaceService faceService;

    @Lazy
    @Autowired
    private MediaProcessingService self;

    /** Returns a Future so callers (MediaUploadService) can wait for a whole
     *  batch to finish before triggering face re-clustering and the spelling
     *  dictionary rebuild once, instead of per image. */
    @Async("taskExecutor")
    public CompletableFuture<Void> processMedia(Long mediaId, String filePath) {
        boolean success = self.analyzeAndSave(mediaId, filePath);
        if (success) {
            faceService.extractAndAssignFaces(mediaId, filePath);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Transactional
    public boolean analyzeAndSave(Long mediaId, String filePath) {
        try {
            System.out.println("Processing media ID: " + mediaId);

            VocabularyClient.AnalyzeResult analysis = vocabularyClient.analyzeImage(filePath, mediaId);

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

            // VOCAB keywords (already lemmatized on the Python side; re-lemmatize
            // here too as a safety net in case a word slipped through unnormalized).
            for (String word : analysis.vocabularyWords) {
                String key = LemmatizerUtil.lemmatize(word);
                if (key.isBlank()) continue;
                mediaKeywordRepository.save(MediaKeyword.builder()
                        .keyword(key).domain(Domain.VOCAB).media(media).build());
            }

            // LOCATION keywords — 3 separate granularities per §6 of the design doc.
            saveLocationKeyword(media, analysis.locationCity);
            saveLocationKeyword(media, analysis.locationRegion);
            saveLocationKeyword(media, analysis.locationCountry);

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

    private void saveLocationKeyword(Media media, String value) {
        if (value == null || value.isBlank()) return;
        mediaKeywordRepository.save(MediaKeyword.builder()
                .keyword(value.toLowerCase().trim()).domain(Domain.LOCATION).media(media).build());
    }
}
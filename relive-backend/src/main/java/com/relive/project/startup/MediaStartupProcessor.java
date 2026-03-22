package com.relive.project.startup;

import com.relive.project.entity.Media;
import com.relive.project.repository.MediaRepository;
import com.relive.project.service.MediaProcessingService;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MediaStartupProcessor {

    private final MediaRepository mediaRepository;
    private final MediaProcessingService mediaProcessingService;

    @EventListener(ApplicationReadyEvent.class)
    public void reprocessUnfinishedMedia() {

        // Find all media stuck in PROCESSING or FAILED state
        List<Media> unfinished = mediaRepository
                .findByStatusIn(List.of("PROCESSING", "FAILED"));

        if (unfinished.isEmpty()) {
            System.out.println("Startup check: all media already processed.");
            return;
        }

        System.out.println("Startup check: found " + unfinished.size()
                + " unprocessed media items. Queuing now...");

        for (Media media : unfinished) {

            String absolutePath =
                    System.getProperty("user.dir") + "/" + media.getFilePath();

            // Reset to PROCESSING so frontend shows correct status
            media.setStatus("PROCESSING");
            mediaRepository.save(media);

            mediaProcessingService.processMedia(media.getId(), absolutePath);
        }
    }
}
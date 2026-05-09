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

    /**
     * On startup, find any media items stuck in PROCESSING or FAILED state
     * (e.g. due to a crash during a previous session) and re-queue them.
     *
     * filePath is now stored as an absolute path, so no reconstruction needed.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void reprocessUnfinishedMedia() {
        List<Media> unfinished = mediaRepository.findByStatusIn(List.of("PROCESSING", "FAILED"));

        if (unfinished.isEmpty()) {
            System.out.println("Startup check: all media already processed.");
            return;
        }

        System.out.println("Startup check: found " + unfinished.size()
                + " unprocessed media items. Queuing now...");

        for (Media media : unfinished) {
            media.setStatus("PROCESSING");
            mediaRepository.save(media);
            mediaProcessingService.processMedia(media.getId(), media.getFilePath());
        }
    }
}
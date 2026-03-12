package com.relive.project.service;

import com.relive.project.client.QueryParserClient;
import com.relive.project.dto.ParsedQueryResponse;
import com.relive.project.entity.Media;
import com.relive.project.entity.MediaObject;
import com.relive.project.repository.MediaRepository;
import com.relive.project.repository.MediaObjectRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MediaSearchService {

    private final MediaRepository mediaRepository;
    private final MediaObjectRepository mediaObjectRepository;
    private final QueryParserClient queryParserClient;

    public List<Media> searchByObject(String objectName, String email) {

        List<MediaObject> mediaObjects =
                mediaObjectRepository
                        .findByObjectNameContainingIgnoreCaseAndMedia_User_Email(
                                objectName,
                                email
                        );

        return mediaObjects.stream()
                .map(MediaObject::getMedia)
                .distinct()
                .toList();
    }

    public List<Media> searchByObjectAndYear(
            String objectName,
            int year,
            String email
    ) {

        return mediaRepository.searchByObjectAndYear(
                objectName.toLowerCase(),
                year,
                email
        );
    }

    public List<Media> searchByNaturalQuery(String query, String email) {

        ParsedQueryResponse parsed = queryParserClient.parseQuery(query);

        if (parsed == null || parsed.getObjects() == null || parsed.getObjects().isEmpty()) {
            return List.of();
        }

        return mediaRepository.searchByObjectsAndYear(
                parsed.getObjects(),
                parsed.getYear(),
                email
        );
    }
}
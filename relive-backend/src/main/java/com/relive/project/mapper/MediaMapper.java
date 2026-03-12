package com.relive.project.mapper;

import com.relive.project.dto.MediaResponseDTO;
import com.relive.project.entity.Media;

public class MediaMapper {

    public static MediaResponseDTO toDTO(Media media) {

        MediaResponseDTO dto = new MediaResponseDTO();

        dto.setId(media.getId());
        dto.setFileName(media.getFileName());
        dto.setSceneCaption(media.getSceneCaption());
        dto.setFaceCount(media.getFaceCount());
        dto.setStatus(media.getStatus());
        dto.setDateTaken(media.getDateTaken());

        return dto;
    }
}
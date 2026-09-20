package com.relive.project.dto;

import lombok.Data;

@Data
public class FaceCropDTO {
    private Long embeddingId;
    private String cropPath;
    private Long mediaId;
}
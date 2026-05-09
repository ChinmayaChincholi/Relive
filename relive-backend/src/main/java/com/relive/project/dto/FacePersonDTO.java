package com.relive.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class FacePersonDTO {

    private Long personId;
    private String name;

    // One representative crop path per person (for the thumbnail in UI)
    private String representativeCrop;

    // All media IDs that contain this person
    private List<Long> mediaIds;

    // All crop paths for this person (for the detail view)
    private List<String> cropPaths;
}
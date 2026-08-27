package com.relive.project.dto;

import lombok.Data;

import java.util.List;

@Data
public class FacePersonDTO {

    private Long personId;
    private String name;

    private String representativeCrop;

    private List<Long> mediaIds;

    private List<String> cropPaths;
}
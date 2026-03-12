package com.relive.project.dto;

import lombok.Data;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
public class VisionResponse {

    private String caption;

    private List<String> semantic_objects;

    private Integer face_count;

    private String time_of_day;

    private List<Double> embedding;

    @JsonProperty("date_taken")
    private String date_taken;
}

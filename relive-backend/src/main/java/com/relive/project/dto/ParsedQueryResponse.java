package com.relive.project.dto;

import lombok.Data;
import java.util.List;

@Data
public class ParsedQueryResponse {

    // Nouns — matched against media_objects table
    private List<String> objects;

    // Verbs — matched against captions (activities like eating, standing)
    private List<String> verbs;

    // Adjectives — matched against captions (colorful, outdoor, etc)
    private List<String> adjectives;

    // All terms combined — for broad caption search
    private List<String> all_terms;

    private Integer year;
    private Integer month;
    private String time_of_day;
    private Integer min_faces;

    // Place names detected by spaCy NER
    private List<String> location_hints;

    // Person names detected by spaCy NER
    private List<String> person_hints;
}
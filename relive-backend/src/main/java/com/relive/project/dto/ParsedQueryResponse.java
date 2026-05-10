package com.relive.project.dto;

import lombok.Data;
import java.util.List;

@Data
public class ParsedQueryResponse {

    // Common nouns — matched against media_objects table
    private List<String> objects;

    // Verbs — matched against captions
    private List<String> verbs;

    // Adjectives — matched against captions
    private List<String> adjectives;

    // All terms combined
    private List<String> all_terms;

    private Integer year;
    private Integer month;
    private String time_of_day;
    private Integer min_faces;

    // Place names from spaCy NER
    private List<String> location_hints;

    // Deprecated — backend now uses query_words for person detection
    private List<String> person_hints;

    // Terms to exclude
    private List<String> negated_terms;

    // Every meaningful word from the query, lowercased, stopwords removed.
    // The backend checks each of these directly against the face name DB
    // (case-insensitive, partial match). This is the reliable way to detect
    // person names regardless of capitalisation or spaCy NER accuracy.
    private List<String> query_words;
}
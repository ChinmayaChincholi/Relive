package com.relive.project.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class TermLeaf {
    private String domain;   // PERSON | LOCATION | DATE | TIME | VOCAB

    private String value;

    @JsonProperty("range_end")
    private String rangeEnd; // null unless this term is a range
}
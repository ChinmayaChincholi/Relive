package com.relive.project.dto;

import lombok.Data;
import java.util.List;

@Data
public class ParsedQueryResponse {

    private List<String> must_include;

    private List<String> must_exclude;

    private List<List<String>> any_of;

    private List<String> persons;

    private List<String> locations;

    private Integer year;
    private Integer month;
    private String time_of_day;
    private Integer min_people;

    private String free_text_semantic;
}
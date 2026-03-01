package com.relive.project.dto;

import lombok.Data;
import java.util.List;

@Data
public class ParsedQueryResponse {

    private List<String> objects;
    private Integer year;
}

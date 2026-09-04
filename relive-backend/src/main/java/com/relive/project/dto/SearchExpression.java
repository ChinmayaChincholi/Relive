package com.relive.project.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class SearchExpression {

    private TermLeaf term;

    private List<SearchExpression> must = new ArrayList<>();

    private List<SearchExpression> should = new ArrayList<>();

    private List<SearchExpression> mustNot = new ArrayList<>();

    public boolean isLeaf() {
        return term != null;
    }
}
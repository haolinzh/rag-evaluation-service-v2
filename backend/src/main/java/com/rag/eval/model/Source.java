package com.rag.eval.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Source {
    private String fileName;
    private String snippet;
    private String content;
    private double score;
    private String sourceType;
    private String url;
}

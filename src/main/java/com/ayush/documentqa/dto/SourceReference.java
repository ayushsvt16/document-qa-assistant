package com.ayush.documentqa.dto;

public record SourceReference(
        String documentTitle,
        Integer pageNumber,
        double similarityScore,
        String snippet
) {}

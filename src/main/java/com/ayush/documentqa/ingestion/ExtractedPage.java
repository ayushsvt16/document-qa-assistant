package com.ayush.documentqa.ingestion;

/**
 * Represents extracted text from a single page or section of a document.
 * pageNumber may be null for formats that don't have physical pages (TXT, DOCX).
 */
public record ExtractedPage(
        String text,
        Integer pageNumber
) {}

package com.ayush.documentqa.ingestion;

import java.io.InputStream;
import java.util.List;

/**
 * Strategy interface for document text extraction.
 * Each implementation handles a specific file format and preserves page/section metadata where possible.
 */
public interface TextExtractor {

    /** Returns true if this extractor handles the given content type */
    boolean supports(String contentType, String filename);

    /** Extracts text pages/sections from the document */
    List<ExtractedPage> extract(InputStream inputStream, String filename);
}

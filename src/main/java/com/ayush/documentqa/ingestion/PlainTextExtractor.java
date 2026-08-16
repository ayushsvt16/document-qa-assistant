package com.ayush.documentqa.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Plain text and Markdown extraction.
 * Uses logical section breaks (double newlines or markdown headers) as boundaries.
 * No physical page numbers are fabricated for these formats.
 */
@Component
public class PlainTextExtractor implements TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PlainTextExtractor.class);

    @Override
    public boolean supports(String contentType, String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return "text/plain".equals(contentType)
                || "text/markdown".equals(contentType)
                || lower.endsWith(".txt")
                || lower.endsWith(".md")
                || lower.endsWith(".markdown");
    }

    @Override
    public List<ExtractedPage> extract(InputStream inputStream, String filename) {
        try {
            String content = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Extracting text from {}: {} characters", filename, content.length());

            if (content.isBlank()) {
                return List.of();
            }

            // Split by double newlines or markdown heading boundaries
            List<ExtractedPage> sections = new ArrayList<>();
            String[] parts = content.split("(?m)(?=^#{1,3}\\s)|\\n{2,}");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    sections.add(new ExtractedPage(trimmed, null));
                }
            }

            if (sections.isEmpty()) {
                sections.add(new ExtractedPage(content.trim(), null));
            }

            return sections;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from: " + filename, e);
        }
    }
}

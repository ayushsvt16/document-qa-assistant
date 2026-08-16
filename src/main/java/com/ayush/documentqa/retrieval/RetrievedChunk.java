package com.ayush.documentqa.retrieval;

import java.util.UUID;

/**
 * Represents a chunk retrieved via vector similarity search.
 * Every field comes directly from the database query — nothing is fabricated.
 */
public record RetrievedChunk(
        UUID chunkId,
        String content,
        Integer pageNumber,
        UUID documentId,
        String documentTitle,
        String documentCategory,
        double similarityScore
) {}

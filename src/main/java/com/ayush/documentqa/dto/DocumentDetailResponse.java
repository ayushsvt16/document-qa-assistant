package com.ayush.documentqa.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentDetailResponse(
        UUID id,
        String title,
        String category,
        String filename,
        String status,
        String errorMessage,
        long sizeBytes,
        long chunkCount,
        Instant createdAt,
        Instant updatedAt
) {}

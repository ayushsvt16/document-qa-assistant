package com.ayush.documentqa.dto;

import java.util.UUID;

public record DocumentUploadResponse(
        UUID documentId,
        String status
) {}

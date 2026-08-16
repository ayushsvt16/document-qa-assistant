package com.ayush.documentqa.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        String title,
        Instant createdAt,
        List<MessageResponse> messages
) {
    public record MessageResponse(
            UUID id,
            String role,
            String content,
            Instant createdAt
    ) {}
}

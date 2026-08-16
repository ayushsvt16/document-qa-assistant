package com.ayush.documentqa.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        String conversationId,

        @NotBlank(message = "Question must not be blank")
        String question,

        String category
) {}

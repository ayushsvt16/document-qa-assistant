package com.ayush.documentqa.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatResponse(
        String answer,
        List<SourceReference> sources,
        UUID conversationId
) {}

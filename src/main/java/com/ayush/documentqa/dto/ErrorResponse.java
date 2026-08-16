package com.ayush.documentqa.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String correlationId
) {
    public static ErrorResponse of(int status, String error, String message, String path, String correlationId) {
        return new ErrorResponse(Instant.now(), status, error, message, path, correlationId);
    }
}

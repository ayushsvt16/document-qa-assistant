package com.ayush.documentqa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralized configuration for all tunable application parameters.
 * Every value is overridable via environment variables — no magic constants in business logic.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Chunking chunking,
        Retrieval retrieval,
        Conversation conversation,
        Ingestion ingestion,
        Resilience resilience
) {
    public record Chunking(int maxTokens, int overlapTokens) {}

    public record Retrieval(int topK, double similarityThreshold) {}

    public record Conversation(int maxHistoryTurns, int historyTokenBudget) {}

    public record Ingestion(int poolSize, int embeddingBatchSize) {}

    public record Resilience(int timeoutSeconds, int maxRetries, long retryDelayMs) {}
}

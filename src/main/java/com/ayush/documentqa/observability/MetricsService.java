package com.ayush.documentqa.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized metrics collection for retrieval latency, model latency, and token usage.
 */
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final AtomicLong totalInputTokens;
    private final AtomicLong totalOutputTokens;

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.totalInputTokens = meterRegistry.gauge("ai.tokens.input.total", new AtomicLong(0));
        this.totalOutputTokens = meterRegistry.gauge("ai.tokens.output.total", new AtomicLong(0));
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordRetrievalLatency(Timer.Sample sample) {
        sample.stop(meterRegistry.timer("retrieval.latency"));
    }

    public void recordModelLatency(Timer.Sample sample) {
        sample.stop(meterRegistry.timer("ai.model.latency"));
    }

    public void recordEmbeddingLatency(Timer.Sample sample) {
        sample.stop(meterRegistry.timer("ai.embedding.latency"));
    }

    public void recordTokenUsage(int inputTokens, int outputTokens) {
        if (totalInputTokens != null) totalInputTokens.addAndGet(inputTokens);
        if (totalOutputTokens != null) totalOutputTokens.addAndGet(outputTokens);
        meterRegistry.counter("ai.tokens.input").increment(inputTokens);
        meterRegistry.counter("ai.tokens.output").increment(outputTokens);
    }

    public void recordIngestion(String status) {
        meterRegistry.counter("ingestion.documents", "status", status).increment();
    }
}

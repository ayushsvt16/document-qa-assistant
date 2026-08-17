package com.ayush.documentqa.retrieval;

import com.ayush.documentqa.config.AppProperties;
import com.ayush.documentqa.observability.MetricsService;
import com.ayush.documentqa.repository.DocumentChunkRepository;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Handles vector similarity retrieval with tenant/category filtering.
 *
 * Critical: All filtering (tenant, category) happens inside the SQL query via
 * DocumentChunkRepository.findSimilarChunks — NOT after retrieval in Java.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final EmbeddingModel embeddingModel;
    private final DocumentChunkRepository chunkRepository;
    private final AppProperties appProperties;
    private final MetricsService metricsService;

    public RetrievalService(EmbeddingModel embeddingModel,
                            DocumentChunkRepository chunkRepository,
                            AppProperties appProperties,
                            MetricsService metricsService) {
        this.embeddingModel = embeddingModel;
        this.chunkRepository = chunkRepository;
        this.appProperties = appProperties;
        this.metricsService = metricsService;
    }

    /**
     * Retrieves similar chunks from the database, filtered by tenant and optionally by category.
     * Returns only chunks that meet the similarity threshold.
     *
     * @param tenantId  Mandatory tenant filter (applied in SQL)
     * @param question  The user's question
     * @param category  Optional category filter (applied in SQL)
     * @return Chunks meeting the similarity threshold, ordered by relevance
     */
    public List<RetrievedChunk> retrieve(String tenantId, String question, String category) {
        // 1. Generate query embedding
        Timer.Sample embeddingSample = metricsService.startTimer();
        float[] queryEmbedding = embeddingModel.embed(question);
        metricsService.recordEmbeddingLatency(embeddingSample);

        String embeddingStr = toVectorString(queryEmbedding);
        int topK = appProperties.retrieval().topK();
        double threshold = appProperties.retrieval().similarityThreshold();

        // 2. Execute pgvector cosine similarity search with tenant + category filter IN SQL
        Timer.Sample retrievalSample = metricsService.startTimer();
        List<Object[]> results = chunkRepository.findSimilarChunks(tenantId, embeddingStr, category, topK);
        metricsService.recordRetrievalLatency(retrievalSample);

        // 3. Map results and apply similarity threshold
        List<RetrievedChunk> chunks = results.stream()
                .map(this::mapToRetrievedChunk)
                .filter(chunk -> chunk.similarityScore() >= threshold)
                .collect(Collectors.toList());

        log.info("Retrieved {} chunks above threshold {} for tenant {} (query: '{}')",
                chunks.size(), threshold, tenantId,
                question.length() > 50 ? question.substring(0, 50) + "..." : question);

        return chunks;
    }

    private RetrievedChunk mapToRetrievedChunk(Object[] row) {
        return new RetrievedChunk(
                (UUID) row[0],         // chunk_id
                (String) row[1],       // content
                row[2] != null ? ((Number) row[2]).intValue() : null, // page_number
                (UUID) row[3],         // document_id
                (String) row[4],       // document_title
                (String) row[5],       // document_category
                ((Number) row[6]).doubleValue() // similarity_score
        );
    }

    private String toVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}

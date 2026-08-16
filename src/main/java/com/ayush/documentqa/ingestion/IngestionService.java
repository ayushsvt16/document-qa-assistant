package com.ayush.documentqa.ingestion;

import com.ayush.documentqa.config.AppProperties;
import com.ayush.documentqa.entity.Document;
import com.ayush.documentqa.entity.DocumentStatus;
import com.ayush.documentqa.observability.MetricsService;
import com.ayush.documentqa.repository.DocumentChunkRepository;
import com.ayush.documentqa.repository.DocumentRepository;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates the async document ingestion pipeline:
 * Extract → Chunk → Embed (batch) → Persist atomically → Mark READY/FAILED
 *
 * Transaction design:
 * - Embedding API calls happen OUTSIDE the transaction (they're slow external I/O)
 * - The final chunk+embedding persistence is INSIDE a transaction (atomic write)
 * - This avoids long-running transactions while ensuring atomicity of the data write
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final List<TextExtractor> extractors;
    private final TextChunker textChunker;
    private final EmbeddingModel embeddingModel;
    private final DocumentChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final AppProperties appProperties;
    private final MetricsService metricsService;

    public IngestionService(List<TextExtractor> extractors,
                            TextChunker textChunker,
                            EmbeddingModel embeddingModel,
                            DocumentChunkRepository chunkRepository,
                            DocumentRepository documentRepository,
                            AppProperties appProperties,
                            MetricsService metricsService) {
        this.extractors = extractors;
        this.textChunker = textChunker;
        this.embeddingModel = embeddingModel;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.appProperties = appProperties;
        this.metricsService = metricsService;
    }

    /**
     * Runs asynchronously on the bounded ingestion executor.
     * The caller (DocumentService) has already persisted the Document with PROCESSING status.
     */
    @Async("ingestionExecutor")
    public void ingestAsync(UUID documentId, String tenantId, byte[] fileContent,
                            String contentType, String filename) {
        log.info("Starting ingestion for document {} (tenant: {})", documentId, tenantId);

        try {
            // 1. Extract text
            TextExtractor extractor = findExtractor(contentType, filename);
            List<ExtractedPage> pages = extractor.extract(new ByteArrayInputStream(fileContent), filename);

            if (pages.isEmpty()) {
                markFailed(documentId, "No text could be extracted from the document");
                return;
            }

            log.info("Extracted {} pages/sections from document {}", pages.size(), documentId);

            // 2. Chunk text
            List<TextChunker.ChunkedText> chunks = textChunker.chunk(pages);
            if (chunks.isEmpty()) {
                markFailed(documentId, "Document produced no text chunks after processing");
                return;
            }

            log.info("Created {} chunks from document {}", chunks.size(), documentId);

            // 3. Generate embeddings in batches (OUTSIDE transaction — external I/O)
            List<float[]> allEmbeddings = generateEmbeddingsInBatches(chunks);

            // 4. Persist chunks + embeddings atomically (INSIDE transaction)
            persistChunksTransactionally(documentId, tenantId, chunks, allEmbeddings);

            // 5. Mark document as READY
            markReady(documentId);
            metricsService.recordIngestion("success");
            log.info("Ingestion complete for document {} — {} chunks stored", documentId, chunks.size());

        } catch (Exception e) {
            log.error("Ingestion failed for document {}: {}", documentId, e.getMessage(), e);
            markFailed(documentId, e.getMessage());
            metricsService.recordIngestion("failure");
        }
    }

    private List<float[]> generateEmbeddingsInBatches(List<TextChunker.ChunkedText> chunks) {
        Timer.Sample sample = metricsService.startTimer();
        int batchSize = appProperties.ingestion().embeddingBatchSize();
        List<float[]> allEmbeddings = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            List<String> batchTexts = chunks.subList(i, end).stream()
                    .map(TextChunker.ChunkedText::text)
                    .collect(Collectors.toList());

            log.debug("Generating embeddings for batch {}-{} of {}", i, end, chunks.size());
            List<float[]> batchEmbeddings = embeddingModel.embed(batchTexts);
            allEmbeddings.addAll(batchEmbeddings);
        }

        metricsService.recordEmbeddingLatency(sample);
        return allEmbeddings;
    }

    /**
     * Atomic write of all chunks + embeddings for a document.
     * If any chunk fails to persist, the entire batch is rolled back.
     */
    @Transactional
    public void persistChunksTransactionally(UUID documentId, String tenantId,
                                             List<TextChunker.ChunkedText> chunks,
                                             List<float[]> embeddings) {
        for (int i = 0; i < chunks.size(); i++) {
            TextChunker.ChunkedText chunk = chunks.get(i);
            float[] embedding = embeddings.get(i);
            String embeddingStr = toVectorString(embedding);

            chunkRepository.insertWithEmbedding(
                    UUID.randomUUID(),
                    documentId,
                    tenantId,
                    chunk.chunkIndex(),
                    chunk.text(),
                    chunk.pageNumber(),
                    chunk.tokenCount(),
                    embeddingStr
            );
        }
    }

    private TextExtractor findExtractor(String contentType, String filename) {
        return extractors.stream()
                .filter(e -> e.supports(contentType, filename))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No text extractor found for content type: " + contentType));
    }

    private void markReady(UUID documentId) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(DocumentStatus.READY);
            documentRepository.save(doc);
        });
    }

    private void markFailed(UUID documentId, String errorMessage) {
        documentRepository.findById(documentId).ifPresent(doc -> {
            doc.setStatus(DocumentStatus.FAILED);
            doc.setErrorMessage(errorMessage != null ? errorMessage.substring(0, Math.min(errorMessage.length(), 2000)) : "Unknown error");
            documentRepository.save(doc);
        });
    }

    /** Converts float[] to PostgreSQL vector literal "[0.1,0.2,0.3]" */
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

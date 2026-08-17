package com.ayush.documentqa.ingestion;

import com.ayush.documentqa.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ChunkPersistenceService {

    private final DocumentChunkRepository chunkRepository;

    public ChunkPersistenceService(DocumentChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Transactional
    public void persistChunksTransactionally(
            UUID documentId,
            String tenantId,
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
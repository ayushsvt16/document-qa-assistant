package com.ayush.documentqa.repository;

import com.ayush.documentqa.entity.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, UUID> {

    /**
     * Insert a chunk with its pgvector embedding using native SQL.
     * The embedding is passed as a string "[0.1,0.2,...]" and cast to vector type.
     */
    @Modifying
    @Query(value = """
            INSERT INTO document_chunks (id, document_id, tenant_id, chunk_index, content, page_number, token_count, embedding, created_at)
            VALUES (:id, :documentId, :tenantId, :chunkIndex, :content, :pageNumber, :tokenCount, cast(:embedding AS vector), NOW())
            """, nativeQuery = true)
    void insertWithEmbedding(
            @Param("id") UUID id,
            @Param("documentId") UUID documentId,
            @Param("tenantId") String tenantId,
            @Param("chunkIndex") int chunkIndex,
            @Param("content") String content,
            @Param("pageNumber") Integer pageNumber,
            @Param("tokenCount") int tokenCount,
            @Param("embedding") String embedding
    );

    /**
     * pgvector cosine similarity search with tenant + category filtering IN THE DATABASE.
     * This is the critical query — filtering happens in SQL, not Java.
     *
     * Returns: chunk_id, content, page_number, document_id, document_title, document_category, similarity_score
     */
    @Query(value = """
            SELECT dc.id AS chunk_id,
                   dc.content,
                   dc.page_number,
                   dc.document_id,
                   d.title AS document_title,
                   d.category AS document_category,
                   1 - (dc.embedding <=> cast(:queryEmbedding AS vector)) AS similarity_score
            FROM document_chunks dc
            JOIN documents d ON dc.document_id = d.id
            WHERE dc.tenant_id = :tenantId
              AND d.status = 'READY'
              AND (:category IS NULL OR d.category = :category)
            ORDER BY dc.embedding <=> cast(:queryEmbedding AS vector)
            LIMIT :topK
            """, nativeQuery = true)
    List<Object[]> findSimilarChunks(
            @Param("tenantId") String tenantId,
            @Param("queryEmbedding") String queryEmbedding,
            @Param("category") String category,
            @Param("topK") int topK
    );

    long countByDocumentId(UUID documentId);

    void deleteByDocumentId(UUID documentId);
}

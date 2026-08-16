package com.ayush.documentqa.repository;

import com.ayush.documentqa.entity.Document;
import com.ayush.documentqa.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    /** Tenant-scoped document lookup — never returns another tenant's document */
    Optional<Document> findByIdAndTenantId(UUID id, String tenantId);

    /** Paginated list of documents for a specific tenant */
    Page<Document> findByTenantId(String tenantId, Pageable pageable);

    /** Check for duplicate by tenant + content hash */
    Optional<Document> findByTenantIdAndContentHash(String tenantId, String contentHash);

    /** Count chunks for a document (used in list responses) */
    @Query("SELECT COUNT(c) FROM DocumentChunk c WHERE c.documentId = :documentId")
    long countChunksByDocumentId(@Param("documentId") UUID documentId);

    /** Delete only if owned by tenant — returns count for verification */
    long deleteByIdAndTenantId(UUID id, String tenantId);
}

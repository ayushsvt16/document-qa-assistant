package com.ayush.documentqa.service;

import com.ayush.documentqa.dto.DocumentDetailResponse;
import com.ayush.documentqa.dto.DocumentUploadResponse;
import com.ayush.documentqa.entity.Document;
import com.ayush.documentqa.entity.DocumentStatus;
import com.ayush.documentqa.exception.DocumentNotFoundException;
import com.ayush.documentqa.exception.DuplicateDocumentException;
import com.ayush.documentqa.ingestion.IngestionService;
import com.ayush.documentqa.repository.DocumentChunkRepository;
import com.ayush.documentqa.repository.DocumentRepository;
import com.ayush.documentqa.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024; // 20 MB
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".pdf", ".docx", ".txt", ".md", ".markdown");
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown"
    );

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final IngestionService ingestionService;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunkRepository chunkRepository,
                           IngestionService ingestionService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ingestionService = ingestionService;
    }

    public DocumentUploadResponse uploadDocument(MultipartFile file, String title, String category) {
        String tenantId = TenantContext.requireTenantId();

        // Validate file type
        String filename = file.getOriginalFilename();
        if (!isSupportedFile(file.getContentType(), filename)) {
            throw new UnsupportedFileTypeException("Unsupported file type. Supported: PDF, DOCX, TXT, Markdown");
        }

        // Validate file size
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileTooLargeException("File exceeds maximum size of 20MB");
        }

        try {
            byte[] fileContent = file.getBytes();

            // Calculate SHA-256
            String contentHash = sha256(fileContent);

            // Check for duplicate (application-level check; DB constraint is the final guard)
            documentRepository.findByTenantIdAndContentHash(tenantId, contentHash)
                    .ifPresent(existing -> {
                        throw new DuplicateDocumentException(
                                "Document already uploaded (id: " + existing.getId() + ", status: " + existing.getStatus() + ")");
                    });

            // Create document record with PROCESSING status
            Document document = new Document();
            document.setTenantId(tenantId);
            document.setTitle(title != null ? title : filename);
            document.setCategory(category);
            document.setFilename(filename);
            document.setContentHash(contentHash);
            document.setSizeBytes(file.getSize());
            document.setStatus(DocumentStatus.PROCESSING);

            document = documentRepository.save(document);
            log.info("Document created: {} (tenant: {}, hash: {})", document.getId(), tenantId, contentHash);

            // Trigger async ingestion
            ingestionService.ingestAsync(document.getId(), tenantId, fileContent,
                    file.getContentType(), filename);

            return new DocumentUploadResponse(document.getId(), DocumentStatus.PROCESSING.name());

        } catch (DuplicateDocumentException | UnsupportedFileTypeException | FileTooLargeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to process document upload", e);
        }
    }

    public Page<DocumentDetailResponse> listDocuments(Pageable pageable) {
        String tenantId = TenantContext.requireTenantId();
        return documentRepository.findByTenantId(tenantId, pageable)
                .map(this::toDetailResponse);
    }

    public DocumentDetailResponse getDocument(UUID documentId) {
        String tenantId = TenantContext.requireTenantId();
        Document doc = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));
        return toDetailResponse(doc);
    }

    @Transactional
    public void deleteDocument(UUID documentId) {
        String tenantId = TenantContext.requireTenantId();
        Document doc = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found: " + documentId));

        // Cascade delete: chunks (with embeddings) and associated message_sources are
        // handled by ON DELETE CASCADE in the database schema
        documentRepository.delete(doc);
        log.info("Document deleted: {} (tenant: {})", documentId, tenantId);
    }

    private DocumentDetailResponse toDetailResponse(Document doc) {
        long chunkCount = chunkRepository.countByDocumentId(doc.getId());
        return new DocumentDetailResponse(
                doc.getId(),
                doc.getTitle(),
                doc.getCategory(),
                doc.getFilename(),
                doc.getStatus().name(),
                doc.getErrorMessage(),
                doc.getSizeBytes(),
                chunkCount,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    private boolean isSupportedFile(String contentType, String filename) {
        if (contentType != null && SUPPORTED_CONTENT_TYPES.contains(contentType)) {
            return true;
        }
        if (filename != null) {
            String lower = filename.toLowerCase();
            return SUPPORTED_EXTENSIONS.stream().anyMatch(lower::endsWith);
        }
        return false;
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 computation failed", e);
        }
    }

    // Package-private exceptions for specific HTTP status handling
    public static class UnsupportedFileTypeException extends RuntimeException {
        public UnsupportedFileTypeException(String message) { super(message); }
    }

    public static class FileTooLargeException extends RuntimeException {
        public FileTooLargeException(String message) { super(message); }
    }
}

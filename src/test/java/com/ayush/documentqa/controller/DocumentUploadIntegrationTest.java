package com.ayush.documentqa.controller;

import com.ayush.documentqa.BaseIntegrationTest;
import com.ayush.documentqa.entity.Document;
import com.ayush.documentqa.entity.DocumentStatus;
import com.ayush.documentqa.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Document upload integration tests — validates HTTP-level behavior.
 * Uses Testcontainers for real PostgreSQL, mocks the embedding model (no API key needed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DocumentUploadIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentRepository documentRepository;

    @MockitoBean
    private EmbeddingModel embeddingModel;

    @Test
    void uploadValidTxtFile_returns202() throws Exception {
        when(embeddingModel.embed(anyString())).thenReturn(new float[1536]);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "Hello world content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .param("title", "Test Document")
                        .param("category", "FEES")
                        .header("X-Tenant-Id", "test-tenant"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    void uploadUnsupportedFileType_returns415() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.exe", "application/octet-stream", "binary content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header("X-Tenant-Id", "test-tenant"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void uploadOversizedFile_returns413() throws Exception {
        // Create a file > 20MB
        byte[] largeContent = new byte[21 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.txt", "text/plain", largeContent);

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header("X-Tenant-Id", "test-tenant"))
                .andExpect(status().isContentTooLarge());
    }

    @Test
    void uploadDuplicate_returns409() throws Exception {
        byte[] content = "Duplicate content test".getBytes();

        // First upload
        Document doc = new Document();
        doc.setTenantId("dup-tenant");
        doc.setFilename("dup.txt");
        doc.setContentHash("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e");
        doc.setSizeBytes(content.length);
        doc.setStatus(DocumentStatus.PROCESSING);
        doc.setTitle("Dup Doc");

        // Pre-calculate SHA-256 and save a doc with it
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        String hash = java.util.HexFormat.of().formatHex(digest.digest(content));
        doc.setContentHash(hash);
        documentRepository.save(doc);

        MockMultipartFile file = new MockMultipartFile(
                "file", "dup.txt", "text/plain", content);

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file)
                        .header("X-Tenant-Id", "dup-tenant"))
                .andExpect(status().isConflict());
    }

    @Test
    void missingTenantHeader_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes());

        mockMvc.perform(multipart("/api/v1/documents")
                        .file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteDocument_cascadesAndReturns204() throws Exception {
        Document doc = new Document();
        doc.setTenantId("del-tenant");
        doc.setTitle("To Delete");
        doc.setFilename("delete-me.txt");
        doc.setContentHash("del-hash-" + UUID.randomUUID());
        doc.setSizeBytes(100);
        doc.setStatus(DocumentStatus.READY);
        doc = documentRepository.save(doc);

        mockMvc.perform(delete("/api/v1/documents/" + doc.getId())
                        .header("X-Tenant-Id", "del-tenant"))
                .andExpect(status().isNoContent());

        // Verify document is gone
        mockMvc.perform(get("/api/v1/documents/" + doc.getId())
                        .header("X-Tenant-Id", "del-tenant"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantDelete_returns404() throws Exception {
        Document doc = new Document();
        doc.setTenantId("owner-tenant");
        doc.setTitle("Owner's Doc");
        doc.setFilename("owned.txt");
        doc.setContentHash("own-hash-" + UUID.randomUUID());
        doc.setSizeBytes(100);
        doc.setStatus(DocumentStatus.READY);
        doc = documentRepository.save(doc);

        // Attempt delete as different tenant
        mockMvc.perform(delete("/api/v1/documents/" + doc.getId())
                        .header("X-Tenant-Id", "attacker-tenant"))
                .andExpect(status().isNotFound());
    }
}

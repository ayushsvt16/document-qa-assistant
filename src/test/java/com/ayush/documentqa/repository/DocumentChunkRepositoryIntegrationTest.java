package com.ayush.documentqa.repository;

import com.ayush.documentqa.BaseIntegrationTest;
import com.ayush.documentqa.entity.Document;
import com.ayush.documentqa.entity.DocumentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for document chunk repository with real pgvector database.
 * Tests vector similarity search, tenant filtering, and category filtering.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DocumentChunkRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentChunkRepository chunkRepository;

    @Test
    void flywayMigrationsRun_schemaExists() {
        // If this test passes, Flyway migrations ran successfully
        // and the pgvector extension + tables were created
        Document doc = new Document();
        doc.setTenantId("tenant-flyway-test");
        doc.setTitle("Test Doc");
        doc.setFilename("test.pdf");
        doc.setContentHash("abc123");
        doc.setSizeBytes(100);
        doc.setStatus(DocumentStatus.PROCESSING);

        Document saved = documentRepository.save(doc);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void insertWithEmbedding_andFindSimilar() {
        // Create a test document
        Document doc = new Document();
        doc.setTenantId("tenant-a");
        doc.setTitle("Fee Policy");
        doc.setCategory("FEES");
        doc.setFilename("fees.pdf");
        doc.setContentHash("hash-" + UUID.randomUUID());
        doc.setSizeBytes(1000);
        doc.setStatus(DocumentStatus.READY);
        doc = documentRepository.save(doc);

        // Insert a chunk with a simple embedding (1536 dimensions)
        UUID chunkId = UUID.randomUUID();
        float[] embedding = new float[1536];
        embedding[0] = 0.5f;
        embedding[1] = 0.3f;
        embedding[2] = 0.8f;

        chunkRepository.insertWithEmbedding(
                chunkId, doc.getId(), "tenant-a", 0,
                "The annual fee is ₹50,000", 1, 6,
                toVectorString(embedding)
        );

        // Search with a similar embedding
        float[] queryEmbedding = new float[1536];
        queryEmbedding[0] = 0.5f;
        queryEmbedding[1] = 0.3f;
        queryEmbedding[2] = 0.8f;

        List<Object[]> results = chunkRepository.findSimilarChunks(
                "tenant-a", toVectorString(queryEmbedding), null, 5);

        assertThat(results).isNotEmpty();
        assertThat((String) results.get(0)[1]).contains("₹50,000");
    }

    @Test
    void tenantFiltering_inDatabase() {
        // Create documents for two different tenants
        Document docA = createDoc("tenant-a", "Doc A", "FEES", "hash-a-" + UUID.randomUUID());
        Document docB = createDoc("tenant-b", "Doc B", "FEES", "hash-b-" + UUID.randomUUID());

        float[] embedding = new float[1536];
        embedding[0] = 1.0f;

        chunkRepository.insertWithEmbedding(
                UUID.randomUUID(), docA.getId(), "tenant-a", 0,
                "Tenant A content", 1, 3, toVectorString(embedding));
        chunkRepository.insertWithEmbedding(
                UUID.randomUUID(), docB.getId(), "tenant-b", 0,
                "Tenant B content", 1, 3, toVectorString(embedding));

        // Search as tenant-a — should NOT see tenant-b's data
        List<Object[]> results = chunkRepository.findSimilarChunks(
                "tenant-a", toVectorString(embedding), null, 10);

        assertThat(results).allSatisfy(row -> {
            String content = (String) row[1];
            assertThat(content).doesNotContain("Tenant B");
        });
    }

    @Test
    void categoryFiltering_inDatabase() {
        Document feeDoc = createDoc("tenant-cat", "Fee Doc", "FEES", "hash-fee-" + UUID.randomUUID());
        Document hrDoc = createDoc("tenant-cat", "HR Doc", "HR", "hash-hr-" + UUID.randomUUID());

        float[] embedding = new float[1536];
        embedding[0] = 1.0f;

        chunkRepository.insertWithEmbedding(
                UUID.randomUUID(), feeDoc.getId(), "tenant-cat", 0,
                "Fee information", 1, 2, toVectorString(embedding));
        chunkRepository.insertWithEmbedding(
                UUID.randomUUID(), hrDoc.getId(), "tenant-cat", 0,
                "HR information", 1, 2, toVectorString(embedding));

        // Search with category filter — should only get FEES
        List<Object[]> results = chunkRepository.findSimilarChunks(
                "tenant-cat", toVectorString(embedding), "FEES", 10);

        assertThat(results).allSatisfy(row -> {
            String category = (String) row[5]; // document_category column
            assertThat(category).isEqualTo("FEES");
        });
    }

    private Document createDoc(String tenantId, String title, String category, String hash) {
        Document doc = new Document();
        doc.setTenantId(tenantId);
        doc.setTitle(title);
        doc.setCategory(category);
        doc.setFilename(title.toLowerCase().replace(" ", "-") + ".pdf");
        doc.setContentHash(hash);
        doc.setSizeBytes(1000);
        doc.setStatus(DocumentStatus.READY);
        return documentRepository.save(doc);
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

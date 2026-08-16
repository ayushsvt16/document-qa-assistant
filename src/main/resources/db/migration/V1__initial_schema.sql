-- V1: Initial schema for Document Q&A Assistant
-- pgvector extension, all core tables, indexes, and constraints

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================
-- Documents
-- ============================================================
CREATE TABLE documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100)  NOT NULL,
    title           VARCHAR(500),
    category        VARCHAR(100),
    filename        VARCHAR(500)  NOT NULL,
    content_hash    VARCHAR(64)   NOT NULL,
    size_bytes      BIGINT        NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PROCESSING',
    error_message   TEXT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_tenant_content_hash UNIQUE (tenant_id, content_hash),
    CONSTRAINT chk_status CHECK (status IN ('PROCESSING', 'READY', 'FAILED'))
);

CREATE INDEX idx_documents_tenant_id ON documents(tenant_id);
CREATE INDEX idx_documents_status    ON documents(status);
CREATE INDEX idx_documents_category  ON documents(category);

-- ============================================================
-- Document Chunks (with pgvector embedding)
-- ============================================================
CREATE TABLE document_chunks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id     UUID          NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    tenant_id       VARCHAR(100)  NOT NULL,
    chunk_index     INTEGER       NOT NULL,
    content         TEXT          NOT NULL,
    page_number     INTEGER,
    token_count     INTEGER       NOT NULL,
    embedding       vector(1536),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chunks_document_id ON document_chunks(document_id);
CREATE INDEX idx_chunks_tenant_id   ON document_chunks(tenant_id);

-- HNSW index for cosine similarity — critical for retrieval performance
CREATE INDEX idx_chunks_embedding ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- ============================================================
-- Conversations
-- ============================================================
CREATE TABLE conversations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(100)  NOT NULL,
    title           VARCHAR(500),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    last_message_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_conversations_tenant_id ON conversations(tenant_id);

-- ============================================================
-- Messages
-- ============================================================
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID          NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role            VARCHAR(20)   NOT NULL,
    content         TEXT          NOT NULL,
    token_count     INTEGER,
    model           VARCHAR(100),
    latency_ms      BIGINT,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_messages_conversation_id ON messages(conversation_id);
CREATE INDEX idx_messages_created_at      ON messages(created_at);

-- ============================================================
-- Message Sources (links answers to retrieved chunks)
-- ============================================================
CREATE TABLE message_sources (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id       UUID             NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
    chunk_id         UUID             NOT NULL REFERENCES document_chunks(id) ON DELETE CASCADE,
    similarity_score DOUBLE PRECISION NOT NULL
);

CREATE INDEX idx_message_sources_message_id ON message_sources(message_id);

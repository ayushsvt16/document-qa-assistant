-- Change embedding dimension from OpenAI text-embedding-3-small (1536)
-- to Ollama mxbai-embed-large (1024).

DROP INDEX IF EXISTS idx_chunks_embedding;

ALTER TABLE document_chunks
    DROP COLUMN embedding;

ALTER TABLE document_chunks
    ADD COLUMN embedding vector(1024);

CREATE INDEX idx_chunks_embedding ON document_chunks
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
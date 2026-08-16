# Document Q&A Assistant

A multi-tenant, RAG-based document question-answering backend built with **Spring Boot 4.0.7** and **Spring AI 2.0.0**.

Upload documents (PDF, DOCX, TXT, Markdown) → the system extracts, chunks, and embeds the text → then answers questions grounded exclusively in the uploaded content using retrieved context.

## Architecture

```
┌─────────────────┐    ┌────────────────────────────────────────────────────────┐
│   HTTP Client    │───▶│                 Spring Boot App                        │
│  (curl, Postman, │    │                                                        │
│   Swagger UI)    │    │  ┌──────────┐  ┌───────────────┐  ┌───────────────┐   │
└─────────────────┘    │  │Controller│──│ DocumentSvc   │──│ IngestionSvc  │   │
                       │  │  Layer   │  │ ChatService   │  │ (async)       │   │
                       │  └──────────┘  │ ConversationSvc│  │               │   │
                       │                └───────┬───────┘  └──────┬────────┘   │
                       │                        │                  │            │
                       │                ┌───────▼───────┐  ┌──────▼────────┐   │
                       │                │ RetrievalSvc  │  │  TextChunker  │   │
                       │                │ (pgvector     │  │  Extractors   │   │
                       │                │  cosine sim)  │  │  (PDF,DOCX,TXT)│  │
                       │                └───────┬───────┘  └──────┬────────┘   │
                       │                        │                  │            │
                       │                ┌───────▼──────────────────▼────────┐   │
                       │                │     Spring AI (EmbeddingModel     │   │
                       │                │     + ChatClient)                 │   │
                       │                └──────────────────────────────────┘   │
                       └────────────────────────────┬──────────────────────────┘
                                                    │
                                     ┌──────────────▼──────────────┐
                                     │  PostgreSQL 16 + pgvector   │
                                     │  (HNSW index, Flyway)       │
                                     └─────────────────────────────┘
```

## Quick Start

### Prerequisites
- Docker Desktop (with Docker Compose)
- An OpenAI API key (or compatible provider)

### Run

```bash
# 1. Clone
git clone https://github.com/ayushsvt16/document-qa-assistant.git
cd document-qa-assistant

# 2. Set your API key
export OPENAI_API_KEY=sk-your-key-here

# 3. Start everything
docker compose up -d

# 4. Wait ~30 seconds for startup, then verify
curl http://localhost:8080/actuator/health
```

### 5-Minute Demo

```bash
# Upload a document (returns 202 Accepted — ingestion is async)
curl -X POST http://localhost:8080/api/v1/documents \
  -H "X-Tenant-Id: school-alpha" \
  -F "file=@demo/fee-policy.md" \
  -F "title=Fee Policy" \
  -F "category=FEES"

# Wait ~10s for ingestion, then ask a question
curl -X POST http://localhost:8080/api/v1/chat \
  -H "X-Tenant-Id: school-alpha" \
  -H "Content-Type: application/json" \
  -d '{"question": "What is the tuition fee for Class 6?"}'
```

Or run the full automated demo: `bash demo/demo.sh`

## API Endpoints

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `POST` | `/api/v1/documents` | Upload document (multipart) | `202 Accepted` |
| `GET` | `/api/v1/documents` | List documents (paginated) | `200 OK` |
| `GET` | `/api/v1/documents/{id}` | Get document details + status | `200 OK` |
| `DELETE` | `/api/v1/documents/{id}` | Delete document + chunks | `204 No Content` |
| `POST` | `/api/v1/chat` | Ask question (sync) | `200 OK` |
| `POST` | `/api/v1/chat/stream` | Ask question (SSE streaming) | `text/event-stream` |
| `GET` | `/api/v1/conversations/{id}` | Get conversation history | `200 OK` |

All `/api/**` endpoints require the `X-Tenant-Id` header.

**Swagger UI**: http://localhost:8080/swagger-ui.html

## Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Spring Boot | 4.0.7 |
| AI/RAG | Spring AI | 2.0.0 |
| LLM | OpenAI GPT-4o-mini | (configurable) |
| Embeddings | text-embedding-3-small | 1536 dimensions |
| Database | PostgreSQL + pgvector | 16 |
| Migrations | Flyway | (managed by Boot) |
| API Docs | springdoc-openapi | 2.9.0 |
| Resilience | Resilience4j | 2.4.0 |
| PDF | Apache PDFBox | 3.0.4 |
| DOCX | Apache POI | 5.3.0 |
| Metrics | Micrometer + Prometheus | (managed by Boot) |
| Tests | JUnit 5, Testcontainers | 1.21.4 |
| Container | Docker (multi-stage) | - |

## Database Schema

5 tables with tenant-scoped data isolation:

```
documents           → Core document metadata (status: PROCESSING → READY → FAILED)
document_chunks     → Text chunks with pgvector embedding (1536-dim, HNSW index)
conversations       → Chat sessions
messages            → Conversation history (user + assistant turns)
message_sources     → Links answers to retrieved chunks (provenance tracking)
```

Deduplication: `UNIQUE(tenant_id, content_hash)` prevents re-uploading identical content.

## Key Design Decisions

### Retrieval Algorithm
- **Vector search**: pgvector cosine similarity with HNSW index (`m=16, ef_construction=64`)
- **Top-K**: 5 chunks per query (configurable via `RETRIEVAL_TOP_K`)
- **Filtering**: Tenant + category filtering happens **inside the SQL query** (not in Java post-query)
- **Similarity threshold**: 0.70 default (configurable via `SIMILARITY_THRESHOLD`)

### Chunking
- **Strategy**: Token-aware splitting with configurable overlap (800 tokens / 100 overlap default)
- **Token estimation**: ~4 characters per token (approximate but fast, no provider dependency)
- **Sentence boundary**: Chunks prefer breaking at sentence boundaries to avoid mid-sentence splits
- **Page tracking**: PDF page numbers are preserved per chunk; DOCX/TXT sections don't have physical pages

### Refusal Mechanism
When no retrieved chunks meet the similarity threshold:
1. The **LLM is NOT called** (saves cost and latency)
2. A fixed refusal message is returned
3. This is verified by the `RefusalTest` unit test (proves `verifyNoInteractions(aiService)`)

### Conversation Memory
- Token-budgeted history: max 6 turns, 2000 token budget
- Recent messages are loaded newest-first, trimmed by budget, then reversed to chronological order
- History is injected into the prompt as conversation context

### Tenant Isolation
- `X-Tenant-Id` header required on all `/api/**` endpoints
- `TenantInterceptor` → `TenantContext` (ThreadLocal) → all repositories filter by tenant
- Database constraints: `UNIQUE(tenant_id, content_hash)`, tenant_id indexed on all tables
- Cross-tenant access is impossible — every query includes `WHERE tenant_id = ?`

### Streaming (SSE)
- `POST /api/v1/chat/stream` returns `text/event-stream`
- Events: `token` (incremental text) → `sources` (citations JSON) → `done` (conversation ID)
- Client disconnect detection via `SseEmitter` callbacks

### Async Ingestion
- Document upload returns `202 Accepted` immediately
- Ingestion runs on a bounded thread pool (`ingestion-*` threads)
- Pipeline: Extract → Chunk → Embed (batch, outside txn) → Persist (atomic txn) → Mark READY/FAILED
- Transaction design prevents long-running transactions during embedding API calls

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OPENAI_API_KEY` | (required) | OpenAI API key |
| `AI_CHAT_MODEL` | `gpt-4o-mini` | Chat model |
| `AI_EMBEDDING_MODEL` | `text-embedding-3-small` | Embedding model |
| `RETRIEVAL_TOP_K` | `5` | Max chunks retrieved |
| `SIMILARITY_THRESHOLD` | `0.70` | Min cosine similarity |
| `CHUNK_MAX_TOKENS` | `800` | Max tokens per chunk |
| `CHUNK_OVERLAP_TOKENS` | `100` | Overlap between chunks |
| `MAX_HISTORY_TURNS` | `6` | Max conversation turns in context |
| `HISTORY_TOKEN_BUDGET` | `2000` | Max tokens for conversation history |
| `INGESTION_POOL_SIZE` | `5` | Async ingestion threads |

See `.env.example` for the full list.

## Testing

```bash
# Unit tests only (no Docker needed)
mvn test -pl . -Dtest="TextChunkerTest,RefusalTest,ProviderFailureTest"

# Integration tests (requires Docker for Testcontainers)
mvn verify

# Coverage report
mvn test jacoco:report
# Open target/site/jacoco/index.html
```

### Test Coverage
- **TextChunkerTest**: Empty/null/blank input, single chunk, multi-chunk, overlap, page metadata, token estimation
- **RefusalTest**: Proves LLM is NOT called when no chunks meet threshold
- **DocumentUploadIntegrationTest**: Valid upload (202), unsupported type (415), oversized (413), duplicate (409), missing tenant (400), delete (204), cross-tenant (404)
- **DocumentChunkRepositoryIntegrationTest**: Schema validation, vector similarity search, tenant isolation, category filtering — all against real pgvector
- **ProviderFailureTest**: Validates clean exception wrapping → 503

## Docker Commands

```bash
# Start everything
docker compose up -d

# View logs
docker compose logs -f app

# Rebuild after code changes
docker compose build app && docker compose up -d app

# Stop
docker compose down

# Stop and remove data
docker compose down -v
```

## Known Limitations

1. **Token estimation is approximate** (~4 chars/token). Exact tokenization would require the model's tokenizer, adding a provider dependency.
2. **DOCX page numbers are unavailable** — DOCX format doesn't expose physical page numbers (pagination depends on the rendering engine).
3. **No file storage** — document content is not stored after ingestion; only chunks + embeddings are persisted. Re-upload is required if the source file is needed again.
4. **Single-node** — no distributed processing; the bounded thread pool handles ingestion concurrency on a single instance.
5. **No authentication** — tenant isolation is via header, not JWT/OAuth. Suitable for internal services behind an API gateway.

## Project Structure

```
src/main/java/com/ayush/documentqa/
├── DocumentQaApplication.java
├── ai/                  # AiService, PromptBuilder
├── config/              # AppProperties, AsyncConfig, AiConfig, WebMvcConfig, OpenApiConfig
├── controller/          # DocumentController, ChatController, ConversationController
├── dto/                 # Request/Response records
├── entity/              # JPA entities
├── exception/           # Custom exceptions + GlobalExceptionHandler
├── ingestion/           # TextExtractors, TextChunker, IngestionService
├── observability/       # CorrelationIdFilter, MetricsService
├── repository/          # JPA repositories (incl. native pgvector queries)
├── retrieval/           # RetrievalService, RetrievedChunk
├── security/            # TenantContext, TenantInterceptor
└── service/             # DocumentService, ChatService, ConversationService
```

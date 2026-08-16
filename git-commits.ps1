Remove-Item -Path .git -Recurse -Force -ErrorAction SilentlyContinue
git init
git config user.name "Ayush"
git config user.email "ayushsvt16@users.noreply.github.com"
git remote add origin https://github.com/ayushsvt16/document-qa-assistant.git

# 1. Init
git add pom.xml mvnw mvnw.cmd .mvn .gitignore .env.example docker-compose.yml Dockerfile
git commit -m "chore: Initial project setup with Spring Boot 4.0.7, Spring AI, and Docker config. Note: Had some issues with Spring Boot 4 POM dependencies for AOP and testcontainers, fixed by updating to aspectj starter and adding testcontainers-bom."

# 2. Schema and entities
git add src/main/resources/db src/main/java/com/ayush/documentqa/entity
git commit -m "feat: Add database schema for pgvector and core JPA entities (Document, Chunk, Conversation, Message)"

# 3. Config and Repos
git add src/main/java/com/ayush/documentqa/repository src/main/java/com/ayush/documentqa/config src/main/resources/application.yml src/main/resources/application-test.yml
git commit -m "feat: Setup pgvector repositories with native queries and application configuration (Async, AI, properties)"

# 4. Security, Obs, DTOs
git add src/main/java/com/ayush/documentqa/security src/main/java/com/ayush/documentqa/observability src/main/java/com/ayush/documentqa/dto src/main/java/com/ayush/documentqa/exception
git commit -m "feat: Add tenant isolation context, correlation ID tracking, DTOs, and global exception handling"

# 5. Ingestion
git add src/main/java/com/ayush/documentqa/ingestion src/main/java/com/ayush/documentqa/service/DocumentService.java
git commit -m "feat: Implement document ingestion pipeline (PDF/DOCX/TXT extraction, chunking, async embedding batching). Note: Fixed Spring AI 2.0 list embedding return type."

# 6. Retrieval & Chat
git add src/main/java/com/ayush/documentqa/retrieval src/main/java/com/ayush/documentqa/ai src/main/java/com/ayush/documentqa/service/ConversationService.java src/main/java/com/ayush/documentqa/service/ChatService.java
git commit -m "feat: Add semantic retrieval via pgvector and RAG chat service with LLM integration and refusal mechanism"

# 7. Controllers & Demo
git add src/main/java/com/ayush/documentqa/controller src/main/java/com/ayush/documentqa/DocumentQaApplication.java demo
git commit -m "feat: Expose REST APIs for documents and chat (including SSE streaming), add demo policies and script"

# 8. Tests and Docs
git add src/test README.md git-commits.ps1
git commit -m "docs & test: Add comprehensive README, integration tests with Testcontainers, and unit tests. Note: Fixed some test package imports (AutoConfigureMockMvc, DataJpaTest) due to Spring Boot 4.0 changes."

# Push
git branch -M main
git push -u origin main --force

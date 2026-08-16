package com.ayush.documentqa.service;

import com.ayush.documentqa.ai.AiService;
import com.ayush.documentqa.ai.PromptBuilder;
import com.ayush.documentqa.dto.ChatRequest;
import com.ayush.documentqa.dto.ChatResponse;
import com.ayush.documentqa.dto.SourceReference;
import com.ayush.documentqa.entity.Conversation;
import com.ayush.documentqa.entity.Message;
import com.ayush.documentqa.entity.MessageSource;
import com.ayush.documentqa.repository.MessageSourceRepository;
import com.ayush.documentqa.retrieval.RetrievalService;
import com.ayush.documentqa.retrieval.RetrievedChunk;
import com.ayush.documentqa.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the chat pipeline:
 * 1. Validate → 2. Resolve tenant/conversation → 3. Retrieve → 4. Threshold check →
 * 5. Refuse OR call LLM → 6. Persist turn + sources → 7. Return answer + citations
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    public static final String REFUSAL_MESSAGE =
            "I'm sorry, but I couldn't find sufficient information in the available documents to answer your question. " +
            "Please try rephrasing your question or ensure the relevant documents have been uploaded.";

    private final RetrievalService retrievalService;
    private final AiService aiService;
    private final PromptBuilder promptBuilder;
    private final ConversationService conversationService;
    private final MessageSourceRepository messageSourceRepository;

    public ChatService(RetrievalService retrievalService,
                       AiService aiService,
                       PromptBuilder promptBuilder,
                       ConversationService conversationService,
                       MessageSourceRepository messageSourceRepository) {
        this.retrievalService = retrievalService;
        this.aiService = aiService;
        this.promptBuilder = promptBuilder;
        this.conversationService = conversationService;
        this.messageSourceRepository = messageSourceRepository;
    }

    @Transactional
    public ChatResponse chat(ChatRequest request) {
        String tenantId = TenantContext.requireTenantId();

        // 1. Resolve or create conversation
        Conversation conversation = conversationService.resolveOrCreate(request.conversationId(), tenantId);
        UUID conversationId = conversation.getId();

        // 2. Save user message
        conversationService.saveMessage(conversationId, "user", request.question(), null, null);

        // 3. Retrieve relevant chunks (tenant + category filtering happens in SQL)
        List<RetrievedChunk> chunks = retrievalService.retrieve(tenantId, request.question(), request.category());

        // 4. CRITICAL: Refuse if nothing meets the similarity threshold
        //    The LLM is NOT called when there's insufficient grounding.
        if (chunks.isEmpty()) {
            log.info("Refusal: No chunks above threshold for tenant {} question '{}'",
                    tenantId, truncate(request.question()));

            conversationService.saveMessage(conversationId, "assistant", REFUSAL_MESSAGE, null, null);
            return new ChatResponse(REFUSAL_MESSAGE, List.of(), conversationId);
        }

        // 5. Load conversation history (token-budgeted)
        List<Message> history = conversationService.getRecentHistory(conversationId);

        // 6. Build grounded prompt
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userMessage = promptBuilder.buildUserMessage(request.question(), chunks, history);

        // 7. Call LLM
        long startMs = System.currentTimeMillis();
        String answer = aiService.call(systemPrompt, userMessage);
        long latencyMs = System.currentTimeMillis() - startMs;

        // 8. Save assistant message
        Message assistantMessage = conversationService.saveMessage(
                conversationId, "assistant", answer, null, latencyMs);

        // 9. Save source references
        List<SourceReference> sources = chunks.stream()
                .map(chunk -> {
                    MessageSource ms = new MessageSource();
                    ms.setMessageId(assistantMessage.getId());
                    ms.setChunkId(chunk.chunkId());
                    ms.setSimilarityScore(chunk.similarityScore());
                    messageSourceRepository.save(ms);

                    return new SourceReference(
                            chunk.documentTitle(),
                            chunk.pageNumber(),
                            Math.round(chunk.similarityScore() * 100.0) / 100.0,
                            truncateSnippet(chunk.content())
                    );
                })
                .toList();

        return new ChatResponse(answer, sources, conversationId);
    }

    /**
     * Streaming chat — returns sources as final event after all tokens are streamed.
     */
    public StreamingChatResult streamChat(ChatRequest request) {
        String tenantId = TenantContext.requireTenantId();

        Conversation conversation = conversationService.resolveOrCreate(request.conversationId(), tenantId);
        UUID conversationId = conversation.getId();

        conversationService.saveMessage(conversationId, "user", request.question(), null, null);

        List<RetrievedChunk> chunks = retrievalService.retrieve(tenantId, request.question(), request.category());

        if (chunks.isEmpty()) {
            log.info("Refusal (stream): No chunks above threshold for tenant {}", tenantId);
            conversationService.saveMessage(conversationId, "assistant", REFUSAL_MESSAGE, null, null);
            return new StreamingChatResult(
                    Flux.just(REFUSAL_MESSAGE),
                    List.of(),
                    conversationId,
                    true
            );
        }

        List<Message> history = conversationService.getRecentHistory(conversationId);
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userMessage = promptBuilder.buildUserMessage(request.question(), chunks, history);

        Flux<String> tokenStream = aiService.stream(systemPrompt, userMessage);

        List<SourceReference> sources = chunks.stream()
                .map(chunk -> new SourceReference(
                        chunk.documentTitle(),
                        chunk.pageNumber(),
                        Math.round(chunk.similarityScore() * 100.0) / 100.0,
                        truncateSnippet(chunk.content())
                ))
                .toList();

        return new StreamingChatResult(tokenStream, sources, conversationId, false);
    }

    /** Saves the streamed assistant response after streaming completes */
    @Transactional
    public void saveStreamedResponse(UUID conversationId, String fullAnswer,
                                     List<RetrievedChunk> chunks, Long latencyMs) {
        Message msg = conversationService.saveMessage(conversationId, "assistant", fullAnswer, null, latencyMs);
        for (RetrievedChunk chunk : chunks) {
            MessageSource ms = new MessageSource();
            ms.setMessageId(msg.getId());
            ms.setChunkId(chunk.chunkId());
            ms.setSimilarityScore(chunk.similarityScore());
            messageSourceRepository.save(ms);
        }
    }

    private String truncate(String text) {
        return text.length() > 80 ? text.substring(0, 80) + "..." : text;
    }

    private String truncateSnippet(String content) {
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }

    public record StreamingChatResult(
            Flux<String> tokens,
            List<SourceReference> sources,
            UUID conversationId,
            boolean isRefusal
    ) {}
}

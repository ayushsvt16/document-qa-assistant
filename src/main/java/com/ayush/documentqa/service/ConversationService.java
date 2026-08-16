package com.ayush.documentqa.service;

import com.ayush.documentqa.config.AppProperties;
import com.ayush.documentqa.dto.ConversationResponse;
import com.ayush.documentqa.entity.Conversation;
import com.ayush.documentqa.entity.Message;
import com.ayush.documentqa.exception.ConversationNotFoundException;
import com.ayush.documentqa.ingestion.TextChunker;
import com.ayush.documentqa.repository.ConversationRepository;
import com.ayush.documentqa.repository.MessageRepository;
import com.ayush.documentqa.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AppProperties appProperties;

    public ConversationService(ConversationRepository conversationRepository,
                               MessageRepository messageRepository,
                               AppProperties appProperties) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.appProperties = appProperties;
    }

    @Transactional
    public Conversation resolveOrCreate(String conversationIdStr, String tenantId) {
        if (conversationIdStr != null && !conversationIdStr.isBlank()) {
            UUID convId = UUID.fromString(conversationIdStr);
            return conversationRepository.findByIdAndTenantId(convId, tenantId)
                    .orElseThrow(() -> new ConversationNotFoundException(
                            "Conversation not found: " + conversationIdStr));
        }

        // Create new conversation
        Conversation conversation = new Conversation();
        conversation.setTenantId(tenantId);
        conversation.setTitle("New conversation");
        return conversationRepository.save(conversation);
    }

    /**
     * Loads recent conversation history within token budget.
     * We take the most recent messages up to maxHistoryTurns, then trim by token budget.
     * This prevents sending excessive context to the LLM.
     */
    public List<Message> getRecentHistory(UUID conversationId) {
        int maxTurns = appProperties.conversation().maxHistoryTurns();
        int tokenBudget = appProperties.conversation().historyTokenBudget();

        // Get recent messages (newest first)
        List<Message> recentMessages = messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId);
        if (recentMessages.isEmpty()) return List.of();

        // Take at most maxTurns messages
        List<Message> selected = new ArrayList<>();
        int tokenSum = 0;
        int count = 0;

        for (Message msg : recentMessages) {
            if (count >= maxTurns) break;
            int msgTokens = msg.getTokenCount() != null ? msg.getTokenCount() : TextChunker.estimateTokens(msg.getContent());
            if (tokenSum + msgTokens > tokenBudget && !selected.isEmpty()) {
                break; // Would exceed token budget
            }
            selected.add(msg);
            tokenSum += msgTokens;
            count++;
        }

        // Reverse to chronological order
        Collections.reverse(selected);
        return selected;
    }

    @Transactional
    public Message saveMessage(UUID conversationId, String role, String content,
                               String model, Long latencyMs) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setTokenCount(TextChunker.estimateTokens(content));
        message.setModel(model);
        message.setLatencyMs(latencyMs);

        Message saved = messageRepository.save(message);

        // Update conversation timestamp
        conversationRepository.findById(conversationId).ifPresent(conv -> {
            conv.setLastMessageAt(Instant.now());
            conversationRepository.save(conv);
        });

        return saved;
    }

    public ConversationResponse getConversation(UUID conversationId) {
        String tenantId = TenantContext.requireTenantId();
        Conversation conv = conversationRepository.findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ConversationNotFoundException("Conversation not found: " + conversationId));

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        List<ConversationResponse.MessageResponse> messageResponses = messages.stream()
                .map(m -> new ConversationResponse.MessageResponse(m.getId(), m.getRole(), m.getContent(), m.getCreatedAt()))
                .toList();

        return new ConversationResponse(conv.getId(), conv.getTitle(), conv.getCreatedAt(), messageResponses);
    }
}

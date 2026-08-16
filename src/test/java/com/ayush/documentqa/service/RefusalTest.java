package com.ayush.documentqa.service;

import com.ayush.documentqa.ai.AiService;
import com.ayush.documentqa.ai.PromptBuilder;
import com.ayush.documentqa.dto.ChatRequest;
import com.ayush.documentqa.dto.ChatResponse;
import com.ayush.documentqa.entity.Conversation;
import com.ayush.documentqa.repository.MessageSourceRepository;
import com.ayush.documentqa.retrieval.RetrievalService;
import com.ayush.documentqa.retrieval.RetrievedChunk;
import com.ayush.documentqa.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MANDATORY TEST: Proves that the LLM is NOT called when retrieval returns
 * no chunks above the similarity threshold.
 *
 * This is one of the most critical evaluation criteria.
 */
@ExtendWith(MockitoExtension.class)
class RefusalTest {

    @Mock private RetrievalService retrievalService;
    @Mock private AiService aiService;
    @Mock private PromptBuilder promptBuilder;
    @Mock private ConversationService conversationService;
    @Mock private MessageSourceRepository messageSourceRepository;

    @InjectMocks
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("test-tenant");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void whenNoChunksAboveThreshold_refusesWithoutCallingLLM() {
        // Arrange: retrieval returns EMPTY list (nothing above threshold)
        Conversation mockConv = new Conversation();
        mockConv.setId(UUID.randomUUID());
        mockConv.setTenantId("test-tenant");
        when(conversationService.resolveOrCreate(any(), eq("test-tenant"))).thenReturn(mockConv);
        when(conversationService.saveMessage(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    var msg = new com.ayush.documentqa.entity.Message();
                    msg.setId(UUID.randomUUID());
                    return msg;
                });
        when(retrievalService.retrieve(eq("test-tenant"), anyString(), any()))
                .thenReturn(Collections.emptyList());

        ChatRequest request = new ChatRequest(null, "What is the meaning of life?", null);

        // Act
        ChatResponse response = chatService.chat(request);

        // Assert: Refusal response returned
        assertThat(response.answer()).isEqualTo(ChatService.REFUSAL_MESSAGE);
        assertThat(response.sources()).isEmpty();

        // CRITICAL ASSERTION: LLM was NEVER called
        verifyNoInteractions(aiService);
    }

    @Test
    void whenChunksExist_callsLLM() {
        // Arrange: retrieval returns chunks above threshold
        Conversation mockConv = new Conversation();
        mockConv.setId(UUID.randomUUID());
        mockConv.setTenantId("test-tenant");
        when(conversationService.resolveOrCreate(any(), eq("test-tenant"))).thenReturn(mockConv);
        when(conversationService.saveMessage(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    var msg = new com.ayush.documentqa.entity.Message();
                    msg.setId(UUID.randomUUID());
                    return msg;
                });
        when(conversationService.getRecentHistory(any())).thenReturn(List.of());

        RetrievedChunk chunk = new RetrievedChunk(
                UUID.randomUUID(), "The fee is ₹50,000", 1,
                UUID.randomUUID(), "Fee Policy", "FEES", 0.85);
        when(retrievalService.retrieve(eq("test-tenant"), anyString(), any()))
                .thenReturn(List.of(chunk));

        when(promptBuilder.buildSystemPrompt()).thenReturn("System prompt");
        when(promptBuilder.buildUserMessage(anyString(), anyList(), anyList())).thenReturn("User message");
        when(aiService.call(anyString(), anyString())).thenReturn("The fee is ₹50,000.");

        ChatRequest request = new ChatRequest(null, "What is the fee?", "FEES");

        // Act
        ChatResponse response = chatService.chat(request);

        // Assert: LLM was called, answer includes sources
        assertThat(response.answer()).isEqualTo("The fee is ₹50,000.");
        assertThat(response.sources()).hasSize(1);
        verify(aiService, times(1)).call(anyString(), anyString());
    }
}

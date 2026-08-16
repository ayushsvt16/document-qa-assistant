package com.ayush.documentqa.controller;

import com.ayush.documentqa.dto.ConversationResponse;
import com.ayush.documentqa.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Conversations", description = "Conversation history")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get conversation with full message history")
    public ResponseEntity<ConversationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(conversationService.getConversation(id));
    }
}

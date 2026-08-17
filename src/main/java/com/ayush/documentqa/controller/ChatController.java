package com.ayush.documentqa.controller;

import com.ayush.documentqa.dto.ChatRequest;
import com.ayush.documentqa.dto.ChatResponse;
import com.ayush.documentqa.service.ChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Question answering with RAG")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    @Operation(summary = "Ask a question (synchronous)")
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Ask a question (streaming via SSE)")
    public SseEmitter streamChat(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2 minute timeout
        AtomicBoolean clientDisconnected = new AtomicBoolean(false);

        emitter.onCompletion(() -> clientDisconnected.set(true));
        emitter.onTimeout(() -> clientDisconnected.set(true));
        emitter.onError(e -> clientDisconnected.set(true));

        ChatService.StreamingChatResult result = chatService.streamChat(request);

        if (result.isRefusal()) {
            // Refusal — send single event and complete
            try {
                emitter.send(SseEmitter.event()
                        .name("token")
                        .data(result.tokens().blockFirst()));
                emitter.send(SseEmitter.event()
                        .name("sources")
                        .data("[]"));
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data(result.conversationId().toString()));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            return emitter;
        }

        // Stream tokens from LLM
        StringBuilder fullAnswer = new StringBuilder();

        result.tokens()
                .doOnNext(token -> {
                    if (clientDisconnected.get()) return;
                    fullAnswer.append(token);
                    try {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(token));
                    } catch (IOException e) {
                        clientDisconnected.set(true);
                        log.debug("Client disconnected during streaming");
                    }
                })
                .doOnComplete(() -> {
                    if (clientDisconnected.get()) return;
                    try {
                        // Send sources as terminal event
                        String sourcesJson = objectMapper.writeValueAsString(result.sources());
                        emitter.send(SseEmitter.event()
                                .name("sources")
                                .data(sourcesJson));
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data(result.conversationId().toString()));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(e -> {
                    log.error("Streaming error: {}", e.getMessage());
                    if (!clientDisconnected.get()) {
                        emitter.completeWithError(e);
                    }
                })
                .subscribe();

        return emitter;
    }
}

package com.ayush.documentqa.ai;

import com.ayush.documentqa.entity.Message;
import com.ayush.documentqa.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Constructs the grounded system prompt and user message for the LLM.
 * The model is instructed to answer ONLY from supplied context.
 *
 * This is a separate component (not inline in ChatService) for testability
 * and to make prompt engineering visible.
 */
@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are a document-based Q&A assistant for a school administration system.
            
            STRICT RULES:
            1. Answer ONLY using the context provided below. Do not use outside knowledge.
            2. If the provided context does not contain enough information to answer the question, say:
               "The provided documents do not contain enough information to answer this question."
            3. Do not invent facts, dates, numbers, or policies not present in the context.
            4. When possible, reference the source document and page number in your answer.
            5. Keep answers concise and factual.
            6. If multiple sources provide relevant information, synthesize them coherently.
            """;

    public String buildSystemPrompt() {
        return SYSTEM_PROMPT;
    }

    /**
     * Builds the user message with retrieved context and conversation history.
     * Only relevant chunk snippets are included — never entire documents.
     */
    public String buildUserMessage(String question, List<RetrievedChunk> chunks, List<Message> history) {
        StringBuilder sb = new StringBuilder();

        // Include recent conversation history for follow-up context
        if (history != null && !history.isEmpty()) {
            sb.append("=== CONVERSATION HISTORY ===\n");
            for (Message msg : history) {
                sb.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
            }
            sb.append("\n");
        }

        // Include retrieved context chunks
        sb.append("=== RELEVANT DOCUMENT CONTEXT ===\n");
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            sb.append(String.format("[Source %d] Document: \"%s\"", i + 1,
                    chunk.documentTitle() != null ? chunk.documentTitle() : "Untitled"));
            if (chunk.pageNumber() != null) {
                sb.append(String.format(" | Page: %d", chunk.pageNumber()));
            }
            sb.append(String.format(" | Relevance: %.0f%%\n", chunk.similarityScore() * 100));
            sb.append(chunk.content()).append("\n\n");
        }

        sb.append("=== QUESTION ===\n");
        sb.append(question);

        return sb.toString();
    }
}

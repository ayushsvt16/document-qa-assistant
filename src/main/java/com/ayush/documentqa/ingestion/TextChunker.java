package com.ayush.documentqa.ingestion;

import com.ayush.documentqa.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Token-aware text chunking with configurable size and overlap.
 *
 * Strategy: Character-based approximation (1 token ≈ 4 characters for English text).
 * We approximate because exact tokenization requires the model's tokenizer,
 * which adds a provider dependency to the ingestion pipeline.
 *
 * Trade-offs:
 * - Pro: Fast, deterministic, provider-independent
 * - Con: Approximate token counts — actual token count may vary ±10%
 * - Pro: Sentence boundary awareness reduces mid-sentence splits
 *
 * The chunk size (800 tokens default) balances context richness against embedding quality.
 * The overlap (100 tokens default) ensures continuity across chunk boundaries.
 */
@Component
public class TextChunker {

    private static final int CHARS_PER_TOKEN = 4; // Approximate for English text

    private final int maxChunkChars;
    private final int overlapChars;

    public TextChunker(AppProperties appProperties) {
        this.maxChunkChars = appProperties.chunking().maxTokens() * CHARS_PER_TOKEN;
        this.overlapChars = appProperties.chunking().overlapTokens() * CHARS_PER_TOKEN;
    }

    /**
     * Splits extracted pages into chunks, preserving page number metadata.
     * Each chunk records the page number of its first contributing page.
     */
    public List<ChunkedText> chunk(List<ExtractedPage> pages) {
        if (pages == null || pages.isEmpty()) {
            return List.of();
        }

        // Concatenate all pages with page markers
        List<PageSegment> segments = new ArrayList<>();
        for (ExtractedPage page : pages) {
            if (page.text() != null && !page.text().isBlank()) {
                segments.add(new PageSegment(page.text(), page.pageNumber()));
            }
        }

        if (segments.isEmpty()) {
            return List.of();
        }

        // Build combined text with page tracking
        StringBuilder combined = new StringBuilder();
        List<PageMarker> markers = new ArrayList<>();
        for (PageSegment segment : segments) {
            int startPos = combined.length();
            combined.append(segment.text());
            markers.add(new PageMarker(startPos, combined.length(), segment.pageNumber()));
            combined.append("\n\n");
        }

        String fullText = combined.toString().trim();
        if (fullText.isEmpty()) {
            return List.of();
        }

        // Chunk with overlap
        List<ChunkedText> chunks = new ArrayList<>();
        int pos = 0;
        int chunkIndex = 0;

        while (pos < fullText.length()) {
            int end = Math.min(pos + maxChunkChars, fullText.length());

            // Try to break at sentence boundary if not at the end
            if (end < fullText.length()) {
                int sentenceBreak = findSentenceBreak(fullText, pos, end);
                if (sentenceBreak > pos) {
                    end = sentenceBreak;
                }
            }

            String chunkText = fullText.substring(pos, end).trim();
            if (!chunkText.isEmpty()) {
                Integer pageNumber = findPageForPosition(markers, pos);
                int tokenCount = estimateTokens(chunkText);
                chunks.add(new ChunkedText(chunkText, pageNumber, chunkIndex, tokenCount));
                chunkIndex++;
            }

            // Advance position with overlap
            pos = end - overlapChars;
            if (pos <= (end - maxChunkChars) || pos >= fullText.length()) {
                pos = end;
            }
            // Avoid infinite loops
            if (pos <= 0 && end == 0) break;
        }

        return chunks;
    }

    /** Find the best sentence boundary within the range */
    private int findSentenceBreak(String text, int start, int end) {
        int searchFrom = Math.max(start, end - maxChunkChars / 4);
        int lastPeriod = -1;
        for (int i = end - 1; i >= searchFrom; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '!' || c == '?' || c == '\n') && i + 1 < text.length()) {
                lastPeriod = i + 1;
                break;
            }
        }
        return lastPeriod > start ? lastPeriod : end;
    }

    /** Determine which page a character position belongs to */
    private Integer findPageForPosition(List<PageMarker> markers, int position) {
        for (PageMarker marker : markers) {
            if (position >= marker.start && position < marker.end) {
                return marker.pageNumber();
            }
        }
        // Fall back to the last known page
        if (!markers.isEmpty()) {
            return markers.getLast().pageNumber();
        }
        return null;
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, text.length() / CHARS_PER_TOKEN);
    }

    public record ChunkedText(String text, Integer pageNumber, int chunkIndex, int tokenCount) {}

    private record PageSegment(String text, Integer pageNumber) {}

    private record PageMarker(int start, int end, Integer pageNumber) {}
}

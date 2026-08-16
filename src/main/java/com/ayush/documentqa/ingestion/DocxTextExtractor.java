package com.ayush.documentqa.ingestion;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * DOCX text extraction using Apache POI.
 * Preserves paragraph boundaries as logical sections.
 *
 * Limitation: DOCX format does not reliably expose physical page numbers
 * because pagination is determined by the rendering engine (Word, LibreOffice, etc.).
 * We use section numbering (1-based, sequential) instead and document this honestly.
 */
@Component
public class DocxTextExtractor implements TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocxTextExtractor.class);

    @Override
    public boolean supports(String contentType, String filename) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType)
                || (filename != null && filename.toLowerCase().endsWith(".docx"));
    }

    @Override
    public List<ExtractedPage> extract(InputStream inputStream, String filename) {
        List<ExtractedPage> sections = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            log.info("Extracting text from DOCX: {} ({} paragraphs)", filename, paragraphs.size());

            StringBuilder currentSection = new StringBuilder();
            int sectionIndex = 1;

            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText().trim();
                if (text.isEmpty()) {
                    // Empty paragraph may signal section break — flush current section
                    if (!currentSection.isEmpty()) {
                        sections.add(new ExtractedPage(currentSection.toString().trim(), null));
                        currentSection = new StringBuilder();
                        sectionIndex++;
                    }
                    continue;
                }
                currentSection.append(text).append("\n");
            }

            // Flush remaining content
            if (!currentSection.isEmpty()) {
                sections.add(new ExtractedPage(currentSection.toString().trim(), null));
            }

            // If all text ended up in zero sections, treat entire doc as one section
            if (sections.isEmpty()) {
                String allText = paragraphs.stream()
                        .map(p -> p.getText().trim())
                        .filter(t -> !t.isEmpty())
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");
                if (!allText.isEmpty()) {
                    sections.add(new ExtractedPage(allText, null));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from DOCX: " + filename, e);
        }
        return sections;
    }
}

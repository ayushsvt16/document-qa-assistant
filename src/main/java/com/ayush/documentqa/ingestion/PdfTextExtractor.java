package com.ayush.documentqa.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfTextExtractor implements TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfTextExtractor.class);

    @Override
    public boolean supports(String contentType, String filename) {
        return "application/pdf".equals(contentType)
                || (filename != null && filename.toLowerCase().endsWith(".pdf"));
    }

    @Override
    public List<ExtractedPage> extract(InputStream inputStream, String filename) {
        List<ExtractedPage> pages = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();
            log.info("Extracting text from PDF: {} ({} pages)", filename, totalPages);

            for (int i = 1; i <= totalPages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document).trim();
                if (!text.isEmpty()) {
                    pages.add(new ExtractedPage(text, i));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from PDF: " + filename, e);
        }
        return pages;
    }
}

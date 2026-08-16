package com.ayush.documentqa.controller;

import com.ayush.documentqa.dto.DocumentDetailResponse;
import com.ayush.documentqa.dto.DocumentUploadResponse;
import com.ayush.documentqa.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
@Tag(name = "Documents", description = "Document upload, listing, and management")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a document for ingestion")
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category) {

        DocumentUploadResponse response = documentService.uploadDocument(file, title, category);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping
    @Operation(summary = "List documents for the current tenant")
    public ResponseEntity<Page<DocumentDetailResponse>> list(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(documentService.listDocuments(pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document details")
    public ResponseEntity<DocumentDetailResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a document and its chunks")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}

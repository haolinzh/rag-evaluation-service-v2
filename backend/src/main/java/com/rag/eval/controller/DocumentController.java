package com.rag.eval.controller;

import com.rag.eval.model.ChunkConfig;
import com.rag.eval.model.ChunkPreview;
import com.rag.eval.model.DocumentMeta;
import com.rag.eval.service.AuthService;
import com.rag.eval.service.DocumentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final AuthService authService;

    public DocumentController(DocumentService documentService, AuthService authService) {
        this.documentService = documentService;
        this.authService = authService;
    }

    @PostMapping("/upload")
    public ResponseEntity<DocumentMeta> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "splitMode", defaultValue = ChunkConfig.MODE_SIZE) String splitMode,
            @RequestParam(value = "chunkSize", defaultValue = "1000") int chunkSize,
            @RequestParam(value = "delimiter", defaultValue = "") String delimiter,
            @RequestParam(value = "overlap", defaultValue = "150") int overlap,
            @RequestParam(value = "visibility", defaultValue = "DEPARTMENT") String visibility) throws Exception {
        ChunkConfig config = new ChunkConfig(splitMode, chunkSize, delimiter, overlap);
        return ResponseEntity.ok(documentService.ingest(file, config, authService.currentUser(), visibility));
    }

    @GetMapping
    public ResponseEntity<List<DocumentMeta>> listAll() {
        return ResponseEntity.ok(documentService.listAll(authService.currentUserOrGuest()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(@PathVariable Long id) {
        documentService.deleteById(id, authService.currentUser());
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentMeta> reprocess(
            @PathVariable Long id,
            @RequestParam(value = "splitMode", defaultValue = ChunkConfig.MODE_SIZE) String splitMode,
            @RequestParam(value = "chunkSize", defaultValue = "1000") int chunkSize,
            @RequestParam(value = "delimiter", defaultValue = "") String delimiter,
            @RequestParam(value = "overlap", defaultValue = "150") int overlap,
            @RequestParam(value = "visibility", required = false) String visibility) {
        ChunkConfig config = new ChunkConfig(splitMode, chunkSize, delimiter, overlap);
        return ResponseEntity.ok(documentService.reprocess(id, config, authService.currentUser(), visibility));
    }

    @GetMapping("/{id}/chunks")
    public ResponseEntity<List<ChunkPreview>> chunks(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getChunkPreviews(id, authService.currentUserOrGuest()));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        return documentService.getOriginal(id, authService.currentUserOrGuest())
            .map(f -> {
                ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(f.fileName(), StandardCharsets.UTF_8)
                    .build();
                return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(f.bytes());
            })
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}

package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.dto.ingestion.SourceItemResponse;
import com.limasegura.limasegurabackend.dto.worker.WorkerContentRequest;
import com.limasegura.limasegurabackend.ingestion.fetch.WorkerContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/internal/worker")
@PreAuthorize("hasRole('SERVICE')")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerContentService workerContentService;

    @GetMapping("/pending-videos")
    public ResponseEntity<List<SourceItemResponse>> getPendingVideos() {
        return ResponseEntity.ok(workerContentService.getPendingVideos());
    }

    @PostMapping("/source-items/{id}/content")
    public ResponseEntity<Void> submitContent(
            @PathVariable Long id,
            @Valid @RequestBody WorkerContentRequest request) {
        workerContentService.submitContent(id, request);
        return ResponseEntity.ok().build();
    }
}

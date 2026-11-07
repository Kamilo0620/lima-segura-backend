package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.dto.source.SourceCreateRequest;
import com.limasegura.limasegurabackend.dto.source.SourceResponse;
import com.limasegura.limasegurabackend.dto.source.SourceUpdateRequest;
import com.limasegura.limasegurabackend.service.NewsSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/sources")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminSourceController {

    private final NewsSourceService newsSourceService;

    @PostMapping
    public ResponseEntity<SourceResponse> create(@Valid @RequestBody SourceCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(newsSourceService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<SourceResponse>> getAll() {
        return ResponseEntity.ok(newsSourceService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<SourceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(newsSourceService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SourceResponse> update(@PathVariable Long id, @Valid @RequestBody SourceUpdateRequest request) {
        return ResponseEntity.ok(newsSourceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        newsSourceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/ingest")
    public ResponseEntity<Void> triggerIngestion(@PathVariable Long id) {
        newsSourceService.triggerIngestion(id);
        return ResponseEntity.accepted().build();
    }
}

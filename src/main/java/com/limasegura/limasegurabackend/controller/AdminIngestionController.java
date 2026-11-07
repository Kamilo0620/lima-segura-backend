package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.dto.incident.IncidentResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentReviewRequest;
import com.limasegura.limasegurabackend.dto.ingestion.IngestionRunResponse;
import com.limasegura.limasegurabackend.dto.ingestion.SourceItemResponse;
import com.limasegura.limasegurabackend.model.IncidentStatus;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import com.limasegura.limasegurabackend.service.IncidentService;
import com.limasegura.limasegurabackend.service.IngestionRunService;
import com.limasegura.limasegurabackend.service.SourceItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminIngestionController {

    private final IngestionRunService ingestionRunService;
    private final SourceItemService sourceItemService;
    private final IncidentService incidentService;
    private final com.limasegura.limasegurabackend.ingestion.scheduler.IngestionScheduler ingestionScheduler;

    @PostMapping("/ingestion/trigger-all")
    public ResponseEntity<java.util.Map<String, Object>> triggerAllIngestion() {
        int count = ingestionScheduler.triggerAllSources();
        return ResponseEntity.ok(java.util.Map.of(
            "status", "STARTED",
            "message", "Ciclo de ingesta disparado para " + count + " fuentes activas",
            "sourcesTriggered", count
        ));
    }

    @GetMapping("/ingestion-runs")
    public ResponseEntity<Page<IngestionRunResponse>> getIngestionRuns(
            @RequestParam(required = false) Long sourceId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ingestionRunService.findAll(sourceId, pageable));
    }

    @GetMapping("/source-items")
    public ResponseEntity<Page<SourceItemResponse>> getSourceItems(
            @RequestParam(required = false) SourceItemStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(sourceItemService.findAll(status, pageable));
    }

    @PostMapping("/source-items/{id}/reprocess")
    public ResponseEntity<Void> reprocessSourceItem(@PathVariable Long id) {
        sourceItemService.reprocess(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/incidents")
    public ResponseEntity<Page<IncidentResponse>> getIncidentsForReview(
            @RequestParam(required = false) IncidentStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(incidentService.findByStatus(status != null ? status : IncidentStatus.PENDING_REVIEW, pageable));
    }

    @PatchMapping("/incidents/{id}")
    public ResponseEntity<IncidentResponse> reviewIncident(
            @PathVariable Long id,
            @Valid @RequestBody IncidentReviewRequest request) {
        return ResponseEntity.ok(incidentService.review(id, request));
    }
}

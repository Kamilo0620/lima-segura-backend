package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.dto.incident.IncidentResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentReviewRequest;
import com.limasegura.limasegurabackend.dto.ingestion.IngestionRunResponse;
import com.limasegura.limasegurabackend.dto.ingestion.PipelineTelemetryResponse;
import com.limasegura.limasegurabackend.dto.ingestion.SourceItemResponse;
import com.limasegura.limasegurabackend.ingestion.telemetry.PipelineActivityTracker;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.IncidentOrigin;
import com.limasegura.limasegurabackend.model.IncidentStatus;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
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
    private final com.limasegura.limasegurabackend.ingestion.verify.CrossVerificationService crossVerificationService;
    private final IncidentRepository incidentRepository;
    private final SourceItemRepository sourceItemRepository;
    private final PipelineActivityTracker activityTracker;

    @GetMapping("/telemetry")
    public ResponseEntity<PipelineTelemetryResponse> getPipelineTelemetry() {
        long realNewspaper = incidentRepository.countByStatusAndOrigin(IncidentStatus.PUBLISHED, IncidentOrigin.NEWSPAPER);
        long realYoutube = incidentRepository.countByStatusAndOrigin(IncidentStatus.PUBLISHED, IncidentOrigin.YOUTUBE);
        long citizen = incidentRepository.countByStatusAndOrigin(IncidentStatus.PUBLISHED, IncidentOrigin.CITIZEN);
        long sampleBaseline = incidentRepository.countByStatusAndOrigin(IncidentStatus.PUBLISHED, IncidentOrigin.SAMPLE);
        long totalPublished = incidentRepository.countByStatus(IncidentStatus.PUBLISHED);
        long pendingReview = incidentRepository.countByStatus(IncidentStatus.PENDING_REVIEW);

        long queued = sourceItemRepository.countByStatus(SourceItemStatus.DISCOVERED)
                + sourceItemRepository.countByStatus(SourceItemStatus.CONTENT_READY);
        long extracted = sourceItemRepository.countByStatus(SourceItemStatus.EXTRACTED);
        long discarded = sourceItemRepository.countByStatus(SourceItemStatus.DISCARDED);
        long failed = sourceItemRepository.countByStatus(SourceItemStatus.FAILED);

        java.util.List<Incident> latest = incidentRepository.findTop10ByStatusOrderByIdDesc(IncidentStatus.PUBLISHED);
        java.util.List<PipelineTelemetryResponse.RecentPublishedItem> recentList = latest.stream()
                .map(i -> PipelineTelemetryResponse.RecentPublishedItem.builder()
                        .id(i.getId())
                        .title(i.getTitle())
                        .origin(i.getOrigin() != null ? i.getOrigin().name() : "UNKNOWN")
                        .districtName(i.getDistrict() != null ? i.getDistrict().getName() : "Lima/Callao")
                        .geoPrecision(i.getGeoPrecision() != null ? i.getGeoPrecision().name() : "DISTRICT")
                        .llmModel(i.getLlmModel())
                        .occurredAt(i.getOccurredAt())
                        .createdAt(i.getCreatedAt())
                        .build())
                .toList();

        return ResponseEntity.ok(PipelineTelemetryResponse.builder()
                .searchingInternetNow(activityTracker.isSearchingInternetNow())
                .activeOperationsCount(activityTracker.getActiveOperationsCount())
                .currentOperation(activityTracker.getCurrentOperation())
                .lastActivityAt(activityTracker.getLastActivityAt())
                .totalPublished(totalPublished)
                .realNewspaperCount(realNewspaper)
                .realYoutubeCount(realYoutube)
                .citizenCount(citizen)
                .sampleBaselineCount(sampleBaseline)
                .pendingReviewCount(pendingReview)
                .sourceItemsQueued(queued)
                .sourceItemsExtracted(extracted)
                .sourceItemsDiscarded(discarded)
                .sourceItemsFailed(failed)
                .recentPublished(recentList)
                .recentLogs(activityTracker.getRecentLogs(60))
                .build());
    }

    @PostMapping("/ingestion/trigger-all")
    public ResponseEntity<java.util.Map<String, Object>> triggerAllIngestion() {
        int count = ingestionScheduler.triggerAllSources();
        return ResponseEntity.ok(java.util.Map.of(
            "status", "STARTED",
            "message", "Ciclo de ingesta disparado para " + count + " fuentes activas",
            "sourcesTriggered", count
        ));
    }

    @PostMapping("/ingestion/cross-verify")
    public ResponseEntity<com.limasegura.limasegurabackend.ingestion.verify.CrossVerificationService.RefinementStats> triggerCrossVerification() {
        return ResponseEntity.ok(crossVerificationService.refineRecentIncidents());
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

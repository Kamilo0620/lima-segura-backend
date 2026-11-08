package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.dto.incident.HeatmapPointResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentDetailResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentResponse;
import com.limasegura.limasegurabackend.model.IncidentOrigin;
import com.limasegura.limasegurabackend.model.IncidentStatus;
import com.limasegura.limasegurabackend.model.IncidentType;
import com.limasegura.limasegurabackend.service.IncidentService;
import com.limasegura.limasegurabackend.service.SseService;
import com.limasegura.limasegurabackend.dto.incident.ConfirmationResponse;
import com.limasegura.limasegurabackend.model.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final SseService sseService;

    @GetMapping
    public ResponseEntity<Page<IncidentResponse>> getAll(
            @RequestParam(required = false) IncidentType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) IncidentOrigin origin,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(incidentService.findFiltered(type, from, to, district, status, origin, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentDetailResponse> getById(@PathVariable Long id,
                                                          @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(incidentService.getDetail(id, currentUser));
    }

    @GetMapping("/heatmap")
    public ResponseEntity<List<HeatmapPointResponse>> getHeatmap(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) List<IncidentType> types,
            @RequestParam(required = false) Double minConfidence) {
        return ResponseEntity.ok(incidentService.getHeatmapPoints(from, to, types, minConfidence));
    }

    @GetMapping("/stats/dashboard")
    public ResponseEntity<com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(incidentService.getDashboardStats());
    }

    @PostMapping("/report")
    public ResponseEntity<IncidentResponse> createCitizenReport(
            @jakarta.validation.Valid @RequestBody com.limasegura.limasegurabackend.dto.incident.CitizenReportRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(incidentService.createCitizenReport(request, currentUser));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<ConfirmationResponse> confirm(@PathVariable Long id,
                                                        @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(incidentService.confirm(id, currentUser));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        sseService.addEmitter(emitter);
        emitter.onCompletion(() -> sseService.removeEmitter(emitter));
        emitter.onTimeout(() -> sseService.removeEmitter(emitter));
        emitter.onError(e -> sseService.removeEmitter(emitter));
        return emitter;
    }
}
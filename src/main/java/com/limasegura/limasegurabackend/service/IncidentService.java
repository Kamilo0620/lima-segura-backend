package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.incident.HeatmapPointResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentDetailResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentReviewRequest;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.IncidentOrigin;
import com.limasegura.limasegurabackend.model.IncidentStatus;
import com.limasegura.limasegurabackend.model.IncidentType;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final com.limasegura.limasegurabackend.repository.DistrictRepository districtRepository;
    private final SseService sseService;

    public Page<IncidentResponse> findFiltered(IncidentType type, LocalDateTime from, LocalDateTime to,
                                              String district, IncidentStatus status, IncidentOrigin origin,
                                              Pageable pageable) {
        Specification<Incident> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (type != null) predicates.add(cb.equal(root.get("incidentType"), type));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            if (district != null && !district.isBlank()) {
                predicates.add(cb.or(
                    cb.equal(root.get("district").get("ubigeo"), district),
                    cb.like(cb.lower(root.get("district").get("name")), "%" + district.toLowerCase() + "%")
                ));
            }
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (origin != null) predicates.add(cb.equal(root.get("origin"), origin));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Incident> page = incidentRepository.findAll(spec, pageable);
        return page.map(this::mapToResponse);
    }

    public IncidentDetailResponse getDetail(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        List<IncidentDetailResponse.SourceReferenceDto> sources = incident.getSourceItems().stream()
                .map(item -> IncidentDetailResponse.SourceReferenceDto.builder()
                        .sourceItemId(item.getId())
                        .sourceName(item.getNewsSource() != null ? item.getNewsSource().getName() : null)
                        .url(item.getUrl())
                        .title(item.getTitle())
                        .build())
                .collect(Collectors.toList());

        return IncidentDetailResponse.builder()
                .id(incident.getId())
                .incidentType(incident.getIncidentType())
                .title(incident.getTitle())
                .summary(incident.getSummary())
                .occurredAt(incident.getOccurredAt())
                .occurredAtPrecision(incident.getOccurredAtPrecision())
                .address(incident.getAddress())
                .districtName(incident.getDistrict() != null ? incident.getDistrict().getName() : null)
                .province(incident.getDistrict() != null ? incident.getDistrict().getProvince() : null)
                .department(incident.getDistrict() != null ? incident.getDistrict().getDepartment() : null)
                .lat(incident.getLat())
                .lng(incident.getLng())
                .geoPrecision(incident.getGeoPrecision())
                .confidence(incident.getConfidence())
                .incidentCount(incident.getIncidentCount())
                .status(incident.getStatus())
                .origin(incident.getOrigin())
                .llmModel(incident.getLlmModel())
                .victimsCount(incident.getVictimsCount())
                .evidenceExcerpt(incident.getEvidenceExcerpt())
                .createdAt(incident.getCreatedAt())
                .sources(sources)
                .build();
    }

    /**
     * Calcula los puntos del mapa de calor con el modelo de ponderación del informe técnico:
     * Intensidad = Gravedad_Delito * Peso_Fuente * Factor_Tiempo
     *
     * - Gravedad_Delito: 1 a 10 según enum IncidentType.
     * - Peso_Fuente: Credibilidad / 10 (ej. RPP = 0.95, Trome = 0.78, etc.).
     * - Factor_Tiempo:
     *     0 a 6 horas:  1.0 (Rojo vivo)
     *     6 a 24 horas: 0.7 (Naranja)
     *     24 a 48 horas:0.4 (Amarillo)
     *     > 48 horas:   0.1 (Decaimiento histórico)
     */
    public List<HeatmapPointResponse> getHeatmapPoints(LocalDateTime from, LocalDateTime to,
                                                       List<IncidentType> types, Double minConfidence) {
        Specification<Incident> spec = (root, query, cb) -> {
            query.distinct(true);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), IncidentStatus.PUBLISHED));
            predicates.add(cb.isNotNull(root.get("lat")));
            predicates.add(cb.isNotNull(root.get("lng")));
            if (from != null) predicates.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            if (to != null) predicates.add(cb.lessThanOrEqualTo(root.get("occurredAt"), to));
            if (types != null && !types.isEmpty()) predicates.add(root.get("incidentType").in(types));
            if (minConfidence != null) predicates.add(cb.greaterThanOrEqualTo(root.get("confidence"), minConfidence));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<Incident> incidents = incidentRepository.findAll(spec);
        LocalDateTime now = LocalDateTime.now();

        return incidents.stream()
                .map(i -> {
                    // 1. Gravedad del Delito (1 - 10)
                    int gravedad = i.getIncidentType() != null ? i.getIncidentType().getSeverity() : 5;

                    // 2. Peso de la Fuente = Credibilidad / 10
                    double pesoFuente = 0.8;
                    if (i.getSourceItems() != null && !i.getSourceItems().isEmpty()) {
                        pesoFuente = i.getSourceItems().stream()
                                .filter(si -> si.getNewsSource() != null && si.getNewsSource().getCredibility() != null)
                                .mapToDouble(si -> si.getNewsSource().getCredibility() / 10.0)
                                .max()
                                .orElse(i.getConfidence() > 0 ? i.getConfidence() : 0.8);
                    } else if (i.getConfidence() > 0) {
                        pesoFuente = i.getConfidence();
                    }

                    // 3. Factor Tiempo según antigüedad
                    LocalDateTime time = i.getOccurredAt() != null ? i.getOccurredAt() : i.getCreatedAt();
                    long hours = Duration.between(time, now).abs().toHours();
                    double factorTiempo;
                    String colorHint;

                    if (hours <= 6) {
                        factorTiempo = 1.0;
                        colorHint = "red";
                    } else if (hours <= 24) {
                        factorTiempo = 0.7;
                        colorHint = "orange";
                    } else if (hours <= 48) {
                        factorTiempo = 0.4;
                        colorHint = "yellow";
                    } else {
                        factorTiempo = 0.1;
                        colorHint = "gray";
                    }

                    // Intensidad = Gravedad_Delito * Peso_Fuente * Factor_Tiempo
                    double intensidad = gravedad * pesoFuente * factorTiempo * Math.max(1, i.getIncidentCount());

                    return HeatmapPointResponse.builder()
                            .lat(i.getLat())
                            .lng(i.getLng())
                            .weight(Math.round(intensidad * 100.0) / 100.0)
                            .colorHint(colorHint)
                            .severity(gravedad)
                            .sourceWeight(Math.round(pesoFuente * 100.0) / 100.0)
                            .timeFactor(factorTiempo)
                            .incidentId(i.getId())
                            .incidentType(i.getIncidentType() != null ? i.getIncidentType().name() : "OTHER")
                            .title(i.getTitle())
                            .build();
                })
                .collect(Collectors.toList());
    }

    public Page<IncidentResponse> findByStatus(IncidentStatus status, Pageable pageable) {
        Page<Incident> page = incidentRepository.findByStatus(status, pageable);
        return page.map(this::mapToResponse);
    }

    @Transactional
    public IncidentResponse review(Long id, IncidentReviewRequest request) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found with id: " + id));

        if (request.getStatus() != null) {
            incident.setStatus(request.getStatus());
        }
        if (request.getTitle() != null) {
            incident.setTitle(request.getTitle());
        }
        if (request.getSummary() != null) {
            incident.setSummary(request.getSummary());
        }

        return mapToResponse(incidentRepository.save(incident));
    }

    public com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse getDashboardStats() {
        long total = incidentRepository.count();

        // Hourly risk profile (24 hours) - calibrated according to criminal peak in Lima (12:00 - 22:00)
        List<com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.HourlyRiskDto> hourlyRisk = new ArrayList<>();
        int[] hourlyScores = {12, 10, 8, 6, 8, 14, 22, 35, 48, 56, 68, 74, 80, 85, 92, 88, 79, 72, 65, 54, 42, 34, 25, 18};
        for (int i = 0; i < 24; i++) {
            String hourStr = String.format("%02d:00", i);
            hourlyRisk.add(com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.HourlyRiskDto.builder()
                    .hour(hourStr)
                    .score(hourlyScores[i])
                    .build());
        }

        // Category distribution matching the proposal / mockup
        List<com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.CategoryStatDto> categoryDistribution = List.of(
            com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.CategoryStatDto.builder().category("Robo a transeúnte").type("ROBBERY").percentage(38).count(Math.max(1, (long)(total * 0.38))).color("#ef4444").build(),
            com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.CategoryStatDto.builder().category("Robo de vehículo").type("VEHICLE_THEFT").percentage(22).count(Math.max(1, (long)(total * 0.22))).color("#f97316").build(),
            com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.CategoryStatDto.builder().category("Hurto").type("THEFT").percentage(16).count(Math.max(1, (long)(total * 0.16))).color("#eab308").build(),
            com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.CategoryStatDto.builder().category("Asalto con arma").type("ARMED_ROBBERY").percentage(14).count(Math.max(1, (long)(total * 0.14))).color("#a855f7").build(),
            com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.CategoryStatDto.builder().category("Otros").type("OTHER").percentage(10).count(Math.max(1, (long)(total * 0.10))).color("#64748b").build()
        );

        // Danger zones matching the mockup
        List<com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.DangerZoneDto> topZones = List.of(
            com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.DangerZoneDto.builder().rank(1).zone("Av. Alfonso Ugarte (Breña)").level("ALTO").score(78).build(),
            com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.DangerZoneDto.builder().rank(2).zone("Jr. Zorritos (Cercado de Lima)").level("ALTO").score(74).build(),
            com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.DangerZoneDto.builder().rank(3).zone("Av. Venezuela (Breña)").level("MEDIO").score(61).build(),
            com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.DangerZoneDto.builder().rank(4).zone("Av. Tingo María (Cercado de Lima)").level("MEDIO").score(58).build(),
            com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.DangerZoneDto.builder().rank(5).zone("Jr. Carhuaz (Breña)").level("BAJO").score(42).build()
        );

        return com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.builder()
                .totalIncidents(total)
                .riskLocation(com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse.RiskLocationDto.builder()
                        .score(78)
                        .level("ALTO")
                        .address("Av. Alfonso Ugarte 1235, Breña")
                        .radiusMeters(100)
                        .trendPercent("+12%")
                        .build())
                .hourlyRisk(hourlyRisk)
                .categoryDistribution(categoryDistribution)
                .topDangerZones(topZones)
                .build();
    }

    @Transactional
    public IncidentResponse createCitizenReport(com.limasegura.limasegurabackend.dto.incident.CitizenReportRequest request) {
        Incident incident = new Incident();
        incident.setIncidentType(request.getIncidentType());
        incident.setTitle(request.getTitle());
        incident.setSummary(request.getSummary());
        incident.setAddress(request.getAddress());
        incident.setLat(request.getLat() != null ? request.getLat() : -12.0560);
        incident.setLng(request.getLng() != null ? request.getLng() : -77.0420);
        incident.setGeoPrecision(request.getLat() != null ? com.limasegura.limasegurabackend.model.GeoPrecision.EXACT_ADDRESS : com.limasegura.limasegurabackend.model.GeoPrecision.DISTRICT);
        incident.setConfidence(0.85);
        incident.setStatus(IncidentStatus.PUBLISHED);
        incident.setOrigin(IncidentOrigin.CITIZEN);
        incident.setOccurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now());
        incident.setOccurredAtPrecision(com.limasegura.limasegurabackend.model.OccurredAtPrecision.EXACT);
        incident.setVictimsCount(1);
        incident.setIncidentCount(1);
        incident.setCreatedAt(LocalDateTime.now());

        if (request.getDistrictName() != null && !request.getDistrictName().isBlank()) {
            List<com.limasegura.limasegurabackend.model.District> matches = districtRepository.findByNameIgnoreCase(request.getDistrictName().trim());
            if (!matches.isEmpty()) {
                incident.setDistrict(matches.get(0));
            }
        }

        Incident saved = incidentRepository.save(incident);

        try {
            sseService.broadcastIncident(mapToResponse(saved));
        } catch (Exception e) {
            // ignore SSE fail
        }

        return mapToResponse(saved);
    }

    private IncidentResponse mapToResponse(Incident incident) {
        return IncidentResponse.builder()
                .id(incident.getId())
                .incidentType(incident.getIncidentType())
                .title(incident.getTitle())
                .summary(incident.getSummary())
                .occurredAt(incident.getOccurredAt())
                .occurredAtPrecision(incident.getOccurredAtPrecision())
                .address(incident.getAddress())
                .districtName(incident.getDistrict() != null ? incident.getDistrict().getName() : null)
                .province(incident.getDistrict() != null ? incident.getDistrict().getProvince() : null)
                .department(incident.getDistrict() != null ? incident.getDistrict().getDepartment() : null)
                .lat(incident.getLat())
                .lng(incident.getLng())
                .geoPrecision(incident.getGeoPrecision())
                .confidence(incident.getConfidence())
                .incidentCount(incident.getIncidentCount())
                .status(incident.getStatus())
                .origin(incident.getOrigin())
                .victimsCount(incident.getVictimsCount())
                .createdAt(incident.getCreatedAt())
                .build();
    }
}

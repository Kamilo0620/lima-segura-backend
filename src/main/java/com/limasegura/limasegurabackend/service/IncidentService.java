package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.incident.HeatmapPointResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentDetailResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentReviewRequest;
import com.limasegura.limasegurabackend.dto.incident.ConfirmationResponse;
import com.limasegura.limasegurabackend.dto.incident.DashboardStatsResponse;
import com.limasegura.limasegurabackend.event.IncidentPublishedEvent;
import com.limasegura.limasegurabackend.exception.DuplicateResourceException;
import com.limasegura.limasegurabackend.exception.InvalidOperationException;
import com.limasegura.limasegurabackend.exception.ResourceNotFoundException;
import com.limasegura.limasegurabackend.model.Confirmation;
import com.limasegura.limasegurabackend.model.District;
import com.limasegura.limasegurabackend.model.User;
import com.limasegura.limasegurabackend.repository.ConfirmationRepository;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.IncidentOrigin;
import com.limasegura.limasegurabackend.model.IncidentStatus;
import com.limasegura.limasegurabackend.model.IncidentType;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.criteria.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final com.limasegura.limasegurabackend.repository.DistrictRepository districtRepository;
    private final SseService sseService;
    private final ConfirmationRepository confirmationRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** Confirmaciones necesarias para publicar un reporte ciudadano. */
    public static final int CONFIRMATIONS_TO_PUBLISH = 3;

    public Page<IncidentResponse> findFiltered(IncidentType type, LocalDateTime from, LocalDateTime to,
                                              String district, IncidentStatus status, IncidentOrigin origin,
                                              Pageable pageable) {
        return findFiltered(type, from, to, district, status, origin, null, pageable);
    }

    public Page<IncidentResponse> findFiltered(IncidentType type, LocalDateTime from, LocalDateTime to,
                                              String district, IncidentStatus status, IncidentOrigin origin,
                                              String search, Pageable pageable) {
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
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("address")), pattern)
                ));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Incident> page = incidentRepository.findAll(spec, pageable);
        return page.map(this::mapToResponse);
    }

    public IncidentDetailResponse getDetail(Long id) {
        return getDetail(id, null);
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse getDetail(Long id, User currentUser) {
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
                .reporterId(incident.getReporter() != null ? incident.getReporter().getId() : null)
                .confirmationsCount(confirmationRepository.countByIncidentId(incident.getId()))
                .confirmedByMe(currentUser != null
                        && confirmationRepository.existsByIncidentIdAndUserId(incident.getId(), currentUser.getId()))
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

    /**
     * Conteos agrupados reales (feedback del TA): por franja horaria, por tipo de delito y por distrito.
     * Solo se cuentan incidentes publicados.
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getDashboardStats() {
        List<Incident> published = incidentRepository.findAll().stream()
                .filter(i -> i.getStatus() == IncidentStatus.PUBLISHED)
                .toList();
        long total = published.size();

        // 1. Por hora del día (0..23)
        long[] perHour = new long[24];
        published.stream()
                .map(Incident::getOccurredAt)
                .filter(Objects::nonNull)
                .forEach(t -> perHour[t.getHour()]++);
        long maxHour = java.util.Arrays.stream(perHour).max().orElse(0);
        List<DashboardStatsResponse.HourlyRiskDto> hourlyRisk = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            int score = maxHour == 0 ? 0 : (int) Math.round(perHour[h] * 100.0 / maxHour);
            hourlyRisk.add(DashboardStatsResponse.HourlyRiskDto.builder()
                    .hour(String.format("%02d:00", h))
                    .score(score)
                    .build());
        }

        // 2. Por tipo de delito
        String[] palette = {"#ef4444", "#f97316", "#eab308", "#a855f7", "#3b82f6", "#64748b"};
        Map<IncidentType, Long> perType = published.stream()
                .collect(Collectors.groupingBy(Incident::getIncidentType, Collectors.counting()));
        List<Map.Entry<IncidentType, Long>> sortedTypes = perType.entrySet().stream()
                .sorted(Map.Entry.<IncidentType, Long>comparingByValue().reversed())
                .toList();
        List<DashboardStatsResponse.CategoryStatDto> categoryDistribution = new ArrayList<>();
        for (int i = 0; i < sortedTypes.size(); i++) {
            Map.Entry<IncidentType, Long> e = sortedTypes.get(i);
            categoryDistribution.add(DashboardStatsResponse.CategoryStatDto.builder()
                    .category(e.getKey().name())
                    .type(e.getKey().name())
                    .count(e.getValue())
                    .percentage(total == 0 ? 0 : (int) Math.round(e.getValue() * 100.0 / total))
                    .color(palette[Math.min(i, palette.length - 1)])
                    .build());
        }

        // 3. Distritos con más incidentes (top 5)
        Map<String, Long> perDistrict = published.stream()
                .filter(i -> i.getDistrict() != null)
                .collect(Collectors.groupingBy(i -> i.getDistrict().getName(), Collectors.counting()));
        long maxDistrict = perDistrict.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Map.Entry<String, Long>> sortedDistricts = perDistrict.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .toList();
        List<DashboardStatsResponse.DangerZoneDto> topZones = new ArrayList<>();
        for (int i = 0; i < sortedDistricts.size(); i++) {
            Map.Entry<String, Long> e = sortedDistricts.get(i);
            int score = (int) Math.round(e.getValue() * 100.0 / maxDistrict);
            topZones.add(DashboardStatsResponse.DangerZoneDto.builder()
                    .rank(i + 1)
                    .zone(e.getKey())
                    .score(score)
                    .level(score >= 70 ? "ALTO" : score >= 40 ? "MEDIO" : "BAJO")
                    .build());
        }

        // Zona más riesgosa = el distrito con más incidentes (dato real, no inventado)
        DashboardStatsResponse.RiskLocationDto riskLocation = topZones.isEmpty() ? null
                : DashboardStatsResponse.RiskLocationDto.builder()
                        .score(topZones.get(0).getScore())
                        .level(topZones.get(0).getLevel())
                        .address(topZones.get(0).getZone())
                        .radiusMeters(0)
                        .trendPercent(null)
                        .build();

        return DashboardStatsResponse.builder()
                .totalIncidents(total)
                .riskLocation(riskLocation)
                .hourlyRisk(hourlyRisk)
                .categoryDistribution(categoryDistribution)
                .topDangerZones(topZones)
                .build();
    }


    /**
     * Un ciudadano con sesión reporta un incidente. Nace en PENDING_REVIEW:
     * aparece en la lista para que otros vecinos lo confirmen, pero no en el mapa de calor.
     */
    @Transactional
    public IncidentResponse createCitizenReport(com.limasegura.limasegurabackend.dto.incident.CitizenReportRequest request,
                                                User reporter) {
        Incident incident = new Incident();
        incident.setIncidentType(request.getIncidentType());
        incident.setTitle(request.getTitle());
        incident.setSummary(request.getSummary());
        incident.setAddress(request.getAddress());
        incident.setLat(request.getLat() != null ? request.getLat() : -12.0560);
        incident.setLng(request.getLng() != null ? request.getLng() : -77.0420);
        incident.setGeoPrecision(request.getLat() != null ? com.limasegura.limasegurabackend.model.GeoPrecision.EXACT_ADDRESS : com.limasegura.limasegurabackend.model.GeoPrecision.DISTRICT);
        incident.setConfidence(0.0);
        incident.setStatus(IncidentStatus.PENDING_REVIEW);
        incident.setOrigin(IncidentOrigin.CITIZEN);
        incident.setReporter(reporter);
        incident.setOccurredAt(request.getOccurredAt() != null ? request.getOccurredAt() : LocalDateTime.now());
        incident.setOccurredAtPrecision(request.getOccurredAt() != null
                ? com.limasegura.limasegurabackend.model.OccurredAtPrecision.EXACT
                : com.limasegura.limasegurabackend.model.OccurredAtPrecision.APPROXIMATE);
        incident.setIncidentCount(1);
        incident.setCreatedAt(LocalDateTime.now());

        if (request.getDistrictName() != null && !request.getDistrictName().isBlank()) {
            List<District> matches = districtRepository.findByNameIgnoreCase(request.getDistrictName().trim());
            if (!matches.isEmpty()) {
                incident.setDistrict(matches.get(0));
            }
        }

        return mapToResponse(incidentRepository.save(incident));
    }

    /**
     * Un usuario confirma ("yo también lo vi") un reporte ciudadano.
     * Al llegar a 3 confirmaciones el incidente se publica y se dispara un evento async.
     */
    @Transactional
    public ConfirmationResponse confirm(Long incidentId, User user) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incidente no encontrado con id: " + incidentId));

        if (incident.getOrigin() != IncidentOrigin.CITIZEN) {
            throw new InvalidOperationException("Solo se pueden confirmar reportes ciudadanos");
        }
        if (incident.getStatus() != IncidentStatus.PENDING_REVIEW) {
            throw new InvalidOperationException("Este reporte ya no está pendiente de confirmación");
        }
        if (incident.getReporter() != null && incident.getReporter().getId().equals(user.getId())) {
            throw new InvalidOperationException("No puedes confirmar tu propio reporte");
        }
        if (confirmationRepository.existsByIncidentIdAndUserId(incidentId, user.getId())) {
            throw new DuplicateResourceException("Ya confirmaste este reporte");
        }

        Confirmation confirmation = new Confirmation();
        confirmation.setIncident(incident);
        confirmation.setUser(user);
        confirmationRepository.save(confirmation);

        long total = confirmationRepository.countByIncidentId(incidentId);
        if (total >= CONFIRMATIONS_TO_PUBLISH) {
            incident.setStatus(IncidentStatus.PUBLISHED);
            incident.setConfidence(1.0);
            incidentRepository.save(incident);
            eventPublisher.publishEvent(new IncidentPublishedEvent(this, incident.getId()));
        }

        return ConfirmationResponse.builder()
                .incidentId(incidentId)
                .confirmations(total)
                .required(CONFIRMATIONS_TO_PUBLISH)
                .status(incident.getStatus())
                .build();
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
                .reporterId(incident.getReporter() != null ? incident.getReporter().getId() : null)
                .confirmationsCount(confirmationRepository.countByIncidentId(incident.getId()))
                .build();
    }
}

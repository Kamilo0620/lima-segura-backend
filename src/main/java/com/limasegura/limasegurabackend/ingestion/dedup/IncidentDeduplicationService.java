package com.limasegura.limasegurabackend.ingestion.dedup;

import com.limasegura.limasegurabackend.model.GeoPrecision;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.IncidentType;
import com.limasegura.limasegurabackend.model.OccurredAtPrecision;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Servicio de deduplicación y clusterización espacio-temporal según el
 * Informe Técnico de Ingesta para Lima Segura (Sección 7.4):
 * - Ventana temporal: 6 horas.
 * - Radio espacial: 300 metros a la redonda (Fórmula de Haversine).
 * - Fusión de fuentes: anexar como fuente verificada y aumentar la confianza.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentDeduplicationService {

    private static final double MAX_DISTANCE_METERS = 300.0;
    private static final int TEMPORAL_WINDOW_HOURS = 6;

    private final IncidentRepository incidentRepository;

    @Transactional
    public Incident deduplicateOrCreate(Incident newIncident, SourceItem sourceItem) {
        if (newIncident.getOccurredAt() == null || newIncident.getDistrict() == null) {
            newIncident.getSourceItems().add(sourceItem);
            return incidentRepository.save(newIncident);
        }

        LocalDateTime start = newIncident.getOccurredAt().minusHours(TEMPORAL_WINDOW_HOURS);
        LocalDateTime end = newIncident.getOccurredAt().plusHours(TEMPORAL_WINDOW_HOURS);

        // Evaluar todos los incidentes recientes en el distrito dentro de la ventana temporal (incluye variantes de tipo como SHOOTING/HOMICIDE)
        List<Incident> candidates = incidentRepository.findDuplicateCandidatesWithinWindow(
                newIncident.getDistrict().getUbigeo(),
                start, end);
        if (candidates == null || candidates.isEmpty()) {
            candidates = incidentRepository.findDuplicateCandidates(
                    newIncident.getIncidentType(),
                    newIncident.getDistrict().getUbigeo(),
                    start, end);
        }

        for (Incident candidate : candidates) {
            boolean isDuplicate = false;
            boolean sameOrCompatibleType = candidate.getIncidentType() == newIncident.getIncidentType()
                    || areCompatibleTypes(candidate.getIncidentType(), newIncident.getIncidentType());
            double titleSimilarity = calculateJaccardSimilarity(candidate.getTitle(), newIncident.getTitle());

            // 1. Verificación por Radio Espacial (300 metros) si ambos poseen coordenadas
            if (candidate.getLat() != null && candidate.getLng() != null
                    && newIncident.getLat() != null && newIncident.getLng() != null) {

                double distance = calculateHaversineDistanceMeters(
                        candidate.getLat(), candidate.getLng(),
                        newIncident.getLat(), newIncident.getLng());

                if (distance <= MAX_DISTANCE_METERS) {
                    if (candidate.getIncidentType() == newIncident.getIncidentType()
                            || (sameOrCompatibleType && titleSimilarity >= 0.18)
                            || titleSimilarity > 0.25) {
                        log.info("Deduplicación espacial exitosa: idExistente={}, distancia={}m, tiempoDelta={}h",
                                candidate.getId(), Math.round(distance),
                                Math.abs(java.time.Duration.between(candidate.getOccurredAt(), newIncident.getOccurredAt()).toHours()));
                        isDuplicate = true;
                    }
                }
            }

            // 2. Fallback de similitud textual si no hay coordenadas precisas o los medios difieren en ubicación exacta
            if (!isDuplicate && (titleSimilarity > 0.35 || (sameOrCompatibleType && titleSimilarity > 0.28))) {
                log.info("Deduplicación textual exitosa: idExistente={}, similaridad={}",
                        candidate.getId(), String.format(java.util.Locale.US, "%.2f", titleSimilarity));
                isDuplicate = true;
            }

            if (isDuplicate) {
                if (candidate.getSourceItems() == null) {
                    candidate.setSourceItems(new HashSet<>());
                }
                candidate.getSourceItems().add(sourceItem);
                if (newIncident.getSourceItems() != null) {
                    candidate.getSourceItems().addAll(newIncident.getSourceItems());
                }
                candidate.setIncidentCount(Math.max(candidate.getIncidentCount() + 1, candidate.getSourceItems().size()));
                candidate.setConfidence(Math.min(1.0, candidate.getConfidence() + 0.15));

                // Mejorar dirección y coordenadas si el nuevo reporte aporta mayor precisión territorial
                boolean newIsPrecise = newIncident.getGeoPrecision() == GeoPrecision.ADDRESS
                        || newIncident.getGeoPrecision() == GeoPrecision.EXACT_ADDRESS
                        || newIncident.getGeoPrecision() == GeoPrecision.INTERSECTION;
                boolean existingIsCoarse = candidate.getLat() == null
                        || candidate.getGeoPrecision() == null
                        || candidate.getGeoPrecision() == GeoPrecision.DISTRICT
                        || candidate.getGeoPrecision() == GeoPrecision.DEPARTMENT;
                if (candidate.getAddress() == null && newIncident.getAddress() != null) {
                    candidate.setAddress(newIncident.getAddress());
                }
                if (newIncident.getLat() != null && existingIsCoarse && (candidate.getLat() == null || newIsPrecise)) {
                    candidate.setLat(newIncident.getLat());
                    candidate.setLng(newIncident.getLng());
                    candidate.setGeoPrecision(newIncident.getGeoPrecision());
                    if (newIncident.getAddress() != null && !newIncident.getAddress().isBlank()) {
                        candidate.setAddress(newIncident.getAddress());
                    }
                }
                // Mejorar hora real del evento si el nuevo reporte trajo precisión EXACTA
                if (candidate.getOccurredAtPrecision() != OccurredAtPrecision.EXACT
                        && newIncident.getOccurredAtPrecision() == OccurredAtPrecision.EXACT
                        && newIncident.getOccurredAt() != null) {
                    candidate.setOccurredAt(newIncident.getOccurredAt());
                    candidate.setOccurredAtPrecision(OccurredAtPrecision.EXACT);
                }
                if (candidate.getSummary() == null || (newIncident.getSummary() != null
                        && newIncident.getSummary().length() > candidate.getSummary().length())) {
                    candidate.setSummary(newIncident.getSummary());
                }

                return incidentRepository.save(candidate);
            }
        }

        newIncident.getSourceItems().add(sourceItem);
        return incidentRepository.save(newIncident);
    }

    private boolean areCompatibleTypes(IncidentType a, IncidentType b) {
        if (a == null || b == null) return false;
        if (a == b) return true;
        Set<IncidentType> lethalArmed = Set.of(IncidentType.HOMICIDE, IncidentType.SHOOTING, IncidentType.FEMICIDE, IncidentType.ASSAULT);
        Set<IncidentType> propertyForce = Set.of(IncidentType.ROBBERY, IncidentType.ARMED_ROBBERY, IncidentType.THEFT, IncidentType.VEHICLE_THEFT);
        Set<IncidentType> extortionCluster = Set.of(IncidentType.EXTORTION, IncidentType.SHOOTING, IncidentType.VANDALISM);
        return (lethalArmed.contains(a) && lethalArmed.contains(b))
                || (propertyForce.contains(a) && propertyForce.contains(b))
                || (extortionCluster.contains(a) && extortionCluster.contains(b));
    }

    public static double calculateHaversineDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0; // Radio de la Tierra en metros
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public double calculateJaccardSimilarity(String s1, String s2) {
        if (s1 == null || s2 == null) return 0.0;

        Set<String> words1 = new HashSet<>(Arrays.asList(s1.toLowerCase().split("\\W+")));
        Set<String> words2 = new HashSet<>(Arrays.asList(s2.toLowerCase().split("\\W+")));

        words1.remove("");
        words2.remove("");

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }
}

package com.limasegura.limasegurabackend.ingestion.dedup;

import com.limasegura.limasegurabackend.model.Incident;
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

        List<Incident> candidates = incidentRepository.findDuplicateCandidates(
                newIncident.getIncidentType(),
                newIncident.getDistrict().getUbigeo(),
                start, end);

        for (Incident candidate : candidates) {
            boolean isDuplicate = false;

            // 1. Verificación por Radio Espacial (300 metros) si ambos poseen coordenadas
            if (candidate.getLat() != null && candidate.getLng() != null
                    && newIncident.getLat() != null && newIncident.getLng() != null) {
                
                double distance = calculateHaversineDistanceMeters(
                        candidate.getLat(), candidate.getLng(),
                        newIncident.getLat(), newIncident.getLng());

                if (distance <= MAX_DISTANCE_METERS) {
                    double titleSimilarity = calculateJaccardSimilarity(candidate.getTitle(), newIncident.getTitle());
                    // Mismo tipo de delito dentro de 300m y 6h, o títulos similares
                    if (titleSimilarity > 0.25 || candidate.getIncidentType() == newIncident.getIncidentType()) {
                        log.info("Deduplicación espacial exitosa: idExistente={}, distancia={:.1f}m, tiempoDelta={}h",
                                candidate.getId(), distance,
                                Math.abs(java.time.Duration.between(candidate.getOccurredAt(), newIncident.getOccurredAt()).toHours()));
                        isDuplicate = true;
                    }
                }
            }

            // 2. Fallback de similitud textual si no hay coordenadas precisas
            if (!isDuplicate) {
                double similarity = calculateJaccardSimilarity(candidate.getTitle(), newIncident.getTitle());
                if (similarity > 0.35) {
                    log.info("Deduplicación textual exitosa: idExistente={}, similaridad={:.2f}",
                            candidate.getId(), similarity);
                    isDuplicate = true;
                }
            }

            if (isDuplicate) {
                // Anexar como fuente adicional verificada
                candidate.getSourceItems().add(sourceItem);
                candidate.setIncidentCount(candidate.getIncidentCount() + 1);
                // Subir nivel de confianza (hasta un tope de 1.0)
                candidate.setConfidence(Math.min(1.0, candidate.getConfidence() + 0.15));

                // Si el nuevo reporte trae una dirección o resumen más completo, enriquecer el existente
                if (candidate.getAddress() == null && newIncident.getAddress() != null) {
                    candidate.setAddress(newIncident.getAddress());
                }
                if (candidate.getLat() == null && newIncident.getLat() != null) {
                    candidate.setLat(newIncident.getLat());
                    candidate.setLng(newIncident.getLng());
                    candidate.setGeoPrecision(newIncident.getGeoPrecision());
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

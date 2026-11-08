package com.limasegura.limasegurabackend.ingestion.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limasegura.limasegurabackend.config.IngestionConfig;
import com.limasegura.limasegurabackend.config.LlmConfig;
import com.limasegura.limasegurabackend.dto.llm.LlmExtractionResponse;
import com.limasegura.limasegurabackend.dto.llm.LlmIncidentDto;
import com.limasegura.limasegurabackend.event.IncidentPublishedEvent;
import com.limasegura.limasegurabackend.ingestion.dedup.IncidentDeduplicationService;
import com.limasegura.limasegurabackend.ingestion.geo.DistrictResolver;
import com.limasegura.limasegurabackend.ingestion.geo.GeocodingService;
import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentExtractionService {

    private final SourceItemRepository sourceItemRepository;
    private final IngestionConfig ingestionConfig;
    private final LlmConfig llmConfig;
    private final LlmClient llmClient;
    private final PromptFactory promptFactory;
    private final ObjectMapper objectMapper;
    private final GeocodingService geocodingService;
    private final DistrictResolver districtResolver;
    private final IncidentDeduplicationService deduplicationService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void extractIncidents(Long sourceItemId) {
        SourceItem item = sourceItemRepository.findById(sourceItemId)
                .orElseThrow(() -> new IllegalArgumentException("SourceItem not found: " + sourceItemId));

        if (item.getStatus() != SourceItemStatus.CONTENT_READY) {
            log.debug("SourceItem {} is not in CONTENT_READY status, skipping", sourceItemId);
            return;
        }

        String contentText = item.getContentText();
        if (contentText == null || contentText.isBlank()) {
            item.setStatus(SourceItemStatus.FAILED);
            item.setError("Content text is empty");
            sourceItemRepository.save(item);
            return;
        }

        // RELEVANCE PREFILTER: check if content contains any domain keywords
        boolean relevant = false;
        if (ingestionConfig.getRelevanceKeywords() != null) {
            String lowerContent = contentText.toLowerCase();
            for (String keyword : ingestionConfig.getRelevanceKeywords()) {
                if (lowerContent.contains(keyword.toLowerCase())) {
                    relevant = true;
                    break;
                }
            }
        } else {
            relevant = true;
        }

        if (!relevant) {
            item.setStatus(SourceItemStatus.DISCARDED);
            sourceItemRepository.save(item);
            log.info("Discarded source item {} - no relevant keywords found", sourceItemId);
            return;
        }

        try {
            String systemPrompt = promptFactory.buildExtractionPrompt();
            String publishedAtStr = item.getPublishedAt() != null
                    ? item.getPublishedAt().toString()
                    : "Unknown";
            String userMessage = promptFactory.buildUserMessage(contentText, publishedAtStr);

            String llmResponse = llmClient.chat(systemPrompt, userMessage);
            LlmExtractionResponse extractionResponse;

            try {
                llmResponse = cleanJsonResponse(llmResponse);
                extractionResponse = objectMapper.readValue(llmResponse, LlmExtractionResponse.class);
            } catch (Exception e) {
                log.warn("Failed to parse LLM response, retrying with repair prompt: {}", e.getMessage());
                String repairPrompt = "Output ONLY valid JSON with no markdown code blocks. " + systemPrompt;
                llmResponse = llmClient.chat(repairPrompt, userMessage);
                llmResponse = cleanJsonResponse(llmResponse);
                extractionResponse = objectMapper.readValue(llmResponse, LlmExtractionResponse.class);
            }

            if (extractionResponse.getIncidents() == null || extractionResponse.getIncidents().isEmpty()) {
                item.setStatus(SourceItemStatus.DISCARDED);
                sourceItemRepository.save(item);
                log.info("No incidents extracted from source item {}", sourceItemId);
                return;
            }

            for (LlmIncidentDto dto : extractionResponse.getIncidents()) {
                Incident incident = mapDtoToIncident(dto, item);

                // Geocode the incident
                geocodingService.geocodeIncident(incident);

                // Deduplicate or create
                Incident saved = deduplicationService.deduplicateOrCreate(incident, item);

                // Determine publication status
                if (saved.getConfidence() >= ingestionConfig.getMinConfidence()
                        && saved.getGeoPrecision() != null
                        && (saved.getGeoPrecision() == GeoPrecision.ADDRESS || saved.getGeoPrecision() == GeoPrecision.DISTRICT)) {
                    saved.setStatus(IncidentStatus.PUBLISHED);
                } else {
                    saved.setStatus(IncidentStatus.PENDING_REVIEW);
                }

                eventPublisher.publishEvent(new IncidentPublishedEvent(this, saved.getId()));
            }

            item.setStatus(SourceItemStatus.EXTRACTED);
            sourceItemRepository.save(item);
            log.info("Extracted {} incidents from source item {}",
                    extractionResponse.getIncidents().size(), sourceItemId);

        } catch (Exception e) {
            log.warn("LLM extraction failed for item {} ({}), falling back to heuristic extraction: {}",
                    sourceItemId, e.getClass().getSimpleName(), e.getMessage());
            try {
                fallbackHeuristicExtraction(item, contentText);
            } catch (Exception fallbackEx) {
                log.error("Both LLM and fallback heuristic extraction failed for item {}: {}", sourceItemId, fallbackEx.getMessage());
                item.setAttempts(item.getAttempts() + 1);
                item.setError(e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 2000)) : "Unknown error");
                if (item.getAttempts() >= ingestionConfig.getMaxAttempts()) {
                    item.setStatus(SourceItemStatus.FAILED);
                }
                sourceItemRepository.save(item);
            }
        }
    }

    private void fallbackHeuristicExtraction(SourceItem item, String contentText) {
        log.info("Running heuristic fallback extraction for source item {}", item.getId());
        String textLower = ((item.getTitle() != null ? item.getTitle() : "") + " " + contentText).toLowerCase();

        // 1. Detect incident type
        IncidentType type = IncidentType.OTHER;
        if (textLower.contains("homicidio") || textLower.contains("asesin") || textLower.contains("sicari") || textLower.contains("crimen") || textLower.contains("matar") || textLower.contains("disparo") || textLower.contains("acribill") || textLower.contains("fallecid")) {
            type = IncidentType.HOMICIDE;
        } else if (textLower.contains("extorsi") || textLower.contains("cupo") || textLower.contains("dinamita") || textLower.contains("explosivo") || textLower.contains("amenaza")) {
            type = IncidentType.EXTORTION;
        } else if (textLower.contains("secuestro") || textLower.contains("plagio") || textLower.contains("rehén")) {
            type = IncidentType.KIDNAPPING;
        } else if (textLower.contains("arma de fuego") || textLower.contains("encañon") || textLower.contains("asalto a mano armada") || textLower.contains("banco") || textLower.contains("cambista") || textLower.contains("armado")) {
            type = IncidentType.ARMED_ROBBERY;
        } else if (textLower.contains("robo") || textLower.contains("asalto") || textLower.contains("asaltaron") || textLower.contains("delincuent")) {
            type = IncidentType.ROBBERY;
        } else if (textLower.contains("hurto") || textLower.contains("carterist") || textLower.contains("arrebato") || textLower.contains("celular")) {
            type = IncidentType.THEFT;
        } else if (textLower.contains("droga") || textLower.contains("pbc") || textLower.contains("microcomercializ") || textLower.contains("coca")) {
            type = IncidentType.DRUG_TRAFFICKING;
        }

        // 2. Resolve District from text
        District district = districtResolver.resolveFromText((item.getTitle() != null ? item.getTitle() : "") + " " + contentText);

        Incident incident = new Incident();
        incident.setIncidentType(type);
        IncidentOrigin origin = item.getNewsSource().getType() == SourceType.YOUTUBE_CHANNEL
                ? IncidentOrigin.YOUTUBE
                : IncidentOrigin.NEWSPAPER;
        incident.setOrigin(origin);
        incident.setTitle(item.getTitle() != null ? item.getTitle().substring(0, Math.min(item.getTitle().length(), 200)) : "Incidente reportado en noticias");
        String cleanContent = contentText.replaceAll("\\s+", " ").trim();
        String summary = cleanContent.length() > 300 ? cleanContent.substring(0, 300) + "..." : cleanContent;
        incident.setSummary(summary);
        // Sin LLM la extracción es poco confiable: confianza baja y requiere revisión de un admin
        incident.setConfidence(0.3);
        incident.setLlmModel("nlp-heuristic-v1");
        incident.setEvidenceExcerpt(summary.length() > 200 ? summary.substring(0, 200) : summary);
        incident.setVictimsCount(1);
        incident.setIncidentCount(1);
        incident.setOccurredAt(item.getPublishedAt() != null ? item.getPublishedAt() : LocalDateTime.now());
        incident.setOccurredAtPrecision(OccurredAtPrecision.APPROXIMATE);

        if (district != null) {
            incident.setDistrict(district);
        }

        // Geocode
        geocodingService.geocodeIncident(incident);

        // Deduplicate and save
        Incident saved = deduplicationService.deduplicateOrCreate(incident, item);
        // No se publica automáticamente: un admin la revisa en el panel (PATCH /admin/incidents/{id})
        saved.setStatus(IncidentStatus.PENDING_REVIEW);

        item.setStatus(SourceItemStatus.EXTRACTED);
        sourceItemRepository.save(item);
        log.info("Successfully extracted incident via heuristic fallback for item {}: {} ({}) in {}",
                item.getId(), saved.getTitle(), saved.getIncidentType(), saved.getDistrict() != null ? saved.getDistrict().getName() : "Lima");
    }

    private Incident mapDtoToIncident(LlmIncidentDto dto, SourceItem item) {
        Incident incident = new Incident();

        // Map incident type
        try {
            incident.setIncidentType(IncidentType.valueOf(dto.getIncident_type()));
        } catch (Exception e) {
            incident.setIncidentType(IncidentType.OTHER);
        }

        // Set origin based on source type
        IncidentOrigin origin = item.getNewsSource().getType() == SourceType.YOUTUBE_CHANNEL
                ? IncidentOrigin.YOUTUBE
                : IncidentOrigin.NEWSPAPER;
        incident.setOrigin(origin);

        // Basic fields
        incident.setTitle(dto.getTitle() != null ? dto.getTitle().substring(0, Math.min(dto.getTitle().length(), 200)) : null);
        incident.setSummary(dto.getSummary());
        incident.setConfidence(dto.getConfidence() != null ? dto.getConfidence() : 0.5);
        incident.setLlmModel(llmConfig.getModel());
        incident.setEvidenceExcerpt(dto.getEvidence_excerpt());
        incident.setVictimsCount(dto.getVictims_count());
        incident.setIncidentCount(1);
        incident.setStatus(IncidentStatus.PENDING_REVIEW);

        // Parse occurred_at
        if (dto.getOccurred_at() != null && !dto.getOccurred_at().isBlank()) {
            try {
                // Try ISO offset datetime first (e.g., 2026-09-23T21:30:00-05:00)
                OffsetDateTime odt = OffsetDateTime.parse(dto.getOccurred_at());
                incident.setOccurredAt(odt.toLocalDateTime());
            } catch (Exception e1) {
                try {
                    incident.setOccurredAt(LocalDateTime.parse(dto.getOccurred_at(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                } catch (Exception e2) {
                    incident.setOccurredAt(item.getPublishedAt() != null ? item.getPublishedAt() : LocalDateTime.now());
                }
            }
        } else {
            incident.setOccurredAt(item.getPublishedAt() != null ? item.getPublishedAt() : LocalDateTime.now());
        }

        // Parse occurred_at_precision
        try {
            incident.setOccurredAtPrecision(OccurredAtPrecision.valueOf(dto.getOccurred_at_precision()));
        } catch (Exception e) {
            incident.setOccurredAtPrecision(OccurredAtPrecision.UNKNOWN);
        }

        // Location
        if (dto.getLocation() != null) {
            LlmIncidentDto.LlmLocationDto loc = dto.getLocation();
            incident.setAddress(loc.getAddress_text());

            // Try to resolve district
            if (loc.getDistrict() != null) {
                District district = districtResolver.resolve(loc.getDistrict(), loc.getProvince(), loc.getDepartment());
                incident.setDistrict(district);
            }
        }

        // Initialize source items set
        incident.setSourceItems(new HashSet<>());

        return incident;
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        response = response.trim();
        if (response.startsWith("```json")) {
            response = response.substring(7);
        } else if (response.startsWith("```")) {
            response = response.substring(3);
        }
        if (response.endsWith("```")) {
            response = response.substring(0, response.length() - 3);
        }
        return response.trim();
    }
}

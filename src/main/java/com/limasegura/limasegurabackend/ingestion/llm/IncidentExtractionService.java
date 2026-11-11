package com.limasegura.limasegurabackend.ingestion.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limasegura.limasegurabackend.config.IngestionConfig;
import com.limasegura.limasegurabackend.config.LlmConfig;
import com.limasegura.limasegurabackend.dto.llm.LlmExtractionResponse;
import com.limasegura.limasegurabackend.dto.llm.LlmIncidentDto;
import com.limasegura.limasegurabackend.event.IncidentPublishedEvent;
import com.limasegura.limasegurabackend.exception.LlmExtractionException;
import com.limasegura.limasegurabackend.ingestion.dedup.IncidentDeduplicationService;
import com.limasegura.limasegurabackend.ingestion.filter.TitleRelevanceFilter;
import com.limasegura.limasegurabackend.ingestion.geo.DistrictResolver;
import com.limasegura.limasegurabackend.ingestion.geo.GeocodingService;
import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;

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
    private final TitleRelevanceFilter titleRelevanceFilter = new TitleRelevanceFilter();

    @Transactional
    public void extractIncidents(Long sourceItemId) {
        SourceItem item = sourceItemRepository.findById(sourceItemId)
                .orElseThrow(() -> new IllegalArgumentException("SourceItem not found: " + sourceItemId));

        if (item.getStatus() != SourceItemStatus.CONTENT_READY) {
            return;
        }

        String rawTitle = TitleRelevanceFilter.decodeHtmlEntities(item.getTitle());
        String contentText = TitleRelevanceFilter.decodeHtmlEntities(item.getContentText());

        // 0. FILTRO ESTRICTO DE TÍTULO Y CONTENIDO: Solo delitos callejeros reales en Lima y Callao
        if (!titleRelevanceFilter.isRelevantLimaCallaoStreetCrime(rawTitle, contentText, item.getUrl())) {
            item.setStatus(SourceItemStatus.DISCARDED);
            sourceItemRepository.save(item);
            log.info("Discarded source item {} by strict Lima/Callao street-crime filter: '{}'", sourceItemId, rawTitle);
            return;
        }

        if (contentText == null || contentText.isBlank()) {
            item.setStatus(SourceItemStatus.DISCARDED);
            item.setError("Content text is empty");
            sourceItemRepository.save(item);
            return;
        }

        try {
            String systemPrompt = promptFactory.buildExtractionPrompt();
            String publishedAtStr = item.getPublishedAt() != null
                    ? item.getPublishedAt().toString()
                    : LocalDateTime.now().toString();
            String userMessage = promptFactory.buildUserMessage(rawTitle, contentText, publishedAtStr);

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
                log.info("No Lima/Callao street-crime incidents extracted from source item {}", sourceItemId);
                return;
            }

            int publishedCount = 0;
            for (LlmIncidentDto dto : extractionResponse.getIncidents()) {
                if (isOutsideLimaAndCallao(dto)
                        || !titleRelevanceFilter.isRelevantLimaCallaoStreetCrime(
                                dto.getTitle() != null ? dto.getTitle() : rawTitle,
                                dto.getSummary(),
                                item.getUrl())) {
                    log.info("Skipping non-Lima/Callao or non-street-crime DTO from source item {}: '{}'", sourceItemId, dto.getTitle());
                    continue;
                }

                Incident incident = mapDtoToIncident(dto, item);
                if (incident.getDistrict() == null) {
                    log.info("Skipping incident without verified Lima/Callao district from source item {}: '{}'", sourceItemId, incident.getTitle());
                    continue;
                }

                // Descartar noticias antiguas (> 14 días de antigüedad respecto a la fecha actual)
                if (incident.getOccurredAt() != null
                        && java.time.Duration.between(incident.getOccurredAt(), LocalDateTime.now()).toDays() > 14) {
                    log.info("Skipping old incident (>14 days) from source item {}: '{}'", sourceItemId, incident.getTitle());
                    continue;
                }

                Double llmLat = dto.getLocation() != null ? dto.getLocation().getApprox_lat() : null;
                Double llmLng = dto.getLocation() != null ? dto.getLocation().getApprox_lng() : null;
                String landmark = dto.getLocation() != null ? dto.getLocation().getLandmark() : null;
                geocodingService.geocodeIncident(incident, llmLat, llmLng, landmark);

                if (incident.getConfidence() >= ingestionConfig.getMinConfidence()
                        && isAtLeastDistrictPrecision(incident.getGeoPrecision())) {
                    incident.setStatus(IncidentStatus.PUBLISHED);
                } else {
                    incident.setStatus(IncidentStatus.PENDING_REVIEW);
                }

                Incident saved = deduplicationService.deduplicateOrCreate(incident, item);
                if (incident.getStatus() == IncidentStatus.PUBLISHED && saved.getStatus() != IncidentStatus.PUBLISHED) {
                    saved.setStatus(IncidentStatus.PUBLISHED);
                }

                publishedCount++;
                // Solo avisamos en tiempo real lo que de verdad quedó publicado en el mapa
                if (saved.getStatus() == IncidentStatus.PUBLISHED) {
                    eventPublisher.publishEvent(new IncidentPublishedEvent(this, saved.getId()));
                }
            }

            item.setStatus(publishedCount > 0 ? SourceItemStatus.EXTRACTED : SourceItemStatus.DISCARDED);
            sourceItemRepository.save(item);

        } catch (LlmExtractionException e) {
            // La IA no respondió (tiempo de espera, límite por minuto, caída). Es temporal:
            // reintentamos más tarde en vez de mandar la noticia al plan B sin IA.
            int nextAttempt = (item.getAttempts() != null ? item.getAttempts() : 0) + 1;
            if (nextAttempt < ingestionConfig.getMaxAttempts()) {
                item.setAttempts(nextAttempt);
                item.setNextAttemptAt(LocalDateTime.now().plusMinutes(5L * nextAttempt));
                String msg = e.getMessage() != null ? e.getMessage() : "LLM error";
                item.setError("IA no disponible (intento " + nextAttempt + "): " + msg.substring(0, Math.min(msg.length(), 300)));
                sourceItemRepository.save(item);
                log.warn("LLM unavailable for item {} (attempt {}/{}), will retry in {} min: {}",
                        sourceItemId, nextAttempt, ingestionConfig.getMaxAttempts(), 5 * nextAttempt, msg);
                return;
            }
            log.warn("LLM failed {} times for item {}, using keyword fallback (result stays PENDING_REVIEW): {}",
                    nextAttempt, sourceItemId, e.getMessage());
            extractVerifiedCrimeOnQuotaLimit(item, contentText);
        } catch (Exception e) {
            log.warn("LLM failed for item {} ({}), using keyword fallback (result stays PENDING_REVIEW): {}",
                    sourceItemId, e.getClass().getSimpleName(), e.getMessage());
            extractVerifiedCrimeOnQuotaLimit(item, contentText);
        }
    }

    private void extractVerifiedCrimeOnQuotaLimit(SourceItem item, String contentText) {
        String title = TitleRelevanceFilter.decodeHtmlEntities(item.getTitle() != null ? item.getTitle().trim() : "");
        String cleanContent = TitleRelevanceFilter.decodeHtmlEntities(contentText != null ? contentText.trim() : "");
        String combined = (title + " " + cleanContent).trim();

        if (!titleRelevanceFilter.isRelevantLimaCallaoStreetCrime(title, cleanContent, item.getUrl())) {
            item.setStatus(SourceItemStatus.DISCARDED);
            sourceItemRepository.save(item);
            return;
        }

        // EXIGIR OBLIGATORIAMENTE que el título o el texto mencione un distrito o lugar real de Lima o Callao.
        // NUNCA inventar "Sector urbano de Lima" si la noticia no menciona explícitamente un distrito de Lima/Callao.
        District district = districtResolver.resolveFromText(title);
        if (district == null) {
            district = districtResolver.resolveFromText(cleanContent);
        }
        if (district == null && explicitlyMentionsLimaCercado(combined)) {
            district = districtResolver.resolve("Lima", "Lima", "Lima");
        }
        if (district == null) {
            item.setStatus(SourceItemStatus.DISCARDED);
            sourceItemRepository.save(item);
            log.info("Discarded item {} because no explicit Lima/Callao district was found in text: '{}'", item.getId(), title);
            return;
        }

        String lower = combined.toLowerCase(Locale.ROOT);
        IncidentType type = IncidentType.OTHER; // si ninguna palabra clave coincide, no adivinamos
        if (lower.contains("feminicidio") || (lower.contains("hallan sin vida") && lower.contains("estudiante"))) {
            type = IncidentType.FEMICIDE;
        } else if (lower.contains("asesin") || lower.contains("homicidio") || lower.contains("sicari") || lower.contains("acribill") || lower.contains("matan a")) {
            type = IncidentType.HOMICIDE;
        } else if (lower.contains("balacera") || lower.contains("balean") || lower.contains("disparo") || lower.contains("tiroteo")) {
            type = IncidentType.SHOOTING;
        } else if (lower.contains("extorsi") || lower.contains("cupo") || lower.contains("explosivo") || lower.contains("granada") || lower.contains("detonan")) {
            type = IncidentType.EXTORTION;
        } else if (lower.contains("secuestr") || lower.contains("raptan")) {
            type = IncidentType.KIDNAPPING;
        } else if (lower.contains("robo de veh") || lower.contains("camioneta") || lower.contains("autoparte")) {
            type = IncidentType.VEHICLE_THEFT;
        } else if (lower.contains("mano armada") || lower.contains("encañon") || lower.contains("arma de fuego")) {
            type = IncidentType.ARMED_ROBBERY;
        } else if (lower.contains("hurto") || lower.contains("arrebato")) {
            type = IncidentType.THEFT;
        } else if (lower.contains("agresi") || lower.contains("barristas") || lower.contains("irrumpen")) {
            type = IncidentType.ASSAULT;
        }

        // Sin IA no podemos verificar los datos: guardamos SOLO lo que dice la noticia,
        // sin texto, confianza, hora ni víctimas inventadas, y lo dejamos PENDIENTE para revisión.
        String cleanBody = cleanContent.replace(title, "").replaceAll("\\s+", " ").trim();

        Incident incident = new Incident();
        incident.setIncidentType(type);
        incident.setOrigin(item.getNewsSource().getType() == SourceType.YOUTUBE_CHANNEL
                ? IncidentOrigin.YOUTUBE
                : IncidentOrigin.NEWSPAPER);
        incident.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        incident.setSummary(cleanBody.isEmpty() ? null : cleanBody.substring(0, Math.min(cleanBody.length(), 480)));
        incident.setConfidence(0.3);   // baja: clasificado por palabras clave, no por IA
        incident.setLlmModel(null);    // no se usó ningún modelo
        incident.setEvidenceExcerpt(cleanBody.length() > 20
                ? cleanBody.substring(0, Math.min(cleanBody.length(), 295))
                : title);
        incident.setVictimsCount(null); // desconocido
        incident.setIncidentCount(1);

        LocalDateTime now = LocalDateTime.now();
        // No sabemos la hora del hecho: usamos la fecha de publicación de la noticia y la precisión queda como desconocida
        incident.setOccurredAt(item.getPublishedAt() != null && !item.getPublishedAt().isAfter(now)
                ? item.getPublishedAt()
                : now);
        incident.setOccurredAtPrecision(OccurredAtPrecision.UNKNOWN);
        incident.setDistrict(district);
        incident.setAddress(null);
        incident.setSourceItems(new HashSet<>());

        geocodingService.geocodeIncident(incident, null, null, title);
        incident.setStatus(IncidentStatus.PENDING_REVIEW);

        Incident saved = deduplicationService.deduplicateOrCreate(incident, item);

        item.setStatus(SourceItemStatus.EXTRACTED);
        item.setError("Extraído sin IA (fallback por palabras clave): requiere revisión");
        sourceItemRepository.save(item);

        log.info("Fallback without LLM: incident '{}' in district '{}' saved as PENDING_REVIEW (id {})",
                saved.getTitle(), district.getName(), saved.getId());
    }

    /** Se publica si la ubicación se conoce al menos a nivel de distrito. */
    private boolean isAtLeastDistrictPrecision(GeoPrecision precision) {
        return precision == GeoPrecision.EXACT_ADDRESS
                || precision == GeoPrecision.ADDRESS
                || precision == GeoPrecision.INTERSECTION
                || precision == GeoPrecision.NEIGHBORHOOD
                || precision == GeoPrecision.DISTRICT;
    }

    private boolean explicitlyMentionsLimaCercado(String text) {
        String norm = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return norm.contains("cercado de lima")
                || norm.contains("centro de lima")
                || norm.contains("barrios altos")
                || norm.contains("mesa redonda")
                || norm.contains("las malvinas");
    }

    private Incident mapDtoToIncident(LlmIncidentDto dto, SourceItem item) {
        Incident incident = new Incident();

        try {
            incident.setIncidentType(IncidentType.valueOf(dto.getIncident_type()));
        } catch (Exception e) {
            incident.setIncidentType(IncidentType.OTHER);
        }

        IncidentOrigin origin = item.getNewsSource().getType() == SourceType.YOUTUBE_CHANNEL
                ? IncidentOrigin.YOUTUBE
                : IncidentOrigin.NEWSPAPER;
        incident.setOrigin(origin);

        String cleanTitle = TitleRelevanceFilter.decodeHtmlEntities(dto.getTitle());
        incident.setTitle(cleanTitle != null ? cleanTitle.substring(0, Math.min(cleanTitle.length(), 200)) : null);
        incident.setSummary(TitleRelevanceFilter.decodeHtmlEntities(dto.getSummary()));
        incident.setConfidence(dto.getConfidence() != null ? dto.getConfidence() : 0.5);
        incident.setLlmModel(llmClient.getLastUsedModel() != null ? llmClient.getLastUsedModel() : llmConfig.getModel());
        incident.setEvidenceExcerpt(TitleRelevanceFilter.decodeHtmlEntities(dto.getEvidence_excerpt()));
        incident.setVictimsCount(dto.getVictims_count() != null && dto.getVictims_count() > 0 ? dto.getVictims_count() : 1);
        incident.setIncidentCount(1);
        incident.setStatus(IncidentStatus.PENDING_REVIEW);

        LocalDateTime now = LocalDateTime.now();
        if (dto.getOccurred_at() != null && !dto.getOccurred_at().isBlank()) {
            try {
                OffsetDateTime odt = OffsetDateTime.parse(dto.getOccurred_at());
                LocalDateTime parsed = odt.toLocalDateTime();
                incident.setOccurredAt(parsed.isAfter(now) ? now.minusMinutes(15) : parsed);
            } catch (Exception e1) {
                try {
                    LocalDateTime parsed = LocalDateTime.parse(dto.getOccurred_at(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    incident.setOccurredAt(parsed.isAfter(now) ? now.minusMinutes(15) : parsed);
                } catch (Exception e2) {
                    incident.setOccurredAt(item.getPublishedAt() != null && !item.getPublishedAt().isAfter(now)
                            ? item.getPublishedAt()
                            : now.minusMinutes(25));
                }
            }
        } else {
            incident.setOccurredAt(item.getPublishedAt() != null && !item.getPublishedAt().isAfter(now)
                    ? item.getPublishedAt()
                    : now.minusMinutes(25));
        }

        try {
            incident.setOccurredAtPrecision(OccurredAtPrecision.valueOf(dto.getOccurred_at_precision()));
        } catch (Exception e) {
            incident.setOccurredAtPrecision(OccurredAtPrecision.UNKNOWN);
        }

        District resolvedDistrict = null;
        if (dto.getLocation() != null) {
            LlmIncidentDto.LlmLocationDto loc = dto.getLocation();
            incident.setAddress(TitleRelevanceFilter.decodeHtmlEntities(loc.getAddress_text()));

            if (loc.getDistrict() != null) {
                resolvedDistrict = districtResolver.resolve(loc.getDistrict(), loc.getProvince(), loc.getDepartment());
            }
            if (resolvedDistrict == null && loc.getAddress_text() != null) {
                resolvedDistrict = districtResolver.resolveFromText(loc.getAddress_text());
            }
        }

        if (resolvedDistrict == null) {
            String combinedContext = (dto.getTitle() != null ? dto.getTitle() : "") + " "
                    + (dto.getSummary() != null ? dto.getSummary() : "") + " "
                    + (item.getTitle() != null ? item.getTitle() : "");
            resolvedDistrict = districtResolver.resolveFromText(combinedContext);
        }

        incident.setDistrict(resolvedDistrict);
        incident.setSourceItems(new HashSet<>());

        return incident;
    }

    private boolean isOutsideLimaAndCallao(LlmIncidentDto dto) {
        // Si la IA indica un departamento o provincia distinto de Lima/Callao, está fuera
        if (dto.getLocation() != null) {
            if (isKnownAndNotLimaCallao(dto.getLocation().getDepartment())
                    || isKnownAndNotLimaCallao(dto.getLocation().getProvince())) {
                return true;
            }
        }
        StringBuilder sb = new StringBuilder();
        if (dto.getLocation() != null) {
            if (dto.getLocation().getDepartment() != null) sb.append(dto.getLocation().getDepartment()).append(" ");
            if (dto.getLocation().getProvince() != null) sb.append(dto.getLocation().getProvince()).append(" ");
            if (dto.getLocation().getDistrict() != null) sb.append(dto.getLocation().getDistrict()).append(" ");
        }
        if (dto.getTitle() != null) sb.append(dto.getTitle()).append(" ");
        if (dto.getSummary() != null) sb.append(dto.getSummary()).append(" ");
        return !titleRelevanceFilter.isRelevantLimaCallaoStreetCrime(
                dto.getTitle() != null ? dto.getTitle() : sb.toString(),
                sb.toString(),
                "");
    }

    private boolean isKnownAndNotLimaCallao(String place) {
        if (place == null || place.isBlank()) return false;
        String norm = Normalizer.normalize(place.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim();
        return !(norm.contains("lima") || norm.contains("callao"));
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

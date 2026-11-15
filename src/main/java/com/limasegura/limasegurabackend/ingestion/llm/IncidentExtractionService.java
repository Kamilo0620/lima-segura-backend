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
import com.limasegura.limasegurabackend.ingestion.telemetry.PipelineActivityTracker;
import com.limasegura.limasegurabackend.ingestion.verify.CrossVerificationService;
import com.limasegura.limasegurabackend.ingestion.verify.ExactEventParser;
import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final CrossVerificationService crossVerificationService;
    private final ExactEventParser exactEventParser;
    private final TitleRelevanceFilter titleRelevanceFilter = new TitleRelevanceFilter();

    @Autowired(required = false)
    private PipelineActivityTracker activityTracker;

    @Autowired
    public IncidentExtractionService(
            SourceItemRepository sourceItemRepository,
            IngestionConfig ingestionConfig,
            LlmConfig llmConfig,
            LlmClient llmClient,
            PromptFactory promptFactory,
            ObjectMapper objectMapper,
            GeocodingService geocodingService,
            DistrictResolver districtResolver,
            IncidentDeduplicationService deduplicationService,
            ApplicationEventPublisher eventPublisher,
            CrossVerificationService crossVerificationService,
            ExactEventParser exactEventParser
    ) {
        this.sourceItemRepository = sourceItemRepository;
        this.ingestionConfig = ingestionConfig;
        this.llmConfig = llmConfig;
        this.llmClient = llmClient;
        this.promptFactory = promptFactory;
        this.objectMapper = objectMapper;
        this.geocodingService = geocodingService;
        this.districtResolver = districtResolver;
        this.deduplicationService = deduplicationService;
        this.eventPublisher = eventPublisher;
        this.crossVerificationService = crossVerificationService;
        this.exactEventParser = exactEventParser != null ? exactEventParser : new ExactEventParser();
    }

    public IncidentExtractionService(
            SourceItemRepository sourceItemRepository,
            IngestionConfig ingestionConfig,
            LlmConfig llmConfig,
            LlmClient llmClient,
            PromptFactory promptFactory,
            ObjectMapper objectMapper,
            GeocodingService geocodingService,
            DistrictResolver districtResolver,
            IncidentDeduplicationService deduplicationService,
            ApplicationEventPublisher eventPublisher
    ) {
        this(sourceItemRepository, ingestionConfig, llmConfig, llmClient, promptFactory, objectMapper,
                geocodingService, districtResolver, deduplicationService, eventPublisher, null, new ExactEventParser());
    }

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

        // 0.5 Verificación cruzada autónoma multi-fuente (corpus local + Google/Bing News PE)
        CrossVerificationService.VerificationDossier dossier = crossVerificationService != null
                ? crossVerificationService.verifyAndFuse(item, rawTitle, contentText, item.getPublishedAt(), null)
                : null;
        String narrativeForLlm = dossier != null ? dossier.fusedNarrativeText() : contentText;

        try {
            String systemPrompt = promptFactory.buildExtractionPrompt();
            String publishedAtStr = item.getPublishedAt() != null
                    ? item.getPublishedAt().toString()
                    : LocalDateTime.now(ExactEventParser.LIMA_ZONE).toString();
            String userMessage = promptFactory.buildUserMessage(rawTitle, narrativeForLlm, publishedAtStr);

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
                String dtoTitle = dto.getTitle() != null ? dto.getTitle() : rawTitle;
                boolean excluded = isOutsideLimaAndCallao(dto)
                        || titleRelevanceFilter.isExcludedCrimeCategory(dtoTitle, dto.getSummary());
                boolean relevantDto = !excluded
                        && (titleRelevanceFilter.isRelevantLimaCallaoStreetCrime(dtoTitle, dto.getSummary(), item.getUrl())
                            || (isStreetCrimeType(dto.getIncident_type())
                                && titleRelevanceFilter.isRelevantLimaCallaoStreetCrime(rawTitle, dto.getSummary(), item.getUrl())));
                if (!relevantDto) {
                    log.info("Skipping non-Lima/Callao or non-street-crime DTO from source item {}: '{}'", sourceItemId, dto.getTitle());
                    if (activityTracker != null) {
                        activityTracker.record("FILTER_SKIP", "WARN",
                                item.getNewsSource() != null ? item.getNewsSource().getName() : "Extracción",
                                "Descartado DTO no elegible: " + dtoTitle);
                    }
                    continue;
                }

                Incident incident = mapDtoToIncident(dto, item);
                if (incident.getDistrict() == null) {
                    log.info("Skipping incident without verified Lima/Callao district from source item {}: '{}'", sourceItemId, incident.getTitle());
                    continue;
                }

                // Descartar noticias antiguas (> 14 días de antigüedad respecto a la fecha actual)
                if (incident.getOccurredAt() != null
                        && java.time.Duration.between(incident.getOccurredAt(), LocalDateTime.now(ExactEventParser.LIMA_ZONE)).toDays() > 14) {
                    log.info("Skipping old incident (>14 days) from source item {}: '{}'", sourceItemId, incident.getTitle());
                    continue;
                }

                // Aplicar calle exacta y hora exacta determinadas por verificación cruzada cuando Gemini dejó datos genéricos
                applyCrossVerifiedRefinements(incident, dossier);

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
                attachCorroborationsToSaved(saved, dossier);

                publishedCount++;
                // Solo avisamos en tiempo real lo que de verdad quedó publicado en el mapa
                if (saved.getStatus() == IncidentStatus.PUBLISHED) {
                    eventPublisher.publishEvent(new IncidentPublishedEvent(this, saved.getId()));
                    if (activityTracker != null) {
                        String srcName = item.getNewsSource() != null ? item.getNewsSource().getName() : "Prensa/TV";
                        String distName = saved.getDistrict() != null ? saved.getDistrict().getName() : "Lima/Callao";
                        activityTracker.record("PUBLISHED", "INFO", srcName,
                                "Publicado #" + saved.getId() + " (" + distName + "): " + saved.getTitle());
                    }
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
            extractVerifiedCrimeOnQuotaLimit(item, contentText, dossier);
        } catch (Exception e) {
            log.warn("LLM failed for item {} ({}), using keyword fallback (result stays PENDING_REVIEW): {}",
                    sourceItemId, e.getClass().getSimpleName(), e.getMessage());
            extractVerifiedCrimeOnQuotaLimit(item, contentText, dossier);
        }
    }

    private void extractVerifiedCrimeOnQuotaLimit(
            SourceItem item,
            String contentText,
            CrossVerificationService.VerificationDossier dossier
    ) {
        String title = TitleRelevanceFilter.decodeHtmlEntities(item.getTitle() != null ? item.getTitle().trim() : "");
        String cleanContent = TitleRelevanceFilter.decodeHtmlEntities(contentText != null ? contentText.trim() : "");
        String fusedText = dossier != null && dossier.fusedNarrativeText() != null
                ? dossier.fusedNarrativeText()
                : cleanContent;
        String combined = (title + " " + fusedText).trim();

        if (!titleRelevanceFilter.isRelevantLimaCallaoStreetCrime(title, fusedText, item.getUrl())) {
            item.setStatus(SourceItemStatus.DISCARDED);
            sourceItemRepository.save(item);
            return;
        }

        // EXIGIR OBLIGATORIAMENTE que el título o el texto mencione un distrito o lugar real de Lima o Callao.
        District district = districtResolver.resolveFromText(title);
        if (district == null) {
            district = districtResolver.resolveFromText(fusedText);
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
        } else if (lower.contains("desmantel") || lower.contains("desarticul") || lower.contains("organizacion criminal") || lower.contains("banda criminal")) {
            type = IncidentType.ARMED_ROBBERY;
        } else if (lower.contains("robo de veh") || lower.contains("camioneta") || lower.contains("autoparte")) {
            type = IncidentType.VEHICLE_THEFT;
        } else if (lower.contains("mano armada") || lower.contains("encañon") || lower.contains("arma de fuego")) {
            type = IncidentType.ARMED_ROBBERY;
        } else if (lower.contains("hurto") || lower.contains("arrebato")) {
            type = IncidentType.THEFT;
        } else if (lower.contains("agresi") || lower.contains("irrumpen")) {
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

        LocalDateTime now = LocalDateTime.now(ExactEventParser.LIMA_ZONE);
        ExactEventParser.ResolvedEventTime resolvedTime = dossier != null && dossier.resolvedEventTime() != null
                ? dossier.resolvedEventTime()
                : exactEventParser.resolveExactOccurredAt(combined, item.getPublishedAt());
        if (resolvedTime.exactHourFound()) {
            incident.setOccurredAt(resolvedTime.occurredAt());
            incident.setOccurredAtPrecision(OccurredAtPrecision.EXACT);
        } else {
            incident.setOccurredAt(item.getPublishedAt() != null && !item.getPublishedAt().isAfter(now)
                    ? item.getPublishedAt()
                    : now);
            incident.setOccurredAtPrecision(OccurredAtPrecision.UNKNOWN);
        }

        incident.setDistrict(district);
        String exactStreet = dossier != null && dossier.suggestedExactAddress() != null
                ? dossier.suggestedExactAddress()
                : exactEventParser.extractExactStreetAddress(combined);
        incident.setAddress(exactStreet != null && !exactStreet.isBlank() ? exactStreet : null);
        incident.setSourceItems(new HashSet<>());

        geocodingService.geocodeIncident(incident, null, null, exactStreet != null ? exactStreet : title);
        incident.setStatus(IncidentStatus.PENDING_REVIEW);

        Incident saved = deduplicationService.deduplicateOrCreate(incident, item);
        attachCorroborationsToSaved(saved, dossier);

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

    private void applyCrossVerifiedRefinements(Incident incident, CrossVerificationService.VerificationDossier dossier) {
        if (dossier == null) {
            return;
        }
        String suggestedAddress = dossier.suggestedExactAddress();
        if (suggestedAddress != null && !suggestedAddress.isBlank()) {
            boolean currentIsWeak = incident.getAddress() == null
                    || incident.getAddress().isBlank()
                    || !exactEventParser.hasSpecificStreetDetail(incident.getAddress());
            if (currentIsWeak && exactEventParser.hasSpecificStreetDetail(suggestedAddress)) {
                incident.setAddress(suggestedAddress);
            } else if (incident.getAddress() == null || incident.getAddress().isBlank()) {
                incident.setAddress(suggestedAddress);
            }
        }

        if (dossier.resolvedEventTime() != null && dossier.resolvedEventTime().exactHourFound()
                && incident.getOccurredAtPrecision() != OccurredAtPrecision.EXACT) {
            incident.setOccurredAt(dossier.resolvedEventTime().occurredAt());
            incident.setOccurredAtPrecision(OccurredAtPrecision.EXACT);
        }

        if (!dossier.corroboratedSources().isEmpty()) {
            incident.setConfidence(Math.min(1.0, incident.getConfidence() + 0.05 * dossier.corroboratedSources().size()));
        }
    }

    private void attachCorroborationsToSaved(Incident saved, CrossVerificationService.VerificationDossier dossier) {
        if (crossVerificationService == null || dossier == null || dossier.corroboratedSources().isEmpty()) {
            return;
        }
        int added = crossVerificationService.attachCorroboratingSources(saved, dossier.corroboratedSources());
        if (added > 0) {
            saved.setIncidentCount(Math.max(saved.getIncidentCount(), saved.getSourceItems().size()));
            saved.setConfidence(Math.min(1.0, saved.getConfidence() + 0.04 * added));
        }
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

    private boolean isStreetCrimeType(String rawType) {
        if (rawType == null) return false;
        return switch (rawType.trim().toUpperCase(Locale.ROOT)) {
            case "ROBBERY", "ARMED_ROBBERY", "THEFT", "VEHICLE_THEFT",
                 "EXTORTION", "HOMICIDE", "SHOOTING", "KIDNAPPING",
                 "FEMICIDE", "ASSAULT", "VANDALISM" -> true;
            default -> false;
        };
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
        String combined = sb.toString();
        return titleRelevanceFilter.isExcludedCrimeCategory(dto.getTitle(), combined);
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

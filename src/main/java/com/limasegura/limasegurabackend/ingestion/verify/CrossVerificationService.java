package com.limasegura.limasegurabackend.ingestion.verify;

import com.limasegura.limasegurabackend.event.IncidentPublishedEvent;
import com.limasegura.limasegurabackend.ingestion.fetch.ArticleFetcher;
import com.limasegura.limasegurabackend.ingestion.filter.TitleRelevanceFilter;
import com.limasegura.limasegurabackend.ingestion.geo.GeocodingService;
import com.limasegura.limasegurabackend.ingestion.telemetry.PipelineActivityTracker;
import com.limasegura.limasegurabackend.model.GeoPrecision;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.NewsSource;
import com.limasegura.limasegurabackend.model.OccurredAtPrecision;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import com.limasegura.limasegurabackend.model.SourceType;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import com.limasegura.limasegurabackend.repository.NewsSourceRepository;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio autónomo de verificación cruzada multi-fuente en segundo plano.
 * Busca reportes periodísticos corroboradores del mismo hecho delictivo en el corpus local
 * y en buscadores de noticias de Perú (Google News PE / Bing News PE) para fusionar hechos,
 * precisar la calle/cruce exacto, determinar la hora real de ocurrencia y evitar duplicados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrossVerificationService {

    private static final String GOOGLE_NEWS_PE_TEMPLATE =
            "https://news.google.com/rss/search?q=%s&hl=es-419&gl=PE&ceid=PE:es-419";
    private static final String BING_NEWS_PE_TEMPLATE =
            "https://www.bing.com/news/search?q=%s&format=RSS&mkt=es-PE";
    private static final String WEB_CORROBORATION_SOURCE_NAME = "Verificación Cruzada Web (Medios PE)";
    private static final Duration SEARCH_CACHE_TTL = Duration.ofMinutes(12);
    private static final int MAX_CORROBORATIONS = 4;
    private static final int MAX_FUSED_BODY_CHARS = 9000;

    private final SourceItemRepository sourceItemRepository;
    private final NewsSourceRepository newsSourceRepository;
    private final IncidentRepository incidentRepository;
    private final ArticleFetcher articleFetcher;
    private final ExactEventParser exactEventParser;
    private final GeocodingService geocodingService;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    private final TitleRelevanceFilter titleRelevanceFilter = new TitleRelevanceFilter();
    private final Map<String, CachedSearch> searchCache = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Value("${app.ingestion.cross-verify-web-enabled:true}")
    private boolean webSearchEnabled;

    @Autowired(required = false)
    private PipelineActivityTracker activityTracker;

    public record CorroboratedEvidence(
            String sourceName,
            String title,
            String url,
            String content,
            LocalDateTime publishedAt,
            Long existingSourceItemId
    ) {
    }

    public record VerificationDossier(
            String fusedNarrativeText,
            String suggestedExactAddress,
            ExactEventParser.ResolvedEventTime resolvedEventTime,
            List<CorroboratedEvidence> corroboratedSources,
            int externalMatchesCount
    ) {
    }

    public record RefinementStats(
            int inspected,
            int refinedGeo,
            int refinedTime,
            int corroboratedSourcesAdded
    ) {
    }

    private record RssSearchHit(String title, String url, String sourceName, String snippet, LocalDateTime publishedAt) {
    }

    private record CachedSearch(List<RssSearchHit> hits, Instant cachedAt) {
    }

    /**
     * Busca notas corroboradoras locales y web del mismo hecho para construir un dossier consolidado.
     */
    public VerificationDossier verifyAndFuse(
            SourceItem primaryItem,
            String rawTitle,
            String rawText,
            LocalDateTime referenceTime,
            String hintDistrict
    ) {
        LocalDateTime ref = referenceTime != null ? referenceTime : LocalDateTime.now(ExactEventParser.LIMA_ZONE);
        String safeTitle = rawTitle != null ? rawTitle : "";
        String safeBody = rawText != null ? rawText : "";

        Map<String, CorroboratedEvidence> matchedByUrlOrTitle = new LinkedHashMap<>();
        Long primaryId = primaryItem != null ? primaryItem.getId() : null;
        String primaryUrl = primaryItem != null ? primaryItem.getUrl() : null;

        // 1. Corroboración local rápida en base de datos (±48h)
        List<SourceItem> localCandidates = sourceItemRepository.findRecentForCorroboration(ref.minusHours(48));
        for (SourceItem candidate : localCandidates) {
            if (matchedByUrlOrTitle.size() >= MAX_CORROBORATIONS) {
                break;
            }
            if (Objects.equals(candidate.getId(), primaryId)) {
                continue;
            }
            if (primaryUrl != null && primaryUrl.equalsIgnoreCase(candidate.getUrl())) {
                continue;
            }
            if (!titleRelevanceFilter.isRelevantLimaCallaoStreetCrime(
                    candidate.getTitle(), candidate.getContentText(), candidate.getUrl())) {
                continue;
            }
            if (exactEventParser.isSameEventCandidate(
                    safeTitle, safeBody,
                    candidate.getTitle(), candidate.getContentText(),
                    ref, candidate.getPublishedAt())) {
                String sourceName = candidate.getNewsSource() != null ? candidate.getNewsSource().getName() : "Medio Local";
                String dedupKey = normalizeDedupKey(candidate.getUrl(), candidate.getTitle());
                matchedByUrlOrTitle.putIfAbsent(dedupKey, new CorroboratedEvidence(
                        sourceName,
                        candidate.getTitle(),
                        candidate.getUrl(),
                        truncate(candidate.getContentText(), 2200),
                        candidate.getPublishedAt(),
                        candidate.getId()
                ));
            }
        }

        // 2. Corroboración autónoma en la web (Google News PE + Bing News PE)
        int externalMatches = 0;
        if (webSearchEnabled && matchedByUrlOrTitle.size() < MAX_CORROBORATIONS && !safeTitle.isBlank()) {
            String districtHint = hintDistrict != null ? hintDistrict
                    : exactEventParser.detectSingleDistrict(ExactEventParser.normalize(safeTitle + " " + safeBody));
            String query = exactEventParser.buildCorroborationQuery(safeTitle, districtHint);

            List<RssSearchHit> hits = searchWebCorroboration(query);
            int fullBodyFetches = 0;
            for (RssSearchHit hit : hits) {
                if (matchedByUrlOrTitle.size() >= MAX_CORROBORATIONS) {
                    break;
                }
                if (primaryUrl != null && primaryUrl.equalsIgnoreCase(hit.url())) {
                    continue;
                }
                String dedupKey = normalizeDedupKey(hit.url(), hit.title());
                if (matchedByUrlOrTitle.containsKey(dedupKey)) {
                    continue;
                }
                if (!titleRelevanceFilter.isRelevantLimaCallaoStreetCrime(hit.title(), hit.snippet(), hit.url())) {
                    continue;
                }
                if (!exactEventParser.isSameEventCandidate(safeTitle, safeBody, hit.title(), hit.snippet(), ref, hit.publishedAt())) {
                    continue;
                }

                String evidenceContent = hit.snippet() != null ? hit.snippet() : hit.title();
                // Enlaces directos distintos a news.google.com permiten descargar el texto íntegro del medio
                if (fullBodyFetches < 2 && hit.url() != null && !hit.url().contains("news.google.com")) {
                    String fetched = articleFetcher.fetchCleanTextFromUrl(hit.url());
                    if (fetched != null && fetched.length() > evidenceContent.length()) {
                        evidenceContent = fetched;
                    }
                    fullBodyFetches++;
                }

                matchedByUrlOrTitle.put(dedupKey, new CorroboratedEvidence(
                        hit.sourceName(),
                        hit.title(),
                        hit.url(),
                        truncate(evidenceContent, 2200),
                        hit.publishedAt(),
                        null
                ));
                externalMatches++;
            }
        }

        List<CorroboratedEvidence> corroborated = new ArrayList<>(matchedByUrlOrTitle.values());

        // 3. Fusión de narrativa + mejor dirección exacta + hora real consolidada
        String fusedNarrative = buildFusedNarrative(safeTitle, safeBody, corroborated);
        String bestAddress = selectBestStreetAddress(safeTitle, safeBody, corroborated);
        ExactEventParser.ResolvedEventTime bestTime = selectBestResolvedTime(safeTitle, safeBody, ref, corroborated);

        return new VerificationDossier(
                fusedNarrative,
                bestAddress,
                bestTime,
                corroborated,
                externalMatches
        );
    }

    /**
     * Ejecuta un ciclo autónomo sobre incidentes publicados recientes con baja precisión geográfica,
     * hora no exacta o una sola fuente, refinando calle/hora y notificando actualizaciones por SSE.
     */
    public RefinementStats refineRecentIncidents() {
        List<Long> candidateIds = transactionTemplate.execute(status -> {
            LocalDateTime since = LocalDateTime.now(ExactEventParser.LIMA_ZONE).minusHours(72);
            List<Incident> candidates = incidentRepository.findRecentNeedingRefinement(since);
            List<Long> ids = new ArrayList<>();
            for (Incident inc : candidates) {
                ids.add(inc.getId());
                if (ids.size() >= 8) {
                    break;
                }
            }
            return ids;
        });

        if (candidateIds == null || candidateIds.isEmpty()) {
            return new RefinementStats(0, 0, 0, 0);
        }

        int inspected = 0;
        int refinedGeo = 0;
        int refinedTime = 0;
        int addedSources = 0;

        for (Long incidentId : candidateIds) {
            IncidentSnapshot snapshot = transactionTemplate.execute(status ->
                    incidentRepository.findByIdWithDetails(incidentId).map(IncidentSnapshot::from).orElse(null)
            );
            if (snapshot == null) {
                continue;
            }
            inspected++;

            VerificationDossier dossier = verifyAndFuse(
                    null,
                    snapshot.title(),
                    snapshot.rawBody(),
                    snapshot.referenceTime(),
                    snapshot.districtName()
            );

            if (dossier.corroboratedSources().isEmpty()
                    && dossier.suggestedExactAddress() == null
                    && !dossier.resolvedEventTime().exactHourFound()) {
                continue;
            }

            RefinementOutcome outcome = transactionTemplate.execute(status ->
                    applyRefinementInTx(incidentId, dossier)
            );
            if (outcome != null) {
                refinedGeo += outcome.geoImproved() ? 1 : 0;
                refinedTime += outcome.timeImproved() ? 1 : 0;
                addedSources += outcome.sourcesAdded();
                if (outcome.anyChange()) {
                    eventPublisher.publishEvent(new IncidentPublishedEvent(this, incidentId));
                }
            }
        }

        if (inspected > 0) {
            log.info("Cross-verification cycle completed: inspected={}, refinedGeo={}, refinedTime={}, sourcesAdded={}",
                    inspected, refinedGeo, refinedTime, addedSources);
        }
        return new RefinementStats(inspected, refinedGeo, refinedTime, addedSources);
    }

    /**
     * Adjunta al incidente las fuentes corroboradoras locales o descubiertas en web sin duplicados.
     */
    public int attachCorroboratingSources(Incident incident, List<CorroboratedEvidence> corroborated) {
        if (incident == null || corroborated == null || corroborated.isEmpty()) {
            return 0;
        }
        int added = 0;
        NewsSource webSource = null;

        for (CorroboratedEvidence ev : corroborated) {
            SourceItem itemToLink = null;
            if (ev.existingSourceItemId() != null) {
                itemToLink = sourceItemRepository.findById(ev.existingSourceItemId()).orElse(null);
            } else if (ev.url() != null && !ev.url().isBlank()) {
                if (webSource == null) {
                    webSource = findOrCreateCorroborationNewsSource(ev.sourceName());
                }
                String extId = sha256Hex(ev.url());
                itemToLink = sourceItemRepository.findByNewsSourceIdAndExternalId(webSource.getId(), extId)
                        .orElse(null);
                if (itemToLink == null) {
                    itemToLink = SourceItem.builder()
                            .newsSource(webSource)
                            .externalId(extId)
                            .url(truncate(ev.url(), 990))
                            .title(truncate(ev.title(), 490))
                            .publishedAt(ev.publishedAt())
                            .contentText(truncate(ev.content(), 4000))
                            .status(SourceItemStatus.EXTRACTED)
                            .build();
                    itemToLink = sourceItemRepository.save(itemToLink);
                }
            }

            if (itemToLink != null && !containsSourceItem(incident, itemToLink)) {
                incident.getSourceItems().add(itemToLink);
                added++;
            }
        }
        return added;
    }

    private RefinementOutcome applyRefinementInTx(Long incidentId, VerificationDossier dossier) {
        Incident incident = incidentRepository.findByIdWithDetails(incidentId).orElse(null);
        if (incident == null) {
            return null;
        }
        boolean geoImproved = false;
        boolean timeImproved = false;

        int sourcesAdded = attachCorroboratingSources(incident, dossier.corroboratedSources());
        if (sourcesAdded > 0) {
            incident.setIncidentCount(Math.max(1, incident.getIncidentCount() + sourcesAdded));
            incident.setConfidence(Math.min(1.0, Math.max(0.75, incident.getConfidence()) + 0.04 * sourcesAdded));
        }

        String candidateAddr = dossier.suggestedExactAddress();
        boolean coordsOutOfBounds = incident.getLat() == null || incident.getLng() == null
                || !geocodingService.isValidLimaCallaoCoord(incident.getLat(), incident.getLng(), incident.getDistrict());
        if ((candidateAddr != null && !candidateAddr.isBlank()
                && (incident.getGeoPrecision() == GeoPrecision.DISTRICT || exactEventParser.hasSpecificStreetDetail(candidateAddr)))
                || coordsOutOfBounds) {
            String previousAddr = incident.getAddress();
            GeoPrecision previousPrecision = incident.getGeoPrecision();
            Double prevLat = incident.getLat();
            Double prevLng = incident.getLng();
            if (candidateAddr != null && !candidateAddr.isBlank()) {
                incident.setAddress(candidateAddr);
            }
            geocodingService.geocodeIncident(incident, null, null, incident.getAddress());
            if (incident.getGeoPrecision() != previousPrecision
                    || !Objects.equals(previousAddr, incident.getAddress())
                    || !Objects.equals(prevLat, incident.getLat())
                    || !Objects.equals(prevLng, incident.getLng())) {
                geoImproved = coordsOutOfBounds
                        || incident.getGeoPrecision() == GeoPrecision.EXACT_ADDRESS
                        || incident.getGeoPrecision() == GeoPrecision.INTERSECTION
                        || incident.getGeoPrecision() == GeoPrecision.ADDRESS
                        || previousPrecision == GeoPrecision.DISTRICT;
            }
        }

        ExactEventParser.ResolvedEventTime rt = dossier.resolvedEventTime();
        if (rt != null && rt.exactHourFound() && incident.getOccurredAtPrecision() != OccurredAtPrecision.EXACT) {
            incident.setOccurredAt(rt.occurredAt());
            incident.setOccurredAtPrecision(OccurredAtPrecision.EXACT);
            timeImproved = true;
        }

        if (sourcesAdded > 0 || geoImproved || timeImproved) {
            incidentRepository.save(incident);
            return new RefinementOutcome(geoImproved, timeImproved, sourcesAdded, true);
        }
        return new RefinementOutcome(false, false, 0, false);
    }

    private NewsSource findOrCreateCorroborationNewsSource(String sourceHint) {
        List<NewsSource> all = newsSourceRepository.findAll();
        if (sourceHint != null && !sourceHint.isBlank()) {
            String normHint = ExactEventParser.normalize(sourceHint);
            for (NewsSource ns : all) {
                String normName = ExactEventParser.normalize(ns.getName());
                if (normName.contains(normHint) || normHint.contains(normName.split(" ")[0])) {
                    return ns;
                }
            }
        }
        for (NewsSource ns : all) {
            if (WEB_CORROBORATION_SOURCE_NAME.equalsIgnoreCase(ns.getName())) {
                return ns;
            }
        }
        return newsSourceRepository.save(NewsSource.builder()
                .name(WEB_CORROBORATION_SOURCE_NAME)
                .type(SourceType.NEWSPAPER_RSS)
                .url("https://news.google.com/rss/search?hl=es-419&gl=PE&ceid=PE:es-419")
                .active(false)
                .credibility(8.2)
                .pollIntervalMinutes(60)
                .build());
    }

    private boolean containsSourceItem(Incident incident, SourceItem candidate) {
        if (incident.getSourceItems() == null) {
            return false;
        }
        for (SourceItem existing : incident.getSourceItems()) {
            if (Objects.equals(existing.getId(), candidate.getId())) {
                return true;
            }
            if (existing.getUrl() != null && candidate.getUrl() != null
                    && existing.getUrl().equalsIgnoreCase(candidate.getUrl())) {
                return true;
            }
        }
        return false;
    }

    private List<RssSearchHit> searchWebCorroboration(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        CachedSearch cached = searchCache.get(query);
        if (cached != null && Duration.between(cached.cachedAt(), Instant.now()).compareTo(SEARCH_CACHE_TTL) < 0) {
            return cached.hits();
        }

        List<RssSearchHit> hits = new ArrayList<>();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);

        PipelineActivityTracker.OperationToken token = activityTracker != null
                ? activityTracker.startOperation(
                        "Buscando corroboración en Google/Bing News PE: \"" + truncate(query, 65) + "\"",
                        "WEB_SEARCH",
                        "Google/Bing News PE")
                : null;
        try {
            // Priorizar Google News PE y complementar con Bing News PE (que devuelve URLs directas)
            hits.addAll(fetchRssSearchFeed(String.format(GOOGLE_NEWS_PE_TEMPLATE, encoded), true));
            if (hits.size() < 6) {
                hits.addAll(fetchRssSearchFeed(String.format(BING_NEWS_PE_TEMPLATE, encoded), false));
            }
            if (activityTracker != null) {
                activityTracker.record("WEB_SEARCH", "INFO", "Google/Bing News PE",
                        "Búsqueda \"" + truncate(query, 55) + "\" -> " + hits.size() + " coincidencias web");
            }
        } finally {
            if (activityTracker != null && token != null) {
                activityTracker.finishOperation(token);
            }
        }

        searchCache.put(query, new CachedSearch(hits, Instant.now()));
        return hits;
    }

    private List<RssSearchHit> fetchRssSearchFeed(String feedUrl, boolean isGoogleNews) {
        List<RssSearchHit> hits = new ArrayList<>();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(feedUrl))
                    .timeout(Duration.ofSeconds(6))
                    .header("User-Agent", "LimaSeguraBot/2.0 (Security Verification Engine)")
                    .header("Accept", "application/rss+xml, application/xml, text/xml")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
                return hits;
            }

            Document doc = Jsoup.parse(resp.body(), feedUrl, Parser.xmlParser());
            int count = 0;
            for (Element item : doc.select("item")) {
                if (count >= 10) {
                    break;
                }
                String rawTitle = TitleRelevanceFilter.decodeHtmlEntities(item.selectFirst("title") != null
                        ? item.selectFirst("title").text() : "");
                String link = item.selectFirst("link") != null ? item.selectFirst("link").text().trim() : "";
                if (!isGoogleNews) {
                    link = unwrapBingUrl(link);
                }
                String sourceName = extractSourceName(item, rawTitle, isGoogleNews);
                String cleanTitle = cleanOutletSuffix(rawTitle);
                String descHtml = item.selectFirst("description") != null ? item.selectFirst("description").text() : "";
                String snippet = TitleRelevanceFilter.decodeHtmlEntities(Jsoup.parse(descHtml).text());
                LocalDateTime pubAt = parseRssDate(item.selectFirst("pubDate") != null
                        ? item.selectFirst("pubDate").text() : null);

                if (!cleanTitle.isBlank() && !link.isBlank()) {
                    hits.add(new RssSearchHit(cleanTitle, link, sourceName, snippet, pubAt));
                    count++;
                }
            }
        } catch (Exception e) {
            log.debug("RSS cross-verification search unavailable for {}: {}", feedUrl, e.getMessage());
        }
        return hits;
    }

    private String selectBestStreetAddress(String primaryTitle, String primaryBody, List<CorroboratedEvidence> corroborated) {
        String best = exactEventParser.extractExactStreetAddress(primaryTitle + " " + primaryBody);
        int bestScore = scoreAddressSpecificity(best);

        for (CorroboratedEvidence ev : corroborated) {
            String candidate = exactEventParser.extractExactStreetAddress(ev.title() + " " + ev.content());
            int score = scoreAddressSpecificity(candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private int scoreAddressSpecificity(String address) {
        if (address == null || address.isBlank()) return 0;
        String norm = ExactEventParser.normalize(address);
        int score = 10;
        if (norm.contains("cruce") || norm.contains("esquina") || norm.contains(" con ")) score += 40;
        if (norm.contains("cdra") || norm.contains("cuadra") || norm.contains("km")) score += 25;
        if (norm.matches(".*\\b\\d{1,5}\\b.*")) score += 15;
        return score;
    }

    private ExactEventParser.ResolvedEventTime selectBestResolvedTime(
            String primaryTitle,
            String primaryBody,
            LocalDateTime ref,
            List<CorroboratedEvidence> corroborated
    ) {
        ExactEventParser.ResolvedEventTime best = exactEventParser.resolveExactOccurredAt(
                primaryTitle + " " + primaryBody, ref);
        if (best.exactHourFound()) {
            return best;
        }
        for (CorroboratedEvidence ev : corroborated) {
            LocalDateTime evRef = ev.publishedAt() != null ? ev.publishedAt() : ref;
            ExactEventParser.ResolvedEventTime cand = exactEventParser.resolveExactOccurredAt(
                    ev.title() + " " + ev.content(), evRef);
            if (cand.exactHourFound()) {
                return cand;
            }
        }
        return best;
    }

    private String buildFusedNarrative(String primaryTitle, String primaryBody, List<CorroboratedEvidence> corroborated) {
        StringBuilder sb = new StringBuilder();
        sb.append("[FUENTE PRINCIPAL]\n");
        sb.append("Título: ").append(primaryTitle).append("\n");
        sb.append(truncate(primaryBody, 3500)).append("\n");

        int idx = 1;
        for (CorroboratedEvidence ev : corroborated) {
            if (sb.length() >= MAX_FUSED_BODY_CHARS) {
                break;
            }
            sb.append("\n[FUENTE CORROBORADORA #").append(idx++)
                    .append(" - ").append(ev.sourceName() != null ? ev.sourceName() : "Medio PE").append("]\n");
            sb.append("Título: ").append(ev.title()).append("\n");
            if (ev.content() != null && !ev.content().isBlank()) {
                sb.append(truncate(ev.content(), 1400)).append("\n");
            }
        }
        return truncate(sb.toString(), MAX_FUSED_BODY_CHARS);
    }

    private String extractSourceName(Element item, String rawTitle, boolean isGoogleNews) {
        Element src = item.selectFirst("source");
        if (src != null && !src.text().isBlank()) {
            return src.text().trim();
        }
        Element newsSrc = item.selectFirst("News|Source");
        if (newsSrc != null && !newsSrc.text().isBlank()) {
            return newsSrc.text().trim();
        }
        if (isGoogleNews && rawTitle.contains(" - ")) {
            return rawTitle.substring(rawTitle.lastIndexOf(" - ") + 3).trim();
        }
        return "Prensa PE";
    }

    private String cleanOutletSuffix(String title) {
        if (title == null) return "";
        int dash = title.lastIndexOf(" - ");
        if (dash > 20 && title.length() - dash <= 40) {
            return title.substring(0, dash).trim();
        }
        return title.trim();
    }

    private String unwrapBingUrl(String url) {
        if (url == null || !url.contains("bing.com/news/apiclick")) {
            return url;
        }
        try {
            int idx = url.indexOf("url=");
            if (idx >= 0) {
                String tail = url.substring(idx + 4);
                int amp = tail.indexOf('&');
                String encoded = amp >= 0 ? tail.substring(0, amp) : tail;
                return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return url;
    }

    private LocalDateTime parseRssDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(rawDate.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
            return zdt.withZoneSameInstant(ExactEventParser.LIMA_ZONE).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private String normalizeDedupKey(String url, String title) {
        if (url != null && !url.isBlank() && !url.contains("news.google.com")) {
            return url.trim().toLowerCase(Locale.ROOT);
        }
        return ExactEventParser.normalize(cleanOutletSuffix(title));
    }

    private String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            return String.valueOf(input.hashCode());
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private record IncidentSnapshot(String title, String rawBody, LocalDateTime referenceTime, String districtName) {
        static IncidentSnapshot from(Incident inc) {
            StringBuilder rawBody = new StringBuilder();
            if (inc.getSummary() != null) rawBody.append(inc.getSummary()).append("\n");
            if (inc.getAddress() != null) rawBody.append(inc.getAddress()).append("\n");
            if (inc.getSourceItems() != null) {
                for (SourceItem si : inc.getSourceItems()) {
                    if (si.getContentText() != null) {
                        rawBody.append(si.getContentText()).append("\n");
                    }
                }
            }
            LocalDateTime ref = inc.getOccurredAt() != null ? inc.getOccurredAt() : inc.getCreatedAt();
            String dist = inc.getDistrict() != null ? inc.getDistrict().getName() : null;
            return new IncidentSnapshot(inc.getTitle(), rawBody.toString(), ref, dist);
        }
    }

    private record RefinementOutcome(boolean geoImproved, boolean timeImproved, int sourcesAdded, boolean anyChange) {
    }
}

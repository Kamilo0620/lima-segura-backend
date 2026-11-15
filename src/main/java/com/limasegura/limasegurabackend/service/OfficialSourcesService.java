package com.limasegura.limasegurabackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limasegura.limasegurabackend.dto.dataset.DatasetImportResult;
import com.limasegura.limasegurabackend.ingestion.verify.ExactEventParser;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.IncidentOrigin;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Servicio de auditoría técnica en vivo y sincronización selectiva de fuentes oficiales
 * de la Policía Nacional del Perú (PNP / SIDPOL / GEOMININTER) e INEI (DATACRIM).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficialSourcesService {

    private static final String GEOMININTER_BASE =
            "https://geomininter.mininter.gob.pe/arcgis/rest/services/ogc/denuncias/MapServer/0";
    private static final String DATACRIM_URL = "https://datacrim.inei.gob.pe/";
    private static final String ONSC_URL = "https://observatorio.mininter.gob.pe/";
    private static final Duration STATUS_CACHE_TTL = Duration.ofMinutes(15);

    private final ObjectMapper objectMapper;
    private final DatasetImportService datasetImportService;
    private final IncidentRepository incidentRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private volatile OfficialSourcesStatusResponse cachedStatus;
    private volatile Instant cachedAt = Instant.EPOCH;

    public record OfficialSourceAudit(
            String id,
            String name,
            String agency,
            String endpointUrl,
            boolean reachable,
            String updateCadence,
            String latestDataDate,
            Long estimatedLagDays,
            Long totalRecords,
            boolean suitableForRealtimeAlerts,
            String recommendedRole,
            String noiseAndFilteringNotes
    ) {
    }

    public record OfficialSourcesStatusResponse(
            LocalDateTime auditedAt,
            String executiveSummary,
            List<OfficialSourceAudit> sources
    ) {
    }

    /**
     * Audita la disponibilidad, frescura y aptitud de GEOMININTER, MININTER/SIDPOL e INEI DATACRIM.
     */
    public OfficialSourcesStatusResponse getOfficialSourcesStatus(boolean forceRefresh) {
        if (!forceRefresh && cachedStatus != null
                && Duration.between(cachedAt, Instant.now()).compareTo(STATUS_CACHE_TTL) < 0) {
            return cachedStatus;
        }

        List<OfficialSourceAudit> audits = new ArrayList<>();
        audits.add(auditGeomininter());
        audits.add(auditMininterOnscSidpol());
        audits.add(auditIneiDatacrim());

        OfficialSourcesStatusResponse response = new OfficialSourcesStatusResponse(
                LocalDateTime.now(ExactEventParser.LIMA_ZONE),
                "PNP/SIDPOL y GEOMININTER tienen rezago histórico (>12 meses en ArcGIS) e incluyen ~40% de registros no callejeros (Ley 30364, violencia psicológica y servicios policiales). INEI DATACRIM publica cortes trimestrales agregados sin API en vivo. Por ello, se usan estrictamente filtrados para calibración histórica, mientras que las alertas en vivo provienen de verificación cruzada periodística.",
                audits
        );

        cachedStatus = response;
        cachedAt = Instant.now();
        return response;
    }

    /**
     * Sincroniza de forma segura un lote reciente filtrado de GEOMININTER para calibración histórica de zonas,
     * descartando violencia familiar Ley 30364, servicios policiales, ebriedad y filas sin coordenada.
     */
    public DatasetImportResult syncFilteredPnpBaseline(int requestedLimit) {
        int limit = Math.max(5, Math.min(100, requestedLimit));
        int fetchSize = Math.min(250, limit * 4);
        int imported = 0;
        int filtered = 0;
        int errors = 0;
        int inspected = 0;
        List<String> errorMsgs = new ArrayList<>();

        try {
            String where = URLEncoder.encode(
                    "departamento_hecho IN ('LIMA','CALLAO') AND lat_hecho IS NOT NULL AND lat_hecho <> 'SIN COORDENADA'",
                    StandardCharsets.UTF_8);
            String fields = "departamento_hecho,provincia_hecho,distrito_hecho,tipo_hecho,subtipo_hecho,modalidad_hecho,direccion_hecho,fecha_hora_hecho,lat_hecho,long_hecho";
            String url = GEOMININTER_BASE + "/query?where=" + where
                    + "&outFields=" + fields
                    + "&orderByFields=fecha_hora_hecho+DESC"
                    + "&resultRecordCount=" + fetchSize
                    + "&f=json";

            JsonNode root = fetchJson(url);
            JsonNode features = root != null ? root.get("features") : null;
            if (features == null || !features.isArray()) {
                return DatasetImportResult.builder()
                        .totalRows(0).importedRows(0).filteredOutRows(0).errorRows(1)
                        .importedCount(0).skippedCount(1)
                        .message("GEOMININTER no devolvió registros en este momento.")
                        .errors(List.of("Respuesta vacía o servicio ArcGIS temporalmente no disponible."))
                        .build();
            }

            Set<String> existingKeys = loadRecentOfficialKeys();

            for (JsonNode feature : features) {
                if (imported >= limit) {
                    break;
                }
                JsonNode attrs = feature.get("attributes");
                if (attrs == null) {
                    continue;
                }
                inspected++;

                String rawType = textOrNull(attrs, "tipo_hecho");
                String rawSubtype = textOrNull(attrs, "subtipo_hecho");
                String rawModality = textOrNull(attrs, "modalidad_hecho");
                String address = textOrNull(attrs, "direccion_hecho");
                String district = textOrNull(attrs, "distrito_hecho");
                String province = textOrNull(attrs, "provincia_hecho");
                String dept = textOrNull(attrs, "departamento_hecho");
                Double lat = parseDoubleOrNull(textOrNull(attrs, "lat_hecho"));
                Double lng = parseDoubleOrNull(textOrNull(attrs, "long_hecho"));
                LocalDateTime occurredAt = attrs.hasNonNull("fecha_hora_hecho") && attrs.get("fecha_hora_hecho").isNumber()
                        ? LocalDateTime.ofInstant(Instant.ofEpochMilli(attrs.get("fecha_hora_hecho").asLong()), ExactEventParser.LIMA_ZONE)
                        : null;

                String dedupSignature = ExactEventParser.normalize(
                        (rawModality != null ? rawModality : "") + "|" + (district != null ? district : "") + "|" + occurredAt);
                if (existingKeys.contains(dedupSignature)) {
                    filtered++;
                    continue;
                }

                DatasetImportService.OfficialCrimeRow row = new DatasetImportService.OfficialCrimeRow(
                        rawType, rawSubtype, rawModality, address, district, province, dept,
                        lat, lng, occurredAt, IncidentOrigin.OFFICIAL_PNP, null
                );

                DatasetImportService.ImportRowOutcome outcome = datasetImportService.importNormalizedOfficialRow(row);
                if (outcome == DatasetImportService.ImportRowOutcome.IMPORTED) {
                    imported++;
                    existingKeys.add(dedupSignature);
                } else if (outcome == DatasetImportService.ImportRowOutcome.FILTERED_NOISE) {
                    filtered++;
                } else {
                    errors++;
                }
            }

        } catch (Exception e) {
            log.warn("Failed syncing filtered GEOMININTER baseline: {}", e.getMessage());
            errors++;
            errorMsgs.add(e.getMessage());
        }

        return DatasetImportResult.builder()
                .totalRows(inspected)
                .importedRows(imported)
                .filteredOutRows(filtered)
                .errorRows(errors)
                .importedCount(imported)
                .skippedCount(filtered + errors)
                .message(String.format(
                        "Sincronización histórica PNP filtrada: %d delitos callejeros útiles importados, %d descartados por ruido/Ley 30364/duplicados.",
                        imported, filtered))
                .errors(errorMsgs)
                .build();
    }

    private OfficialSourceAudit auditGeomininter() {
        boolean reachable = false;
        Long totalCount = null;
        String latestDate = "2025-06-30 (referencia conocida)";
        Long lagDays = null;

        try {
            String countUrl = GEOMININTER_BASE + "/query?where=1%3D1&returnCountOnly=true&f=json";
            JsonNode countJson = fetchJson(countUrl);
            if (countJson != null && countJson.has("count")) {
                reachable = true;
                totalCount = countJson.get("count").asLong();
            }

            String statsJsonParam = URLEncoder.encode(
                    "[{\"statisticType\":\"max\",\"onStatisticField\":\"fecha_hora_hecho\",\"outStatisticFieldName\":\"max_hecho\"}]",
                    StandardCharsets.UTF_8);
            String maxUrl = GEOMININTER_BASE + "/query?where=1%3D1&outStatistics=" + statsJsonParam + "&f=json";
            JsonNode statsRoot = fetchJson(maxUrl);
            if (statsRoot != null && statsRoot.has("features") && statsRoot.get("features").isArray()
                    && !statsRoot.get("features").isEmpty()) {
                JsonNode maxNode = statsRoot.get("features").get(0).path("attributes").path("max_hecho");
                if (maxNode.isNumber()) {
                    Instant maxInst = Instant.ofEpochMilli(maxNode.asLong());
                    LocalDateTime maxDt = LocalDateTime.ofInstant(maxInst, ExactEventParser.LIMA_ZONE);
                    latestDate = maxDt.toLocalDate().toString();
                    lagDays = ChronoUnit.DAYS.between(maxDt.toLocalDate(), LocalDateTime.now(ExactEventParser.LIMA_ZONE).toLocalDate());
                }
            }
        } catch (Exception e) {
            log.debug("GEOMININTER live audit unavailable: {}", e.getMessage());
        }

        return new OfficialSourceAudit(
                "pnp-geomininter",
                "GEOMININTER - Mapa del Delito ArcGIS",
                "PNP / MININTER",
                GEOMININTER_BASE,
                reachable,
                "Actualización por lotes irregulares (>12 meses de rezago histórico en servicio OGC)",
                latestDate,
                lagDays,
                totalCount,
                false,
                "Calibración histórica georreferenciada (no alertas en tiempo real)",
                "Contiene coordenadas lat_hecho/long_hecho y direccion_hecho reales, pero mezcla Ley 30364 (violencia familiar/psicológica), conducción en ebriedad, servicio policial y filas 'SIN COORDENADA'; exige filtro estricto."
        );
    }

    private OfficialSourceAudit auditMininterOnscSidpol() {
        boolean reachable = probeHttpHeadOrGet(ONSC_URL);
        return new OfficialSourceAudit(
                "mininter-sidpol-csv",
                "Datos Abiertos SIDPOL / MININTER ONSC",
                "MININTER / PNP",
                ONSC_URL,
                reachable,
                "Mensual (cortes CSV/XLSX agregados por mes)",
                "Cortes mensuales 2026 agregados por distrito",
                30L,
                null,
                false,
                "Peso estadístico distrital y series mensuales (importación CSV filtrada)",
                "La API CKAN v3 heredada de datosabiertos.gob.pe devuelve 404 en varios paquetes y los archivos CSV/XLSX modernos publican conteos agregados (UBIGEO/MODALIDAD/TOTAL_DENUNCIAS) sin calle ni hora exacta."
        );
    }

    private OfficialSourceAudit auditIneiDatacrim() {
        boolean reachable = probeHttpHeadOrGet(DATACRIM_URL);
        return new OfficialSourceAudit(
                "inei-datacrim",
                "INEI DATACRIM / CEIC",
                "INEI",
                DATACRIM_URL,
                reachable,
                "Trimestral / Semestral",
                "Cortes estadísticos consolidados trimestrales",
                90L,
                null,
                false,
                "Validación macro-estadística distrital y comparativa histórica",
                "Plataforma visual consolidada interinstitucionalmente sin API abierta REST/CSV de incidentes callejeros minuto a minuto ni coordenadas puntuales públicas."
        );
    }

    private Set<String> loadRecentOfficialKeys() {
        Set<String> keys = new HashSet<>();
        List<Incident> recentOfficial = incidentRepository.findByOriginWithDistrict(
                IncidentOrigin.OFFICIAL_PNP, PageRequest.of(0, 200)
        );
        for (Incident inc : recentOfficial) {
            String dist = inc.getDistrict() != null ? inc.getDistrict().getName() : "";
            String sig = ExactEventParser.normalize(
                    (inc.getTitle() != null ? inc.getTitle().replace("[PNP] ", "") : "") + "|" + dist + "|" + inc.getOccurredAt());
            keys.add(sig);
        }
        return keys;
    }

    private JsonNode fetchJson(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "LimaSeguraBot/2.0")
                .GET()
                .build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
            return null;
        }
        return objectMapper.readTree(resp.body());
    }

    private boolean probeHttpHeadOrGet(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "LimaSeguraBot/2.0")
                    .GET()
                    .build();
            HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() >= 200 && resp.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    private String textOrNull(JsonNode attrs, String key) {
        if (!attrs.hasNonNull(key)) return null;
        String val = attrs.get(key).asText();
        return val != null && !val.isBlank() ? val.trim() : null;
    }

    private Double parseDoubleOrNull(String val) {
        if (val == null || val.equalsIgnoreCase("SIN COORDENADA")) return null;
        try {
            return Double.parseDouble(val.replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }
}

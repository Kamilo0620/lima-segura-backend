package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.dataset.DatasetImportResult;
import com.limasegura.limasegurabackend.ingestion.filter.TitleRelevanceFilter;
import com.limasegura.limasegurabackend.ingestion.geo.DistrictResolver;
import com.limasegura.limasegurabackend.ingestion.geo.GeocodingService;
import com.limasegura.limasegurabackend.ingestion.verify.ExactEventParser;
import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Servicio de importación y limpieza estricta para datasets oficiales de PNP (SIDPOL / GEOMININTER),
 * MININTER ONSC e INEI (DATACRIM). Descarta ruido institucional, violencia intrafamiliar Ley 30364,
 * servicios policiales y registros fuera de Lima Metropolitana o el Callao.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetImportService {

    private static final Set<String> EXCLUDED_LIMA_PROVINCIAS = Set.of(
            "huaura", "huaral", "barranca", "canete", "yauyos", "oyon", "cajatambo", "canta", "huarochiri"
    );

    private static final Set<String> EXCLUDED_PNP_KEYWORDS = Set.of(
            "ley 30364", "ley nro 30364", "violencia contra la mujer", "integrantes del grupo familiar",
            "violencia familiar", "violencia intrafamiliar", "violencia psicologica", "violencia fisica",
            "servicio policial", "conduccion en estado de ebriedad", "estado de ebriedad",
            "peligro comun", "pension de alimentos", "barristas", "hinchas"
    );

    private final IncidentRepository incidentRepository;
    private final DistrictResolver districtResolver;
    private final TitleRelevanceFilter titleRelevanceFilter = new TitleRelevanceFilter();

    public record OfficialCrimeRow(
            String rawType,
            String rawSubtype,
            String rawModality,
            String address,
            String district,
            String province,
            String department,
            Double lat,
            Double lng,
            LocalDateTime occurredAt,
            IncidentOrigin origin,
            String externalTitle
    ) {
    }

    public enum ImportRowOutcome {
        IMPORTED,
        FILTERED_NOISE,
        SKIPPED_ERROR
    }

    public DatasetImportResult importCsv(MultipartFile file) {
        int totalRows = 0;
        int importedRows = 0;
        int filteredOutRows = 0;
        int errorRows = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                return buildResult(0, 0, 0, 0, "Archivo CSV vacío", errors);
            }

            List<String> headers = parseCsvRow(headerLine);
            Map<String, Integer> headerIndex = buildHeaderIndex(headers);
            boolean isOfficialSidpolSchema = headerIndex.containsKey("subtipo_hecho")
                    || headerIndex.containsKey("modalidad_hecho")
                    || headerIndex.containsKey("distrito_hecho")
                    || headerIndex.containsKey("ubigeo_hecho")
                    || headerIndex.containsKey("tipo");

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                totalRows++;
                List<String> cols = parseCsvRow(line);

                try {
                    OfficialCrimeRow row = isOfficialSidpolSchema
                            ? parseOfficialSchemaRow(cols, headerIndex)
                            : parseGenericSchemaRow(cols);

                    ImportRowOutcome outcome = importNormalizedOfficialRow(row);
                    if (outcome == ImportRowOutcome.IMPORTED) {
                        importedRows++;
                    } else if (outcome == ImportRowOutcome.FILTERED_NOISE) {
                        filteredOutRows++;
                    } else {
                        errorRows++;
                    }
                } catch (Exception e) {
                    errorRows++;
                    errors.add("Fila " + totalRows + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process CSV file: {}", e.getMessage());
            errors.add("Error procesando archivo: " + e.getMessage());
        }

        String summary = String.format("Procesadas %d filas: %d delitos callejeros importados, %d filtrados (ruido/fuera de Lima/Ley 30364), %d errores.",
                totalRows, importedRows, filteredOutRows, errorRows);
        return buildResult(totalRows, importedRows, filteredOutRows, errorRows, summary, errors);
    }

    /**
     * Valida, limpia, mapea y persiste una fila de origen PNP/MININTER/INEI usando las mismas reglas
     * estrictas de seguridad ciudadana que el pipeline de noticias.
     */
    public ImportRowOutcome importNormalizedOfficialRow(OfficialCrimeRow row) {
        if (row == null) {
            return ImportRowOutcome.SKIPPED_ERROR;
        }

        String normDept = ExactEventParser.normalize(row.department());
        String normProv = ExactEventParser.normalize(row.province());
        String normDist = ExactEventParser.normalize(row.district());

        if (!normDept.isBlank() && !normDept.contains("lima") && !normDept.contains("callao")) {
            return ImportRowOutcome.FILTERED_NOISE;
        }
        if (EXCLUDED_LIMA_PROVINCIAS.contains(normProv) || EXCLUDED_LIMA_PROVINCIAS.contains(normDist)) {
            return ImportRowOutcome.FILTERED_NOISE;
        }

        String combinedDesc = ExactEventParser.normalize(
                (row.rawType() != null ? row.rawType() : "") + " "
                        + (row.rawSubtype() != null ? row.rawSubtype() : "") + " "
                        + (row.rawModality() != null ? row.rawModality() : "") + " "
                        + (row.externalTitle() != null ? row.externalTitle() : ""));

        for (String noise : EXCLUDED_PNP_KEYWORDS) {
            if (combinedDesc.contains(noise) && !combinedDesc.contains("feminicidio") && !combinedDesc.contains("homicidio")) {
                return ImportRowOutcome.FILTERED_NOISE;
            }
        }
        if (titleRelevanceFilter.isExcludedCrimeCategory(row.externalTitle(), combinedDesc)) {
            return ImportRowOutcome.FILTERED_NOISE;
        }

        IncidentType mappedType = mapOfficialCrimeType(row.rawType(), row.rawSubtype(), row.rawModality(), combinedDesc);
        if (mappedType == null) {
            return ImportRowOutcome.FILTERED_NOISE;
        }

        District district = districtResolver.resolve(row.district(), row.province(), row.department());
        if (district == null && row.district() != null) {
            district = districtResolver.resolveFromText(row.district());
        }
        if (district == null) {
            return ImportRowOutcome.FILTERED_NOISE;
        }

        Double lat = row.lat();
        Double lng = row.lng();
        boolean hasExactCoord = lat != null && lng != null && Math.abs(lat) > 0.1 && Math.abs(lng) > 0.1;
        if (!hasExactCoord) {
            if (district.getCentroidLat() == null || district.getCentroidLng() == null) {
                return ImportRowOutcome.SKIPPED_ERROR;
            }
            lat = district.getCentroidLat();
            lng = district.getCentroidLng();
        }

        double[] clamped = GeocodingService.clampToLimaLand(lat, lng);
        if (clamped[0] < -12.55 || clamped[0] > -11.50 || clamped[1] < -77.25 || clamped[1] > -76.60) {
            return ImportRowOutcome.FILTERED_NOISE;
        }

        String title = row.externalTitle() != null && !row.externalTitle().isBlank()
                ? row.externalTitle().trim()
                : buildOfficialTitle(row, mappedType, district);
        if (title.length() > 200) {
            title = title.substring(0, 200);
        }

        String cleanAddr = row.address() != null && !row.address().isBlank()
                ? row.address().trim()
                : "Jurisdicción de " + district.getName();

        Incident incident = Incident.builder()
                .incidentType(mappedType)
                .title(title)
                .summary(String.format("Registro histórico oficial (%s). Modalidad: %s. Ubicación reportada: %s (%s). "
                                + "Utilizado como línea base estadística de incidencia delictiva sin alterar alertas activas en vivo.",
                        row.origin() == IncidentOrigin.OFFICIAL_INEI ? "INEI" : "PNP / MININTER",
                        nonBlankOr(row.rawModality(), nonBlankOr(row.rawSubtype(), mappedType.name())),
                        cleanAddr,
                        district.getName()))
                .occurredAt(row.occurredAt() != null ? row.occurredAt() : LocalDateTime.now(ExactEventParser.LIMA_ZONE))
                .occurredAtPrecision(row.occurredAt() != null ? OccurredAtPrecision.EXACT : OccurredAtPrecision.DATE_ONLY)
                .address(cleanAddr)
                .district(district)
                .lat(clamped[0])
                .lng(clamped[1])
                .geoPrecision(hasExactCoord ? GeoPrecision.ADDRESS : GeoPrecision.DISTRICT)
                .origin(row.origin() != null ? row.origin() : IncidentOrigin.OFFICIAL_PNP)
                .status(IncidentStatus.PUBLISHED)
                .confidence(0.95)
                .incidentCount(1)
                .llmModel("official-dataset-calibrated")
                .build();

        incidentRepository.save(incident);
        return ImportRowOutcome.IMPORTED;
    }

    private OfficialCrimeRow parseGenericSchemaRow(List<String> cols) {
        if (cols.size() < 8) {
            throw new IllegalArgumentException("Columnas insuficientes (" + cols.size() + ")");
        }
        LocalDateTime occurredAt = parseFlexibleDateTime(cols.get(2));
        Double lat = parseNullableDouble(cols.get(6));
        Double lng = parseNullableDouble(cols.get(7));
        String sourceStr = cols.size() > 8 ? cols.get(8).trim() : "PNP";
        IncidentOrigin origin = "INEI".equalsIgnoreCase(sourceStr)
                ? IncidentOrigin.OFFICIAL_INEI
                : IncidentOrigin.OFFICIAL_PNP;

        return new OfficialCrimeRow(
                cols.get(0).trim(),
                null,
                cols.get(0).trim(),
                null,
                cols.get(3).trim(),
                cols.size() > 4 ? cols.get(4).trim() : null,
                cols.size() > 5 ? cols.get(5).trim() : null,
                lat,
                lng,
                occurredAt,
                origin,
                cols.get(1).trim()
        );
    }

    private OfficialCrimeRow parseOfficialSchemaRow(List<String> cols, Map<String, Integer> idx) {
        String rawType = getCol(cols, idx, "tipo_hecho", "tipo", "incident_type");
        String rawSubtype = getCol(cols, idx, "subtipo_hecho", "subtipo", "generico");
        String rawModality = getCol(cols, idx, "modalidad_hecho", "modalidad", "especifico");
        String district = getCol(cols, idx, "distrito_hecho", "distrito", "district");
        String province = getCol(cols, idx, "provincia_hecho", "provincia", "province");
        String dept = getCol(cols, idx, "departamento_hecho", "departamento", "department");
        String address = getCol(cols, idx, "direccion_hecho", "direccion", "address");
        String rawDate = getCol(cols, idx, "fecha_hora_hecho", "fecha_hecho", "occurred_at");
        Double lat = parseNullableDouble(getCol(cols, idx, "lat_hecho", "latitud", "lat", "y"));
        Double lng = parseNullableDouble(getCol(cols, idx, "long_hecho", "longitud", "lng", "lon", "x"));

        return new OfficialCrimeRow(
                rawType,
                rawSubtype,
                rawModality,
                address,
                district,
                province,
                dept,
                lat,
                lng,
                parseFlexibleDateTime(rawDate),
                idx.containsKey("datacrim") ? IncidentOrigin.OFFICIAL_INEI : IncidentOrigin.OFFICIAL_PNP,
                null
        );
    }

    private IncidentType mapOfficialCrimeType(String rawType, String rawSubtype, String rawModality, String normCombined) {
        try {
            if (rawType != null && !rawType.isBlank()) {
                return IncidentType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
            }
        } catch (IllegalArgumentException ignored) {
        }

        if (normCombined.contains("feminicidio")) return IncidentType.FEMICIDE;
        if (normCombined.contains("sicariato") || normCombined.contains("homicidio") || normCombined.contains("asesinato")) return IncidentType.HOMICIDE;
        if (normCombined.contains("secuestro") || normCombined.contains("rapto")) return IncidentType.KIDNAPPING;
        if (normCombined.contains("extorsion") || normCombined.contains("cupo") || normCombined.contains("explosivo")) return IncidentType.EXTORTION;
        if (normCombined.contains("arma de fuego") || normCombined.contains("balacera") || normCombined.contains("disparo")) {
            return normCombined.contains("robo") ? IncidentType.ARMED_ROBBERY : IncidentType.SHOOTING;
        }
        if (normCombined.contains("robo a mano armada") || normCombined.contains("robo agravado")) return IncidentType.ARMED_ROBBERY;
        if (normCombined.contains("hurto de vehiculo") || normCombined.contains("robo de vehiculo") || normCombined.contains("autoparte")) return IncidentType.VEHICLE_THEFT;
        if (normCombined.contains("robo")) return IncidentType.ROBBERY;
        if (normCombined.contains("hurto") || normCombined.contains("arrebato")) return IncidentType.THEFT;
        if (normCombined.contains("lesion") || normCombined.contains("agresion")) return IncidentType.ASSAULT;

        // Cualquier otro tipo administrativo o falta menor no corresponde a alerta de calle
        return null;
    }

    private String buildOfficialTitle(OfficialCrimeRow row, IncidentType type, District district) {
        String label = nonBlankOr(row.rawModality(), nonBlankOr(row.rawSubtype(), type.name()));
        String normalizedLabel = label.substring(0, 1).toUpperCase(Locale.ROOT)
                + label.substring(1).toLowerCase(Locale.ROOT);
        return "[PNP] " + normalizedLabel + " en " + district.getName();
    }

    private LocalDateTime parseFlexibleDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            if (trimmed.matches("\\d{12,13}")) {
                long epochMs = Long.parseLong(trimmed);
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ExactEventParser.LIMA_ZONE);
            }
            return LocalDateTime.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
        }
        for (String pattern : List.of("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm")) {
            try {
                return LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {
            }
        }
        for (String dateOnly : List.of("yyyy-MM-dd", "dd/MM/yyyy")) {
            try {
                return LocalDate.parse(trimmed, DateTimeFormatter.ofPattern(dateOnly)).atTime(12, 0);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Double parseNullableDouble(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("null") || raw.equalsIgnoreCase("sin coordenada")) {
            return null;
        }
        try {
            return Double.parseDouble(raw.trim().replace(",", "."));
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> parseCsvRow(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        result.add(current.toString());
        return result;
    }

    private Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = ExactEventParser.normalize(headers.get(i)).replaceAll("[^a-z0-9_]+", "_");
            map.put(key, i);
        }
        return map;
    }

    private String getCol(List<String> cols, Map<String, Integer> idx, String... candidates) {
        for (String c : candidates) {
            Integer i = idx.get(c);
            if (i != null && i >= 0 && i < cols.size()) {
                String val = cols.get(i).trim();
                if (!val.isBlank()) {
                    return val;
                }
            }
        }
        return null;
    }

    private String nonBlankOr(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred.trim() : fallback;
    }

    private DatasetImportResult buildResult(
            int totalRows,
            int importedRows,
            int filteredOutRows,
            int errorRows,
            String message,
            List<String> errors
    ) {
        int skipped = filteredOutRows + errorRows;
        return DatasetImportResult.builder()
                .totalRows(totalRows)
                .importedRows(importedRows)
                .filteredOutRows(filteredOutRows)
                .errorRows(errorRows)
                .importedCount(importedRows)
                .skippedCount(skipped)
                .message(message)
                .errors(errors.size() > 20 ? errors.subList(0, 20) : errors)
                .build();
    }
}

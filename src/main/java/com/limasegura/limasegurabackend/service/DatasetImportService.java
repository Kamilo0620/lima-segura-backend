package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.dataset.DatasetImportResult;
import com.limasegura.limasegurabackend.ingestion.geo.DistrictResolver;
import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetImportService {

    private final IncidentRepository incidentRepository;
    private final DistrictResolver districtResolver;

    public DatasetImportResult importCsv(MultipartFile file) {
        int totalRows = 0;
        int importedRows = 0;
        int errorRows = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            boolean header = true;

            while ((line = reader.readLine()) != null) {
                if (header) {
                    header = false;
                    continue;
                }
                totalRows++;

                String[] parts = line.split(",", -1);
                if (parts.length < 8) {
                    errorRows++;
                    errors.add("Row " + totalRows + ": insufficient columns (" + parts.length + ")");
                    continue;
                }

                try {
                    IncidentType type;
                    try {
                        type = IncidentType.valueOf(parts[0].trim().toUpperCase());
                    } catch (Exception e) {
                        type = IncidentType.OTHER;
                    }

                    String title = parts[1].trim();
                    LocalDateTime occurredAt = null;
                    if (!parts[2].trim().isEmpty()) {
                        try {
                            occurredAt = LocalDateTime.parse(parts[2].trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } catch (Exception e) {
                            occurredAt = LocalDateTime.now();
                        }
                    }

                    String districtName = parts[3].trim();
                    String provinceName = parts.length > 4 ? parts[4].trim() : null;
                    String departmentName = parts.length > 5 ? parts[5].trim() : null;

                    Double lat = null;
                    Double lng = null;
                    if (parts.length > 6 && !parts[6].trim().isEmpty()) {
                        lat = Double.parseDouble(parts[6].trim());
                    }
                    if (parts.length > 7 && !parts[7].trim().isEmpty()) {
                        lng = Double.parseDouble(parts[7].trim());
                    }

                    String sourceStr = parts.length > 8 ? parts[8].trim() : "PNP";
                    IncidentOrigin origin = "INEI".equalsIgnoreCase(sourceStr)
                            ? IncidentOrigin.OFFICIAL_INEI
                            : IncidentOrigin.OFFICIAL_PNP;

                    // Resolve district
                    District district = districtResolver.resolve(districtName, provinceName, departmentName);

                    // Use district centroid if no explicit coordinates
                    if (lat == null && district != null) {
                        lat = district.getCentroidLat();
                        lng = district.getCentroidLng();
                    }

                    Incident incident = Incident.builder()
                            .incidentType(type)
                            .title(title)
                            .occurredAt(occurredAt)
                            .district(district)
                            .lat(lat)
                            .lng(lng)
                            .geoPrecision(lat != null ? GeoPrecision.ADDRESS : GeoPrecision.DEPARTMENT)
                            .origin(origin)
                            .status(IncidentStatus.PUBLISHED)
                            .confidence(1.0)
                            .incidentCount(1)
                            .build();

                    incidentRepository.save(incident);
                    importedRows++;

                } catch (Exception e) {
                    errorRows++;
                    errors.add("Row " + totalRows + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process CSV file: {}", e.getMessage());
            errors.add("File processing error: " + e.getMessage());
        }

        return DatasetImportResult.builder()
                .totalRows(totalRows)
                .importedRows(importedRows)
                .errorRows(errorRows)
                .errors(errors.size() > 20 ? errors.subList(0, 20) : errors)
                .build();
    }
}

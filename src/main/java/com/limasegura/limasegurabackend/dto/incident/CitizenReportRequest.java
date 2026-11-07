package com.limasegura.limasegurabackend.dto.incident;

import com.limasegura.limasegurabackend.model.IncidentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitizenReportRequest {
    @NotNull
    private IncidentType incidentType;
    @NotBlank
    private String title;
    private String summary;
    private String address;
    private String districtName;
    private Double lat;
    private Double lng;
    private LocalDateTime occurredAt;
}

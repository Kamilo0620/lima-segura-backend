package com.limasegura.limasegurabackend.dto.incident;

import com.limasegura.limasegurabackend.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentResponse {
    private Long id;
    private IncidentType incidentType;
    private String title;
    private String summary;
    private LocalDateTime occurredAt;
    private OccurredAtPrecision occurredAtPrecision;
    private String address;
    private String districtName;
    private String province;
    private String department;
    private Double lat;
    private Double lng;
    private GeoPrecision geoPrecision;
    private Double confidence;
    private Integer incidentCount;
    private IncidentStatus status;
    private IncidentOrigin origin;
    private Integer victimsCount;
    private LocalDateTime createdAt;
}

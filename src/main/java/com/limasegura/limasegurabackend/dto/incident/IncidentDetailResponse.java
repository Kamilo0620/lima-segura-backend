package com.limasegura.limasegurabackend.dto.incident;

import com.limasegura.limasegurabackend.model.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDetailResponse {
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
    private String llmModel;
    private Integer victimsCount;
    private String evidenceExcerpt;
    private LocalDateTime createdAt;
    private List<SourceReferenceDto> sources;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceReferenceDto {
        private Long sourceItemId;
        private String url;
        private String title;
        private String sourceName;
    }
}

package com.limasegura.limasegurabackend.dto.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LlmIncidentDto {
    private String incident_type;
    private String title;
    private String summary;
    private String occurred_at;
    private String occurred_at_precision;
    private LlmLocationDto location;
    private Integer victims_count;
    private Double confidence;
    private String evidence_excerpt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LlmLocationDto {
        private String address_text;
        private String landmark;
        private String district;
        private String province;
        private String department;
        private Double approx_lat;
        private Double approx_lng;
    }
}

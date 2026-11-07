package com.limasegura.limasegurabackend.dto.incident;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsResponse {
    private long totalIncidents;
    private RiskLocationDto riskLocation;
    private List<HourlyRiskDto> hourlyRisk;
    private List<CategoryStatDto> categoryDistribution;
    private List<DangerZoneDto> topDangerZones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskLocationDto {
        private int score;
        private String level;
        private String address;
        private int radiusMeters;
        private String trendPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HourlyRiskDto {
        private String hour;
        private int score;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStatDto {
        private String category;
        private String type;
        private int percentage;
        private long count;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DangerZoneDto {
        private int rank;
        private String zone;
        private String level;
        private int score;
    }
}

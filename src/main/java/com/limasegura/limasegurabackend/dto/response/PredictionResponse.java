package com.limasegura.limasegurabackend.dto.response;

import com.limasegura.limasegurabackend.model.RiskLevel;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class PredictionResponse {
    private Long id;
    private DayOfWeek dayOfWeek;
    private Integer hour;
    private RiskLevel riskLevel;
    private Double score;
    private Long zoneId;
    private LocalDateTime generatedAt;
}

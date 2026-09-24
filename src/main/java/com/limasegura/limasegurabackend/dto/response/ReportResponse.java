package com.limasegura.limasegurabackend.dto.response;

import com.limasegura.limasegurabackend.model.ReportStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;


@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class ReportResponse {
    private Long id;
    private String description;
    private Double latitude;
    private Double longitude;
    private ReportStatus status;
    private Long userId;
    private Long zoneId;
    private Long categoryId;
}
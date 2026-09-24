package com.limasegura.limasegurabackend.dto.response;

import com.limasegura.limasegurabackend.model.ReportStatus;
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
}
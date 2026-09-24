package com.limasegura.limasegurabackend.dto.response;

import com.limasegura.limasegurabackend.model.Confirmation;
import com.limasegura.limasegurabackend.model.ReportStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class ReportDetailResponse {
    private Long id;
    private String description;
    private Double latitude;
    private Double longitude;
    private ReportStatus status;
    private LocalDateTime createdAt;
    private List<Confirmation> confirmations;
    private int confirmationsCount;
    private Long userId;
    private Long zoneId;
    private Long categoryId;
}

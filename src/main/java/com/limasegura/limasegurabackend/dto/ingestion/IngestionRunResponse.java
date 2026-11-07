package com.limasegura.limasegurabackend.dto.ingestion;

import com.limasegura.limasegurabackend.model.IngestionRunStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionRunResponse {
    private Long id;
    private Long newsSourceId;
    private String newsSourceName;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Integer itemsFound;
    private Integer itemsNew;
    private Integer errors;
    private IngestionRunStatus status;
}

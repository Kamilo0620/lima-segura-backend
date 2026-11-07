package com.limasegura.limasegurabackend.dto.ingestion;

import com.limasegura.limasegurabackend.model.SourceItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceItemResponse {
    private Long id;
    private Long newsSourceId;
    private String newsSourceName;
    private String externalId;
    private String url;
    private String title;
    private LocalDateTime publishedAt;
    private SourceItemStatus status;
    private Integer attempts;
    private String error;
    private LocalDateTime createdAt;
}

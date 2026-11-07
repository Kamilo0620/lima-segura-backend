package com.limasegura.limasegurabackend.dto.source;

import com.limasegura.limasegurabackend.model.SourceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceResponse {
    private Long id;
    private String name;
    private SourceType type;
    private String url;
    private String channelId;
    private String config;
    private Boolean active;
    private Double credibility;
    private Integer pollIntervalMinutes;
    private LocalDateTime lastPolledAt;
    private Integer consecutiveFailures;
    private String lastError;
}

package com.limasegura.limasegurabackend.dto.source;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceUpdateRequest {
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String url;

    @Size(max = 50)
    private String channelId;

    private String config;

    private Boolean active;

    @DecimalMin(value = "1.0", message = "Credibility must be between 1.0 and 10.0")
    @DecimalMax(value = "10.0", message = "Credibility must be between 1.0 and 10.0")
    private Double credibility;

    private Integer pollIntervalMinutes;
}

package com.limasegura.limasegurabackend.dto.source;

import com.limasegura.limasegurabackend.model.SourceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceCreateRequest {
    @NotBlank(message = "Source name is required")
    @Size(max = 100)
    private String name;

    @NotNull(message = "Source type is required")
    private SourceType type;

    @NotBlank(message = "URL is required")
    @Size(max = 500)
    private String url;

    @Size(max = 50)
    private String channelId;

    private String config;

    @DecimalMin(value = "1.0", message = "Credibility must be between 1.0 and 10.0")
    @DecimalMax(value = "10.0", message = "Credibility must be between 1.0 and 10.0")
    private Double credibility;

    private Integer pollIntervalMinutes;
}

package com.limasegura.limasegurabackend.dto.incident;

import com.limasegura.limasegurabackend.model.IncidentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentReviewRequest {
    @NotNull(message = "Status is required")
    private IncidentStatus status;
    private String title;
    private String summary;
}

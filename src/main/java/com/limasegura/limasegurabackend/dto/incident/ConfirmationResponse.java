package com.limasegura.limasegurabackend.dto.incident;

import com.limasegura.limasegurabackend.model.IncidentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmationResponse {
    private Long incidentId;
    private long confirmations;
    private int required;
    private IncidentStatus status;
}

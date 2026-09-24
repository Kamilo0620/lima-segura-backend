package com.limasegura.limasegurabackend.dto.request;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.*;

import java.time.DayOfWeek;

public class PredictionCreateRequest {
    @NotNull(message = "El id de la zona es obligatorio")
    private Long zoneId;

    @NotNull(message = "El dia de la semana es obligatorio")
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    @NotNull(message = "La hora es obligatoria")
    @Min(0) @Max(23)
    private Integer hour;

    @NotNull(message = "El score es obligatorio")
    @DecimalMin("0.0") @DecimalMax("1.0")
    private Double score;
}

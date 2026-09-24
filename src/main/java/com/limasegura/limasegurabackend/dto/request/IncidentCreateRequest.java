package com.limasegura.limasegurabackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class IncidentCreateRequest {
    @NotNull(message = "El incidente debe tener una zona")
    private Long zoneId;
    @NotNull(message = "El incidente debe tener una categoria")
    private Long categoryId;

    @NotBlank(message = "La fuente es obligatoria")
    @Size(max = 50)
    private String source;
    @NotNull(message = "La fecha del incidente es obligatoria")
    private LocalDate date;
    @NotNull(message = "La latitud es obligatoria")
    private Double latitude;
    @NotNull(message = "La longitud es obligatoria")
    private Double longitude;

}

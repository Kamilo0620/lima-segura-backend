package com.limasegura.limasegurabackend.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
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
    @NotNull(message = "El ID de la zona es obligatorio")
    private Long zoneId;
    @NotNull(message = "El ID de la categoría es obligatorio")
    private Long categoryId;

    @NotBlank(message = "La fuente es obligatoria")
    @Size(max = 50,message = "La fuente no puede superar los 50 caracteres")
    private String source;
    @NotNull(message = "La fecha del incidente es obligatoria")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;
    @NotNull(message = "La latitud es obligatoria")
    private Double latitude;
    @NotNull(message = "La longitud es obligatoria")
    private Double longitude;

}

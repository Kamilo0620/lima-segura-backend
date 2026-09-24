package com.limasegura.limasegurabackend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class ReportCreateRequest {
    @NotNull(message="El ID del usuario es obligatorio")
    private Long userId;

    @NotNull(message="El ID de la zona es obligatorio")
    private Long zoneId;

    @NotNull(message="El ID de la categoría es obligatorio")
    private Long categoryId;

    @NotBlank(message="La descripcion es obligatoria")
    @Size(max = 500,message="La descripción del reporte no debe superar los 500 caracteres")
    private String description;

    @NotNull(message = "La latitud es obligatoria")
    private Double latitude;

    @NotNull(message = "La longitud es obligatoria")
    private Double longitude;
}

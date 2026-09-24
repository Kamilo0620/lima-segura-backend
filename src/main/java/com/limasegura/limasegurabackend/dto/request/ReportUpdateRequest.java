package com.limasegura.limasegurabackend.dto.request;

import com.limasegura.limasegurabackend.model.ReportStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Setter @Getter
@Builder
public class ReportUpdateRequest {
    @NotBlank(message = "La descripcion es obligatoria")
    @Size(max = 500,message="La descripción del reporte no debe superar los 500 caracteres")
    private String description;

    @NotNull(message = "La latitud es obligatoria")
    private Double latitude;

    @NotNull(message = "La longitud es obligatoria")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "El estado es obligatorio")
    private ReportStatus status;
}

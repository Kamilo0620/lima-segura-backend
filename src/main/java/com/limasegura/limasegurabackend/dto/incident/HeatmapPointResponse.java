package com.limasegura.limasegurabackend.dto.incident;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapPointResponse {
    private Double lat;
    private Double lng;
    private Double weight;       // Intensidad = Gravedad_Delito * Peso_Fuente * Factor_Tiempo
    private String colorHint;    // "red" (0-6h), "orange" (6-24h), "yellow" (24-48h), "gray" (>48h)
    private Integer severity;    // 1-10
    private Double sourceWeight; // Credibilidad / 10 (0.1 - 1.0)
    private Double timeFactor;   // 1.0, 0.7, 0.4, 0.1
    private Long incidentId;
    private String incidentType;
    private String title;
}

package com.limasegura.limasegurabackend.dto.incident;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapPointResponse {
    private Double lat;
    private Double lng;
    private Double weight;       // Intensidad = Gravedad_Delito * Peso_Fuente * Factor_Tiempo_Continuo * Corroboracion
    private String colorHint;    // "red" (0-6h), "orange" (6-24h), "yellow" (24-48h), "gray" (>48h)
    private String colorHex;     // Tonalidad graduada exacta entre amarillo, naranja y rojo
    private Integer severity;    // 1-10
    private Double sourceWeight; // Credibilidad / 10 (0.1 - 1.0)
    private Double timeFactor;   // Factor continuo basado en minutos exactos desde occurredAt (0.10 - 1.00)
    private Integer corroborationCount; // Cantidad de fuentes verificadas que corroboran el hecho
    private Long incidentId;
    private String incidentType;
    private String title;
    private String districtName;
    private String province;
    private LocalDateTime occurredAt;
}

package com.limasegura.limasegurabackend.model;


import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Entity
@Table(name = "predicciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Prediccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_id", nullable = false)
    @NotNull(message = "La prediccion debe tener una zona")
    private Zona zona;

    @NotNull(message = "El dia de la semana es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private DayOfWeek diaSemana;

    @NotNull(message = "La hora es obligatoria")
    @Min(0)
    @Max(23)
    @Column(nullable = false)
    private Integer hora;

    @NotNull(message = "El nivel de riesgo es obligatorio")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NivelRiesgo nivelRiesgo;


    @NotNull(message = "El score es obligatorio")
    @DecimalMin("0.0")
    @DecimalMax("1.0")
    @Column(nullable = false)
    private Double score;


    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaGeneracion = LocalDateTime.now();
}

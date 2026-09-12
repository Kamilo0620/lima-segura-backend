package com.limasegura.limasegurabackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "incidentes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    @NotNull(message = "El incidente debe tener una zona")
    private Zone zone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    @NotNull(message = "El incidente debe tener una categoria")
    private Category category;

    @NotBlank(message = "La fuente es obligatoria")
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String source;

    @NotNull(message = "La fecha del incidente es obligatoria")
    @Column(nullable = false)
    private LocalDate date;

    @NotNull(message = "La latitud es obligatoria")
    @Column(nullable = false)
    private Double latitude;

    @NotNull(message = "La longitud es obligatoria")
    @Column(nullable = false)
    private Double longitude;
}
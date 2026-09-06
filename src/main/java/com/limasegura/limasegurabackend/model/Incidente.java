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
public class Incidente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zona_id", nullable = false)
    @NotNull(message = "El incidente debe tener una zona")
    private Zona zona;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    @NotNull(message = "El incidente debe tener una categoria")
    private Categoria categoria;

    @NotBlank(message = "La fuente es obligatoria")
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String fuente;

    @NotNull(message = "La fecha del incidente es obligatoria")
    @Column(nullable = false)
    private LocalDate fecha;


    @NotNull(message = "La latitud es obligatoria")
    @Column(nullable = false)
    private Double latitud;

    @NotNull(message = "La longitud es obligatoria")
    @Column(nullable = false)
    private Double longitud;

}

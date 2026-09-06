package com.limasegura.limasegurabackend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "confirmaciones",
        uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "reporte_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

public class Confirmacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    @NotNull(message = "La confirmacion debe tener un usuario")
    private Usuario usuario;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporte_id", nullable = false)
    @NotNull(message = "La confirmacion debe tener un reporte")
    private Reporte reporte;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha = LocalDateTime.now();
}

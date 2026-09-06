package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.EstadoReporte;
import com.limasegura.limasegurabackend.model.Reporte;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReporteRepository extends JpaRepository<Reporte,Long> {
    List<Reporte> findByZonaId(Long zonaId);

    List<Reporte> findByUsuarioId(Long usuarioId);

    List<Reporte> findByZonaIdAndEstado(Long zonaId, EstadoReporte estado);
}

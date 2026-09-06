package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Confirmacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfirmacionRepository extends JpaRepository<Confirmacion, Long> {

    List<Confirmacion> findByReporteId(Long reporteId);

    Optional<Confirmacion> findByUsuarioIdAndReporteId(Long usuarioId, Long reporteId);

    long countByReporteId(Long reporteId);
}

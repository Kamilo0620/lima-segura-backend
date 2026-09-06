package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Incidente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;


public interface IncidenteRepository extends JpaRepository<Incidente,Long> {

    List<Incidente> findByZonaId(Long zonaId);

    List<Incidente> findByFuente(String fuente);

    List<Incidente> findByFechaBetween(LocalDate desde, LocalDate hasta);
}

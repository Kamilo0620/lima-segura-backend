package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByZoneId(Long zoneId);

    List<Incident> findBySource(String source);

    List<Incident> findByDateBetween(LocalDate from, LocalDate to);

    // NUEVOS: versiones paginadas
    Page<Incident> findByZoneId(Long zoneId, Pageable pageable);

    Page<Incident> findByDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    Integer countByZoneId(Long id);
    long countByCategoryId(Long categoryId);
}
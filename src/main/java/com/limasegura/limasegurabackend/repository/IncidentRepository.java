package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByZoneId(Long zoneId);

    List<Incident> findBySource(String source);

    List<Incident> findByDateBetween(LocalDate from, LocalDate to);

    Integer countByZoneId(Long id);
}
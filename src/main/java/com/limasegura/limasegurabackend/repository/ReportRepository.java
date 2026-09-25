package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Report;
import com.limasegura.limasegurabackend.model.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findById(Long Id);

    List<Report> findByZoneId(Long zoneId);

    List<Report> findByUserId(Long userId);

    List<Report> findByZoneIdAndStatus(Long zoneId, ReportStatus status);

    Integer countByZoneId(Long id);
    long countByCategoryId(Long categoryId);
}
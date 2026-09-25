package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Report;
import com.limasegura.limasegurabackend.model.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByZoneId(Long zoneId);

    List<Report> findByUserId(Long userId);

    List<Report> findByZoneIdAndStatus(Long zoneId, ReportStatus status);

    Integer countByZoneId(Long id);
}
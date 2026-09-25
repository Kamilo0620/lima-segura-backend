package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.Confirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfirmationRepository extends JpaRepository<Confirmation, Long> {

    List<Confirmation> findByReportId(Long reportId);

    Optional<Confirmation> findByUserIdAndReportId(Long userId, Long reportId);

    long countByReportId(Long reportId);

    long countByUserId(Long userId);
}
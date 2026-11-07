package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.IngestionRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {
    Page<IngestionRun> findByNewsSourceIdOrderByStartedAtDesc(Long newsSourceId, Pageable pageable);
    Page<IngestionRun> findAllByOrderByStartedAtDesc(Pageable pageable);
}

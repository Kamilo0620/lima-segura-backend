package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.IngestionRun;
import com.limasegura.limasegurabackend.model.IngestionRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface IngestionRunRepository extends JpaRepository<IngestionRun, Long> {
    Page<IngestionRun> findByNewsSourceIdOrderByStartedAtDesc(Long newsSourceId, Pageable pageable);
    Page<IngestionRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    @Transactional
    @Modifying
    @Query("DELETE FROM IngestionRun r WHERE r.startedAt < :before AND r.status <> :runningStatus")
    int deleteFinishedOlderThan(@Param("before") LocalDateTime before,
                                @Param("runningStatus") IngestionRunStatus runningStatus);
}

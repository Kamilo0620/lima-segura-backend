package com.limasegura.limasegurabackend.repository;

import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SourceItemRepository extends JpaRepository<SourceItem, Long> {

    boolean existsByNewsSourceIdAndExternalId(Long newsSourceId, String externalId);

    Optional<SourceItem> findByNewsSourceIdAndExternalId(Long newsSourceId, String externalId);

    Page<SourceItem> findByStatus(SourceItemStatus status, Pageable pageable);

    @Query("SELECT si FROM SourceItem si WHERE si.status = :status AND (si.nextAttemptAt IS NULL OR si.nextAttemptAt <= :now) AND si.attempts < :maxAttempts ORDER BY si.createdAt ASC")
    List<SourceItem> findRetryable(@Param("status") SourceItemStatus status, @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    @Query("SELECT si FROM SourceItem si WHERE si.status = 'DISCOVERED' AND si.newsSource.type = 'YOUTUBE_CHANNEL' ORDER BY si.createdAt ASC")
    List<SourceItem> findPendingVideos();

    @Query("SELECT si FROM SourceItem si JOIN FETCH si.newsSource WHERE si.status IN :statuses AND (si.nextAttemptAt IS NULL OR si.nextAttemptAt <= :now) AND si.attempts < :maxAttempts ORDER BY si.createdAt ASC")
    List<SourceItem> findByStatusInAndRetryable(@Param("statuses") List<SourceItemStatus> statuses, @Param("now") LocalDateTime now, @Param("maxAttempts") int maxAttempts);

    @Query("DELETE FROM SourceItem si WHERE si.status IN ('EXTRACTED', 'DISCARDED') AND si.createdAt < :before")
    void deleteContentTextOlderThan(@Param("before") LocalDateTime before);
}

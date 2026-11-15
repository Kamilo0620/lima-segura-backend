package com.limasegura.limasegurabackend.ingestion.scheduler;

import com.limasegura.limasegurabackend.ingestion.fetch.ArticleFetcher;
import com.limasegura.limasegurabackend.ingestion.llm.IncidentExtractionService;
import com.limasegura.limasegurabackend.model.IngestionRunStatus;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import com.limasegura.limasegurabackend.repository.IngestionRunRepository;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineRecoveryJob {

    private final SourceItemRepository sourceItemRepository;
    private final IngestionRunRepository ingestionRunRepository;
    private final ArticleFetcher articleFetcher;
    private final IncidentExtractionService extractionService;

    @Value("${ingestion.enabled:false}")
    private boolean ingestionEnabled;

    @Scheduled(initialDelay = 3000, fixedDelay = 6000)
    public void recover() {
        if (!ingestionEnabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        List<SourceItem> readyItems = sourceItemRepository.findByStatusInAndRetryable(
                List.of(SourceItemStatus.CONTENT_READY), now, 5);
        int extractedCount = 0;
        for (SourceItem item : readyItems) {
            if (extractedCount >= 25) break;
            try {
                log.info("Recovering extraction for item {}", item.getId());
                extractionService.extractIncidents(item.getId());
                extractedCount++;
            } catch (Exception e) {
                log.error("Error recovering extraction for item {}: {}", item.getId(), e.getMessage());
            }
        }

        List<SourceItem> discoveredItems = sourceItemRepository.findByStatusInAndRetryable(
                List.of(SourceItemStatus.DISCOVERED), now, 5);
        int fetchedCount = 0;
        for (SourceItem item : discoveredItems) {
            if (fetchedCount >= 25) break;
            try {
                log.info("Recovering fetch for item {}", item.getId());
                articleFetcher.fetchContent(item.getId());
                fetchedCount++;
            } catch (Exception e) {
                log.error("Error recovering fetch for item {}: {}", item.getId(), e.getMessage());
            }
        }
    }

    /**
     * Política inteligente de retención para acotar el crecimiento de PostgreSQL:
     * conserva los incidentes verificados y las claves de deduplicación URL/external_id,
     * pero purga registros descartados (&gt;14d), historial antiguo de corridas (&gt;7d) y
     * texto crudo ya procesado (&gt;7d).
     */
    @Scheduled(initialDelay = 90000, fixedDelay = 21600000)
    public void prunePipelineStorage() {
        LocalDateTime now = LocalDateTime.now();
        try {
            int deletedDiscarded = sourceItemRepository.deleteDiscardedOlderThan(now.minusDays(14));
            int prunedContent = sourceItemRepository.pruneExtractedContentOlderThan(now.minusDays(7));
            int deletedRuns = ingestionRunRepository.deleteFinishedOlderThan(
                    now.minusDays(7), IngestionRunStatus.RUNNING);
            if (deletedDiscarded > 0 || prunedContent > 0 || deletedRuns > 0) {
                log.info("DB retention cleanup: discardedItemsDeleted={}, extractedTextPruned={}, oldRunsDeleted={}",
                        deletedDiscarded, prunedContent, deletedRuns);
            }
        } catch (Exception e) {
            log.warn("Error in retention cleanup: {}", e.getMessage());
        }
    }
}

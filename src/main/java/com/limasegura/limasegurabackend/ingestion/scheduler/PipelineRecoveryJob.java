package com.limasegura.limasegurabackend.ingestion.scheduler;

import com.limasegura.limasegurabackend.ingestion.fetch.ArticleFetcher;
import com.limasegura.limasegurabackend.ingestion.llm.IncidentExtractionService;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
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
    private final ArticleFetcher articleFetcher;
    private final IncidentExtractionService extractionService;

    @Value("${ingestion.enabled:false}")
    private boolean ingestionEnabled;

    // Cada 2 minutos (con 30 s de espera al arrancar) para no gastar la cuota de la IA
    @Scheduled(initialDelay = 30000, fixedDelay = 120000)
    public void recover() {
        if (!ingestionEnabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();

        List<SourceItem> readyItems = sourceItemRepository.findByStatusInAndRetryable(
                List.of(SourceItemStatus.CONTENT_READY), now, 5);
        int extractedCount = 0;
        for (SourceItem item : readyItems) {
            if (extractedCount >= 15) break;
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
            if (fetchedCount >= 20) break;
            try {
                log.info("Recovering fetch for item {}", item.getId());
                articleFetcher.fetchContent(item.getId());
                fetchedCount++;
            } catch (Exception e) {
                log.error("Error recovering fetch for item {}: {}", item.getId(), e.getMessage());
            }
        }
    }
}

package com.limasegura.limasegurabackend.ingestion.scheduler;

import com.limasegura.limasegurabackend.ingestion.fetch.ArticleFetcher;
import com.limasegura.limasegurabackend.ingestion.llm.IncidentExtractionService;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import com.limasegura.limasegurabackend.model.SourceType;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Scheduled(fixedDelay = 120000)
    @org.springframework.transaction.annotation.Transactional
    public void recover() {
        LocalDateTime now = LocalDateTime.now();
        List<SourceItemStatus> statuses = List.of(SourceItemStatus.DISCOVERED, SourceItemStatus.CONTENT_READY);
        
        // Find items that need processing and haven't exceeded max attempts
        List<SourceItem> items = sourceItemRepository.findByStatusInAndRetryable(statuses, now, 5);
        
        for (SourceItem item : items) {
            try {
                if (item.getStatus() == SourceItemStatus.DISCOVERED) {
                    log.info("Recovering fetch for item {} (type: {})", item.getId(), 
                        item.getNewsSource() != null ? item.getNewsSource().getType() : "UNKNOWN");
                    articleFetcher.fetchContent(item.getId());
                } else if (item.getStatus() == SourceItemStatus.CONTENT_READY) {
                    log.info("Recovering extraction for item {}", item.getId());
                    extractionService.extractIncidents(item.getId());
                }
            } catch (Exception e) {
                log.error("Error recovering item {}: {}", item.getId(), e.getMessage());
            }
        }
    }
}

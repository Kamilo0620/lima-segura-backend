package com.limasegura.limasegurabackend.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class HeatmapCacheEvictionListener {

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentPublished(IncidentPublishedEvent event) {
        log.info("Evicting heatmap cache due to published incident {}", event.getIncidentId());
        // Actual cache eviction logic goes here
    }
}

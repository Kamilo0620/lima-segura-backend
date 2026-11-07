package com.limasegura.limasegurabackend.event;

import com.limasegura.limasegurabackend.ingestion.llm.IncidentExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class IncidentExtractionListener {

    private final IncidentExtractionService extractionService;

    @Async("llmExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSourceItemContentReady(SourceItemContentReadyEvent event) {
        log.info("Received SourceItemContentReadyEvent for item {}", event.getSourceItemId());
        extractionService.extractIncidents(event.getSourceItemId());
    }
}

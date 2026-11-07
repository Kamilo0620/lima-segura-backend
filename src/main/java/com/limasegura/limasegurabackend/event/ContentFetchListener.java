package com.limasegura.limasegurabackend.event;

import com.limasegura.limasegurabackend.ingestion.fetch.ArticleFetcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentFetchListener {

    private final ArticleFetcher articleFetcher;

    @Async("ingestionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSourceItemDiscovered(SourceItemDiscoveredEvent event) {
        log.info("Received SourceItemDiscoveredEvent for item {}", event.getSourceItemId());
        articleFetcher.fetchContent(event.getSourceItemId());
    }
}

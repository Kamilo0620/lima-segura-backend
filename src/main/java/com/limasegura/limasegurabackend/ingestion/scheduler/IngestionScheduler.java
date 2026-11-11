package com.limasegura.limasegurabackend.ingestion.scheduler;

import com.limasegura.limasegurabackend.event.IngestionFailedEvent;
import com.limasegura.limasegurabackend.event.SourceItemDiscoveredEvent;
import com.limasegura.limasegurabackend.ingestion.connector.DiscoveredItem;
import com.limasegura.limasegurabackend.ingestion.connector.SourceConnector;
import com.limasegura.limasegurabackend.ingestion.filter.TitleRelevanceFilter;
import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.IngestionRunRepository;
import com.limasegura.limasegurabackend.repository.NewsSourceRepository;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionScheduler {

    private final NewsSourceRepository newsSourceRepository;
    private final SourceItemRepository sourceItemRepository;
    private final IngestionRunRepository ingestionRunRepository;
    private final List<SourceConnector> connectors;
    private final ApplicationEventPublisher eventPublisher;
    private final TitleRelevanceFilter titleRelevanceFilter = new TitleRelevanceFilter();

    /** Ingesta automática apagada por defecto. Se activa con INGESTION_ENABLED=true. */
    @Value("${ingestion.enabled:false}")
    private boolean ingestionEnabled;

    @Scheduled(initialDelay = 5000, fixedDelayString = "${ingestion.poll-interval:PT5M}")
    @SchedulerLock(name = "ingestion", lockAtMostFor = "PT4M")
    public void runIngestion() {
        if (!ingestionEnabled) {
            return;
        }
        log.info("Starting scheduled ingestion cycle");
        triggerAllSources();
    }

    public int triggerAllSources() {
        log.info("Starting ingestion cycle for all active sources");
        List<NewsSource> activeSources = newsSourceRepository.findByActiveTrue();

        for (NewsSource source : activeSources) {
            try {
                processSource(source);
            } catch (Exception e) {
                log.error("Error processing source {}: {}", source.getName(), e.getMessage());
            }
        }
        log.info("Ingestion cycle completed for {} sources", activeSources.size());
        return activeSources.size();
    }

    @Transactional
    public void processSource(NewsSource source) {
        IngestionRun run = IngestionRun.builder()
                .newsSource(source)
                .startedAt(LocalDateTime.now())
                .status(IngestionRunStatus.RUNNING)
                .build();
        ingestionRunRepository.save(run);

        try {
            SourceConnector connector = connectors.stream()
                    .filter(c -> c.supports(source))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No connector found for source type " + source.getType()));

            List<DiscoveredItem> discoveredItems = connector.discover(source);
            int itemsFound = discoveredItems.size();
            int itemsNew = 0;

            for (DiscoveredItem dItem : discoveredItems) {
                // FILTRO PREVIO DE TÍTULO: Evaluar si el título trata sobre un hecho delictivo/policial antes de guardar
                if (!titleRelevanceFilter.isRelevantCrimeTitle(dItem.getTitle(), dItem.getUrl())) {
                    log.debug("Discarded non-crime item by title filter: {}", dItem.getTitle());
                    continue;
                }

                java.util.Optional<SourceItem> existingOpt = sourceItemRepository.findByNewsSourceIdAndExternalId(
                        source.getId(), dItem.getExternalId());

                if (existingOpt.isEmpty()) {
                    SourceItem item = SourceItem.builder()
                            .newsSource(source)
                            .externalId(dItem.getExternalId())
                            .url(dItem.getUrl())
                            .title(dItem.getTitle())
                            .contentText(dItem.getContentText())
                            .publishedAt(dItem.getPublishedAt())
                            .status(SourceItemStatus.DISCOVERED)
                            .attempts(0)
                            .createdAt(LocalDateTime.now())
                            .build();

                    sourceItemRepository.save(item);
                    itemsNew++;

                    eventPublisher.publishEvent(new SourceItemDiscoveredEvent(this, item.getId()));
                }
                // Si ya existía no lo tocamos: los reintentos los maneja PipelineRecoveryJob,
                // respetando el límite de intentos (antes se reiniciaba a 0 en cada ciclo y
                // se reintentaba para siempre, gastando la cuota de la IA).
            }

            run.setItemsFound(itemsFound);
            run.setItemsNew(itemsNew);
            run.setStatus(IngestionRunStatus.COMPLETED);
            run.setFinishedAt(LocalDateTime.now());

            source.setLastPolledAt(LocalDateTime.now());
            source.setConsecutiveFailures(0);
            source.setLastError(null);

            log.info("Ingestion for source '{}': found={}, new (crime-filtered)={}", source.getName(), itemsFound, itemsNew);

        } catch (Exception e) {
            log.error("Ingestion failed for source '{}': {}", source.getName(), e.getMessage());
            run.setStatus(IngestionRunStatus.FAILED);
            run.setErrors(1);
            run.setFinishedAt(LocalDateTime.now());

            source.setConsecutiveFailures(
                    (source.getConsecutiveFailures() != null ? source.getConsecutiveFailures() : 0) + 1);
            source.setLastError(e.getMessage());

            eventPublisher.publishEvent(new IngestionFailedEvent(this, source.getId(), e.getMessage()));
        }

        ingestionRunRepository.save(run);
        newsSourceRepository.save(source);
    }
}

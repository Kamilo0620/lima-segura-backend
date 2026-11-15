package com.limasegura.limasegurabackend.ingestion.verify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler autónomo que ejecuta la verificación cruzada en segundo plano para refinar
 * coordenadas a nivel de calle, hora exacta del evento y fuentes corroboradoras.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ingestion.enabled", havingValue = "true", matchIfMissing = true)
public class CrossVerificationScheduler {

    private final CrossVerificationService crossVerificationService;

    @Scheduled(
            fixedDelayString = "${app.ingestion.cross-verify-interval-ms:600000}",
            initialDelayString = "${app.ingestion.cross-verify-initial-delay-ms:90000}"
    )
    @SchedulerLock(name = "crossVerificationTask", lockAtMostFor = "8m", lockAtLeastFor = "1m")
    public void runCrossVerification() {
        log.debug("Starting autonomous multi-source cross-verification cycle...");
        crossVerificationService.refineRecentIncidents();
    }
}

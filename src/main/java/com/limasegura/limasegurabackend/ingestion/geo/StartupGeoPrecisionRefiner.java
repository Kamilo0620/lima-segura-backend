package com.limasegura.limasegurabackend.ingestion.geo;

import com.limasegura.limasegurabackend.ingestion.verify.ExactEventParser;
import com.limasegura.limasegurabackend.model.GeoPrecision;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.IncidentOrigin;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Refina en segundo plano (sin bloquear el arranque) las coordenadas históricas de noticias y videos
 * que cuentan con direcciones extraíbles por Overpass/Photon/Google/Nominatim.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ingestion.enabled", havingValue = "true")
public class StartupGeoPrecisionRefiner {

    private final IncidentRepository incidentRepository;
    private final GeocodingService geocodingService;
    private final ExactEventParser exactEventParser;

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleBackgroundRefinement() {
        Thread worker = new Thread(this::runRefinementSafely, "LimaSegura-GeoRefiner");
        worker.setDaemon(true);
        worker.start();
    }

    public int refinePublishedNewsIncidents(int maxUpdates) {
        List<Incident> all = incidentRepository.findAllPublishedWithDistrict();
        int updated = 0;

        for (Incident inc : all) {
            if (updated >= maxUpdates) {
                break;
            }
            if (inc.getOrigin() != IncidentOrigin.NEWSPAPER && inc.getOrigin() != IncidentOrigin.YOUTUBE) {
                continue;
            }
            if (inc.getGeoPrecision() == GeoPrecision.EXACT_ADDRESS || inc.getGeoPrecision() == GeoPrecision.INTERSECTION) {
                continue;
            }

            String fullText = ((inc.getTitle() != null ? inc.getTitle() : "") + " "
                    + (inc.getSummary() != null ? inc.getSummary() : "")).trim();
            String extracted = exactEventParser.extractExactStreetAddress(fullText);

            boolean hadGenericAddr = exactEventParser.isGenericJurisdictionAddress(inc.getAddress());
            boolean gainedAddress = hadGenericAddr && extracted != null && !exactEventParser.isGenericJurisdictionAddress(extracted);
            boolean hasStreetToRefine = !exactEventParser.isGenericJurisdictionAddress(inc.getAddress())
                    && (inc.getGeoPrecision() == GeoPrecision.DISTRICT || exactEventParser.hasSpecificStreetDetail(inc.getAddress()));

            if (!gainedAddress && !hasStreetToRefine) {
                continue;
            }

            if (gainedAddress) {
                inc.setAddress(extracted);
            }

            Double oldLat = inc.getLat();
            Double oldLng = inc.getLng();
            GeoPrecision oldPrecision = inc.getGeoPrecision();

            geocodingService.geocodeIncident(inc);

            boolean changed = !Objects.equals(oldLat, inc.getLat())
                    || !Objects.equals(oldLng, inc.getLng())
                    || oldPrecision != inc.getGeoPrecision()
                    || gainedAddress;
            if (changed) {
                incidentRepository.save(inc);
                updated++;
            }
        }
        return updated;
    }

    private void runRefinementSafely() {
        try {
            Thread.sleep(3500L);
            int refined = refinePublishedNewsIncidents(45);
            log.info("StartupGeoPrecisionRefiner: {} incidentes noticiosos refinados en segundo plano.", refined);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.warn("StartupGeoPrecisionRefiner omitió refinamiento en segundo plano: {}", ex.getMessage());
        }
    }
}

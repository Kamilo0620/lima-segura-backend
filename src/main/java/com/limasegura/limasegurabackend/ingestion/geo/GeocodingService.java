package com.limasegura.limasegurabackend.ingestion.geo;

import com.limasegura.limasegurabackend.model.District;
import com.limasegura.limasegurabackend.model.GeoPrecision;
import com.limasegura.limasegurabackend.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final DistrictResolver districtResolver;
    private final NominatimClient nominatimClient;

    /**
     * Geocodes an incident using its address and district information.
     * The incident entity uses 'lat', 'lng' fields (not latitude/longitude).
     */
    public void geocodeIncident(Incident incident) {
        // First, try to resolve district from incident address
        String districtName = null;
        String provinceName = null;
        String departmentName = null;

        // The LLM extraction sets address directly on the incident.
        // District/province/department are parsed from the LlmIncidentDto.location
        // and stored temporarily. We'll use them here.

        if (incident.getAddress() != null && !incident.getAddress().isBlank()) {
            // Try Nominatim for precise coordinates
            Double[] coords = nominatimClient.geocode(incident.getAddress() + ", Peru");
            if (coords != null) {
                incident.setLat(coords[0]);
                incident.setLng(coords[1]);
                incident.setGeoPrecision(GeoPrecision.ADDRESS);
                log.debug("Geocoded address '{}' to [{}, {}]", incident.getAddress(), coords[0], coords[1]);
                return;
            }
        }

        // Try to resolve district
        District district = incident.getDistrict();
        if (district != null) {
            // Use district centroid with small jitter
            double jitterLat = (Math.random() - 0.5) * 0.005;
            double jitterLng = (Math.random() - 0.5) * 0.005;
            incident.setLat(district.getCentroidLat() + jitterLat);
            incident.setLng(district.getCentroidLng() + jitterLng);
            incident.setGeoPrecision(GeoPrecision.DISTRICT);
            log.debug("Using district centroid for '{}' with jitter", district.getName());
            return;
        }

        // Fallback: no geocoding possible
        log.warn("Could not geocode incident: {}", incident.getTitle());
        incident.setGeoPrecision(GeoPrecision.DEPARTMENT);
    }
}

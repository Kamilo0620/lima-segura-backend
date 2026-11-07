package com.limasegura.limasegurabackend.ingestion.geo;

import com.limasegura.limasegurabackend.model.District;
import com.limasegura.limasegurabackend.model.GeoPrecision;
import com.limasegura.limasegurabackend.model.Incident;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class GeocodingServiceTest {

    @Mock
    private DistrictResolver districtResolver;

    @Mock
    private NominatimClient nominatimClient;

    @InjectMocks
    private GeocodingService geocodingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGeocodeIncident_WithValidAddress() {
        // Arrange
        Incident incident = new Incident();
        incident.setAddress("Av. Javier Prado Este 4600");
        
        when(nominatimClient.geocode("Av. Javier Prado Este 4600, Peru"))
                .thenReturn(new Double[]{-12.0833, -76.9667});

        // Act
        geocodingService.geocodeIncident(incident);

        // Assert
        assertEquals(-12.0833, incident.getLat());
        assertEquals(-76.9667, incident.getLng());
        assertEquals(GeoPrecision.ADDRESS, incident.getGeoPrecision());
    }

    @Test
    void testGeocodeIncident_AddressNotFound_FallbackToDistrict() {
        // Arrange
        District district = new District();
        district.setName("Surco");
        district.setCentroidLat(-12.1333);
        district.setCentroidLng(-76.9833);
        
        Incident incident = new Incident();
        incident.setAddress("Unknown Street 123");
        incident.setDistrict(district);
        
        when(nominatimClient.geocode(anyString())).thenReturn(null);

        // Act
        geocodingService.geocodeIncident(incident);

        // Assert
        // Lat/lng should be close to district centroid
        assertEquals(-12.1333, incident.getLat(), 0.01);
        assertEquals(-76.9833, incident.getLng(), 0.01);
        assertEquals(GeoPrecision.DISTRICT, incident.getGeoPrecision());
    }
}

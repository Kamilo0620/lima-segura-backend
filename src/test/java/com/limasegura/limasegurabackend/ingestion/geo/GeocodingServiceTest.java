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

    @Test
    void testGeocodeIncident_SpecificBlockOnArterialAvenue_PrioritizesNominatimOverGenericMidpoint() {
        District surco = new District();
        surco.setName("Santiago de Surco");
        surco.setCentroidLat(-12.1391);
        surco.setCentroidLng(-76.9936);

        Incident incident = new Incident();
        incident.setTitle("Asalto cerca a universidad en Av. Javier Prado");
        incident.setAddress("Av. Javier Prado Este cdra. 42");
        incident.setDistrict(surco);

        when(nominatimClient.geocode("Av. Javier Prado Este 4200, Santiago de Surco, Lima, Peru"))
                .thenReturn(new Double[]{-12.0856, -76.9762});

        geocodingService.geocodeIncident(incident);

        assertEquals(-12.0856, incident.getLat(), 0.0001);
        assertEquals(-76.9762, incident.getLng(), 0.0001);
        assertEquals(GeoPrecision.ADDRESS, incident.getGeoPrecision());
    }

    @Test
    void testGeocodeIncident_PachacutecInCieneguilla_NeverJumpsToVentanilla() {
        District cieneguilla = new District();
        cieneguilla.setName("Cieneguilla");
        cieneguilla.setCentroidLat(-12.1120);
        cieneguilla.setCentroidLng(-76.8170);

        Incident incident = new Incident();
        incident.setTitle("Capturan a presunto cabecilla de banda extorsiva en local comunal de Cieneguilla");
        incident.setAddress("Av. Pachacútec con Av. Inca Roca");
        incident.setDistrict(cieneguilla);

        when(nominatimClient.geocode(anyString())).thenReturn(new Double[]{-11.8528, -77.1482});

        geocodingService.geocodeIncident(incident);

        assertEquals(-12.1120, incident.getLat(), 0.01);
        assertEquals(-76.8170, incident.getLng(), 0.01);
        assertEquals(GeoPrecision.DISTRICT, incident.getGeoPrecision());
    }

    @Test
    void testClampToLimaLand_PreservesLaPerlaAndMovesOceanPointsInland() {
        // La Perla terrestre no debe ser aplastada contra la línea vertical -77.0865
        double[] laPerla = GeocodingService.clampToLimaLand(-12.0750, -77.1150, 101L);
        assertEquals(-77.1150, laPerla[1], 0.0001);

        // Un punto en el Océano Pacífico frente a Miraflores sí debe reubicarse en tierra firme
        double[] pacificOffMiraflores = GeocodingService.clampToLimaLand(-12.1250, -77.0700, 202L);
        org.junit.jupiter.api.Assertions.assertTrue(pacificOffMiraflores[1] > -77.0390);
    }

    @Test
    void testGenericCallaoAddressNeverMarkedAsAddressAndSpreadsAcrossUrbanAnchors() {
        District callao = new District();
        callao.setUbigeo("070101");
        callao.setName("Callao");
        callao.setProvince("Callao");
        callao.setCentroidLat(-12.0558);
        callao.setCentroidLng(-77.1180);

        Incident inc = new Incident();
        inc.setId(1025L);
        inc.setTitle("Nueve detenidos con armas en operativo policial");
        inc.setAddress("Provincia Constitucional del Callao");
        inc.setDistrict(callao);

        geocodingService.geocodeIncident(inc);

        assertEquals(GeoPrecision.DISTRICT, inc.getGeoPrecision());
        org.mockito.Mockito.verifyNoInteractions(nominatimClient);
    }

    @Test
    void testAvenidaGrauNormalizesToCorridorAndIntersectionMarksIntersectionPrecision() {
        District cercado = new District();
        cercado.setUbigeo("150101");
        cercado.setName("Lima");
        cercado.setProvince("Lima");
        cercado.setCentroidLat(-12.0464);
        cercado.setCentroidLng(-77.0428);

        Incident grau = new Incident();
        grau.setId(1081L);
        grau.setTitle("Balacera en la avenida Grau");
        grau.setAddress("avenida Grau");
        grau.setDistrict(cercado);

        when(nominatimClient.geocode(anyString())).thenReturn(null);
        geocodingService.geocodeIncident(grau);
        assertEquals(GeoPrecision.ADDRESS, grau.getGeoPrecision());
        org.junit.jupiter.api.Assertions.assertTrue(grau.getLat() < -12.053 && grau.getLat() > -12.062);

        District sjb = new District();
        sjb.setUbigeo("150130");
        sjb.setName("San Borja");
        sjb.setProvince("Lima");
        sjb.setCentroidLat(-12.0942);
        sjb.setCentroidLng(-76.9990);

        Incident cruce = new Incident();
        cruce.setId(2001L);
        cruce.setTitle("Asalto en San Borja");
        cruce.setAddress("Cruce Av. Javier Prado con Av. Aviación");
        cruce.setDistrict(sjb);

        when(nominatimClient.geocodePrecisionContext("Cruce Av. Javier Prado con Av. Aviación", sjb))
                .thenReturn(new Double[]{-12.0884, -77.0044});
        geocodingService.geocodeIncident(cruce);
        assertEquals(GeoPrecision.INTERSECTION, cruce.getGeoPrecision());
        assertEquals(-12.0884, cruce.getLat(), 0.0001);
        assertEquals(-77.0044, cruce.getLng(), 0.0001);
    }
}

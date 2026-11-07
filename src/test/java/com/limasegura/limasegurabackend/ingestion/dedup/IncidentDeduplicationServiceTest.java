package com.limasegura.limasegurabackend.ingestion.dedup;

import com.limasegura.limasegurabackend.model.District;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.IncidentType;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IncidentDeduplicationServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private IncidentDeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testDeduplicateOrCreate_NoCandidates_CreatesNew() {
        // Arrange
        District district = new District();
        district.setUbigeo("150101");
        
        Incident newIncident = new Incident();
        newIncident.setIncidentType(IncidentType.ROBBERY);
        newIncident.setTitle("Robo en la avenida Arequipa");
        newIncident.setOccurredAt(LocalDateTime.now());
        newIncident.setDistrict(district);
        newIncident.setSourceItems(new HashSet<>());
        
        SourceItem item = new SourceItem();
        item.setId(1L);

        when(incidentRepository.findDuplicateCandidates(any(), any(), any(), any()))
                .thenReturn(new ArrayList<>());
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Incident result = deduplicationService.deduplicateOrCreate(newIncident, item);

        // Assert
        verify(incidentRepository).save(newIncident);
        assertTrue(result.getSourceItems().contains(item));
    }

    @Test
    void testDeduplicateOrCreate_SpatialMatchWithin300Meters_FusesIncidents() {
        // Arrange
        District district = new District();
        district.setUbigeo("150101");

        LocalDateTime time = LocalDateTime.now();

        // Incidente existente en Av. Arequipa cuadra 10 (-12.0721, -77.0345)
        Incident existingIncident = new Incident();
        existingIncident.setId(10L);
        existingIncident.setIncidentType(IncidentType.ROBBERY);
        existingIncident.setTitle("Asalto en Av. Arequipa");
        existingIncident.setConfidence(0.5);
        existingIncident.setOccurredAt(time);
        existingIncident.setLat(-12.0721);
        existingIncident.setLng(-77.0345);
        existingIncident.setIncidentCount(1);
        existingIncident.setSourceItems(new HashSet<>());

        // Nuevo incidente a ~150 metros (-12.0710, -77.0340) dentro de las 6 horas
        Incident newIncident = new Incident();
        newIncident.setIncidentType(IncidentType.ROBBERY);
        newIncident.setTitle("Robo a mano armada en Arequipa");
        newIncident.setOccurredAt(time.plusHours(2));
        newIncident.setDistrict(district);
        newIncident.setLat(-12.0710);
        newIncident.setLng(-77.0340);
        newIncident.setSourceItems(new HashSet<>());

        SourceItem item = new SourceItem();
        item.setId(2L);

        when(incidentRepository.findDuplicateCandidates(any(), any(), any(), any()))
                .thenReturn(List.of(existingIncident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Incident result = deduplicationService.deduplicateOrCreate(newIncident, item);

        // Assert
        assertEquals(10L, result.getId()); // Retorna el existente enriquecido
        assertTrue(result.getSourceItems().contains(item));
        assertEquals(0.65, result.getConfidence(), 0.01); // Confianza sube +0.15
        assertEquals(2, result.getIncidentCount()); // Conteo de incidentes sube

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getId());
    }

    @Test
    void testDeduplicateOrCreate_DistanceExceeds300Meters_DoesNotFuse() {
        // Arrange
        District district = new District();
        district.setUbigeo("150101");

        LocalDateTime time = LocalDateTime.now();

        // Incidente existente en (-12.0721, -77.0345)
        Incident existingIncident = new Incident();
        existingIncident.setId(10L);
        existingIncident.setIncidentType(IncidentType.ROBBERY);
        existingIncident.setTitle("Asalto en la zona");
        existingIncident.setConfidence(0.5);
        existingIncident.setOccurredAt(time);
        existingIncident.setLat(-12.0721);
        existingIncident.setLng(-77.0345);
        existingIncident.setIncidentCount(1);
        existingIncident.setSourceItems(new HashSet<>());

        // Nuevo incidente a más de 2 km (-12.0950, -77.0345) y títulos diferentes
        Incident newIncident = new Incident();
        newIncident.setIncidentType(IncidentType.ROBBERY);
        newIncident.setTitle("Robo en lugar muy lejano de la ciudad");
        newIncident.setOccurredAt(time.plusHours(1));
        newIncident.setDistrict(district);
        newIncident.setLat(-12.0950);
        newIncident.setLng(-77.0345);
        newIncident.setSourceItems(new HashSet<>());

        SourceItem item = new SourceItem();
        item.setId(2L);

        when(incidentRepository.findDuplicateCandidates(any(), any(), any(), any()))
                .thenReturn(List.of(existingIncident));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Incident result = deduplicationService.deduplicateOrCreate(newIncident, item);

        // Assert: no debe fusionar con el id 10, sino crear el nuevo
        assertNotEquals(10L, result.getId());
        assertTrue(result.getSourceItems().contains(item));
    }
}

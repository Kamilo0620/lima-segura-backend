package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.incident.HeatmapPointResponse;
import com.limasegura.limasegurabackend.model.Incident;
import com.limasegura.limasegurabackend.model.IncidentType;
import com.limasegura.limasegurabackend.model.NewsSource;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class IncidentHeatmapWeightingTest {

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetHeatmapPoints_CalculatesFormulaCorrectly() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Homicidio (Gravedad 10), RPP (Credibilidad 9.5 -> 0.95), hace 2 horas (Factor 1.0 -> rojo)
        NewsSource rpp = NewsSource.builder().name("RPP").credibility(9.5).build();
        SourceItem itemRpp = SourceItem.builder().newsSource(rpp).build();
        Incident incHomicide = Incident.builder()
                .id(1L)
                .incidentType(IncidentType.HOMICIDE)
                .title("Sicariato en SJL")
                .lat(-12.0)
                .lng(-77.0)
                .occurredAt(now.minusHours(2))
                .incidentCount(1)
                .sourceItems(Set.of(itemRpp))
                .build();

        // 2. Hurto simple (Gravedad 4), Diario Extra (Credibilidad 6.8 -> 0.68), hace 12 horas (Factor 0.7 -> naranja)
        NewsSource extra = NewsSource.builder().name("Diario Extra").credibility(6.8).build();
        SourceItem itemExtra = SourceItem.builder().newsSource(extra).build();
        Incident incTheft = Incident.builder()
                .id(2L)
                .incidentType(IncidentType.THEFT)
                .title("Arrebato de celular")
                .lat(-12.1)
                .lng(-77.1)
                .occurredAt(now.minusHours(12))
                .incidentCount(1)
                .sourceItems(Set.of(itemExtra))
                .build();

        // 3. Extorsión (Gravedad 8), Bomberos CGBVP (Credibilidad 10.0 -> 1.0), hace 30 horas (Factor 0.4 -> amarillo)
        NewsSource cgbvp = NewsSource.builder().name("CGBVP Bomberos").credibility(10.0).build();
        SourceItem itemBomberos = SourceItem.builder().newsSource(cgbvp).build();
        Incident incExtortion = Incident.builder()
                .id(3L)
                .incidentType(IncidentType.EXTORTION)
                .title("Detonación de artefacto explosivo")
                .lat(-12.2)
                .lng(-77.2)
                .occurredAt(now.minusHours(30))
                .incidentCount(1)
                .sourceItems(Set.of(itemBomberos))
                .build();

        // 4. Asalto (Gravedad 7), Canal N (Credibilidad 9.3 -> 0.93), hace 70 horas (Factor 0.1 -> gris)
        NewsSource canalN = NewsSource.builder().name("Canal N").credibility(9.3).build();
        SourceItem itemCanalN = SourceItem.builder().newsSource(canalN).build();
        Incident incAssault = Incident.builder()
                .id(4L)
                .incidentType(IncidentType.ROBBERY)
                .title("Asalto a mano armada")
                .lat(-12.3)
                .lng(-77.3)
                .occurredAt(now.minusHours(70))
                .incidentCount(1)
                .sourceItems(Set.of(itemCanalN))
                .build();

        org.mockito.Mockito.lenient().when(incidentRepository.findHeatmapIncidents(any(), any(), any(), any()))
                .thenReturn(List.of(incHomicide, incTheft, incExtortion, incAssault));
        org.mockito.Mockito.lenient().when(incidentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(incHomicide, incTheft, incExtortion, incAssault));

        // Act
        List<HeatmapPointResponse> points = incidentService.getHeatmapPoints(null, null, null, null);

        // Assert
        assertEquals(4, points.size());

        // Punto 1: Homicidio RPP -> 10 * 0.95 * 1.0 = 9.5
        HeatmapPointResponse p1 = points.get(0);
        assertEquals(9.5, p1.getWeight(), 0.05);
        assertEquals("red", p1.getColorHint());
        assertEquals(10, p1.getSeverity());
        assertEquals(0.95, p1.getSourceWeight(), 0.01);
        assertEquals(1.0, p1.getTimeFactor(), 0.01);

        // Punto 2: Hurto Extra -> 4 * 0.68 * 0.7 = 1.90
        HeatmapPointResponse p2 = points.get(1);
        assertEquals(1.90, p2.getWeight(), 0.05);
        assertEquals("orange", p2.getColorHint());
        assertEquals(4, p2.getSeverity());
        assertEquals(0.68, p2.getSourceWeight(), 0.01);
        assertEquals(0.7, p2.getTimeFactor(), 0.01);

        // Punto 3: Extorsión CGBVP -> 8 * 1.0 * 0.4 = 3.20
        HeatmapPointResponse p3 = points.get(2);
        assertEquals(3.20, p3.getWeight(), 0.05);
        assertEquals("yellow", p3.getColorHint());
        assertEquals(8, p3.getSeverity());
        assertEquals(1.0, p3.getSourceWeight(), 0.01);
        assertEquals(0.4, p3.getTimeFactor(), 0.01);

        // Punto 4: Asalto Canal N antiguo -> 7 * 0.93 * 0.1 = 0.65
        HeatmapPointResponse p4 = points.get(3);
        assertEquals(0.65, p4.getWeight(), 0.05);
        assertEquals("gray", p4.getColorHint());
        assertEquals(7, p4.getSeverity());
        assertEquals(0.93, p4.getSourceWeight(), 0.01);
        assertEquals(0.1, p4.getTimeFactor(), 0.01);
    }
}

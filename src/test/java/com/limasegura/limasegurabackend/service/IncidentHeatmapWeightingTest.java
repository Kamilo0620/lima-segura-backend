package com.limasegura.limasegurabackend.service;

import com.limasegura.limasegurabackend.dto.incident.HeatmapPointResponse;
import com.limasegura.limasegurabackend.ingestion.verify.ExactEventParser;
import com.limasegura.limasegurabackend.model.District;
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
    void testGetHeatmapPoints_CalculatesContinuousTimeIntensityDistrictAndGraduatedShades() {
        LocalDateTime now = LocalDateTime.now(ExactEventParser.LIMA_ZONE);
        District sjl = District.builder().ubigeo("150132").name("San Juan de Lurigancho").province("Lima").department("Lima").centroidLat(-12.0).centroidLng(-77.0).build();
        District miraflores = District.builder().ubigeo("150122").name("Miraflores").province("Lima").department("Lima").centroidLat(-12.1).centroidLng(-77.1).build();

        // 1. Homicidio hace 2h con doble fuente verificada -> rojo carmesí/vivo, boost de corroboración y distrito poblado
        NewsSource rpp = NewsSource.builder().name("RPP").credibility(9.5).build();
        NewsSource republica = NewsSource.builder().name("La República").credibility(8.8).build();
        SourceItem itemRpp = SourceItem.builder().newsSource(rpp).build();
        SourceItem itemRep = SourceItem.builder().newsSource(republica).build();
        Incident incHomicide = Incident.builder()
                .id(1L)
                .incidentType(IncidentType.HOMICIDE)
                .title("Sicariato en SJL")
                .district(sjl)
                .lat(-12.0)
                .lng(-77.0)
                .occurredAt(now.minusHours(2))
                .incidentCount(2)
                .sourceItems(Set.of(itemRpp, itemRep))
                .build();

        // 2. Hurto simple hace 12h -> naranja medio, decaimiento continuo entre 6h y 24h
        NewsSource extra = NewsSource.builder().name("Diario Extra").credibility(6.8).build();
        SourceItem itemExtra = SourceItem.builder().newsSource(extra).build();
        Incident incTheft = Incident.builder()
                .id(2L)
                .incidentType(IncidentType.THEFT)
                .title("Arrebato de celular")
                .district(miraflores)
                .lat(-12.1)
                .lng(-77.1)
                .occurredAt(now.minusHours(12))
                .incidentCount(1)
                .sourceItems(Set.of(itemExtra))
                .build();

        // 3. Extorsión hace 30h -> tono amarillo/ámbar
        NewsSource cgbvp = NewsSource.builder().name("CGBVP Bomberos").credibility(10.0).build();
        SourceItem itemBomberos = SourceItem.builder().newsSource(cgbvp).build();
        Incident incExtortion = Incident.builder()
                .id(3L)
                .incidentType(IncidentType.EXTORTION)
                .title("Detonación de artefacto explosivo")
                .district(sjl)
                .lat(-12.2)
                .lng(-77.2)
                .occurredAt(now.minusHours(30))
                .incidentCount(1)
                .sourceItems(Set.of(itemBomberos))
                .build();

        // 4. Asalto hace 70h -> decaimiento histórico
        NewsSource canalN = NewsSource.builder().name("Canal N").credibility(9.3).build();
        SourceItem itemCanalN = SourceItem.builder().newsSource(canalN).build();
        Incident incAssault = Incident.builder()
                .id(4L)
                .incidentType(IncidentType.ROBBERY)
                .title("Asalto a mano armada")
                .district(miraflores)
                .lat(-12.3)
                .lng(-77.3)
                .occurredAt(now.minusHours(70))
                .incidentCount(1)
                .sourceItems(Set.of(itemCanalN))
                .build();

        org.mockito.Mockito.lenient().when(incidentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(incHomicide, incTheft, incExtortion, incAssault));

        // Act
        List<HeatmapPointResponse> points = incidentService.getHeatmapPoints(null, null, null, null);

        // Assert
        assertEquals(4, points.size());

        HeatmapPointResponse p1 = points.get(0);
        assertEquals("red", p1.getColorHint());
        assertEquals("#dc2626", p1.getColorHex());
        assertEquals("San Juan de Lurigancho", p1.getDistrictName());
        assertEquals("Lima", p1.getProvince());
        assertNotNull(p1.getOccurredAt());
        assertEquals(2, p1.getCorroborationCount());
        assertTrue(p1.getTimeFactor() > 0.90 && p1.getTimeFactor() <= 1.0);
        assertTrue(p1.getWeight() > 9.5); // boost por doble corroboración

        HeatmapPointResponse p2 = points.get(1);
        assertEquals("orange", p2.getColorHint());
        assertEquals("Miraflores", p2.getDistrictName());
        assertTrue(p2.getTimeFactor() < p1.getTimeFactor());

        HeatmapPointResponse p3 = points.get(2);
        assertEquals("yellow", p3.getColorHint());
        assertTrue(p3.getTimeFactor() < p2.getTimeFactor());

        HeatmapPointResponse p4 = points.get(3);
        assertEquals("gray", p4.getColorHint());
        assertTrue(p4.getTimeFactor() < p3.getTimeFactor());
    }
}

package com.limasegura.limasegurabackend.controller;

import com.limasegura.limasegurabackend.dto.incident.HeatmapPointResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentDetailResponse;
import com.limasegura.limasegurabackend.dto.incident.IncidentResponse;
import com.limasegura.limasegurabackend.model.IncidentType;
import com.limasegura.limasegurabackend.service.IncidentService;
import com.limasegura.limasegurabackend.service.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IncidentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IncidentService incidentService;

    @Mock
    private SseService sseService;

    @InjectMocks
    private IncidentController incidentController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(incidentController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void testGetHeatmap_ReturnsWeightedPointsForLeaflet() throws Exception {
        HeatmapPointResponse point = HeatmapPointResponse.builder()
                .lat(-12.0464)
                .lng(-77.0428)
                .weight(9.5)
                .colorHint("red")
                .severity(10)
                .sourceWeight(0.95)
                .timeFactor(1.0)
                .incidentId(1L)
                .incidentType("HOMICIDE")
                .title("Sicariato en Av. Canto Grande")
                .build();

        when(incidentService.getHeatmapPoints(any(), any(), any(), any()))
                .thenReturn(List.of(point));

        mockMvc.perform(get("/api/v1/incidents/heatmap"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].lat").value(-12.0464))
                .andExpect(jsonPath("$[0].lng").value(-77.0428))
                .andExpect(jsonPath("$[0].weight").value(9.5))
                .andExpect(jsonPath("$[0].colorHint").value("red"))
                .andExpect(jsonPath("$[0].severity").value(10))
                .andExpect(jsonPath("$[0].incidentType").value("HOMICIDE"));
    }

    @Test
    void testGetById_ReturnsFullDetailWhenPointSelected() throws Exception {
        IncidentDetailResponse.SourceReferenceDto source = IncidentDetailResponse.SourceReferenceDto.builder()
                .sourceItemId(10L)
                .sourceName("RPP Noticias")
                .url("https://rpp.pe/lima/noticia-123")
                .title("Reporte RPP")
                .build();

        IncidentDetailResponse detail = IncidentDetailResponse.builder()
                .id(1L)
                .incidentType(IncidentType.ROBBERY)
                .title("Asalto a mano armada en Miraflores")
                .summary("Dos delincuentes armados en motocicleta asaltaron a comensales")
                .occurredAt(LocalDateTime.now())
                .address("Av. Larco 450")
                .districtName("Miraflores")
                .province("Lima")
                .department("Lima")
                .lat(-12.122)
                .lng(-77.031)
                .confidence(0.95)
                .victimsCount(2)
                .evidenceExcerpt("Cámaras de seguridad registraron la placa de la moto")
                .sources(List.of(source))
                .build();

        when(incidentService.getDetail(1L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/incidents/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Asalto a mano armada en Miraflores"))
                .andExpect(jsonPath("$.districtName").value("Miraflores"))
                .andExpect(jsonPath("$.address").value("Av. Larco 450"))
                .andExpect(jsonPath("$.confidence").value(0.95))
                .andExpect(jsonPath("$.sources[0].sourceName").value("RPP Noticias"))
                .andExpect(jsonPath("$.sources[0].url").value("https://rpp.pe/lima/noticia-123"));
    }

    @Test
    void testGetStream_ReturnsSseEmitter() throws Exception {
        mockMvc.perform(get("/api/v1/incidents/stream"))
                .andExpect(status().isOk());
    }
}

package com.limasegura.limasegurabackend.ingestion.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limasegura.limasegurabackend.config.IngestionConfig;
import com.limasegura.limasegurabackend.config.LlmConfig;
import com.limasegura.limasegurabackend.ingestion.dedup.IncidentDeduplicationService;
import com.limasegura.limasegurabackend.ingestion.geo.DistrictResolver;
import com.limasegura.limasegurabackend.ingestion.geo.GeocodingService;
import com.limasegura.limasegurabackend.model.*;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IncidentExtractionServiceTest {

    @Mock
    private SourceItemRepository sourceItemRepository;

    @Mock
    private IngestionConfig ingestionConfig;

    @Mock
    private LlmConfig llmConfig;

    @Mock
    private LlmClient llmClient;

    @Mock
    private PromptFactory promptFactory;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private GeocodingService geocodingService;

    @Mock
    private DistrictResolver districtResolver;

    @Mock
    private IncidentDeduplicationService deduplicationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private IncidentExtractionService extractionService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(ingestionConfig.getRelevanceKeywords()).thenReturn(List.of(
                "robo", "asalto", "sicariato", "homicidio", "balacera", "extorsion", "asesinato"
        ));
        when(ingestionConfig.getMinConfidence()).thenReturn(0.70);
        when(ingestionConfig.getMaxAttempts()).thenReturn(3);
        when(llmConfig.getModel()).thenReturn("llama-3.1-70b-versatile");

        extractionService = new IncidentExtractionService(
                sourceItemRepository,
                ingestionConfig,
                llmConfig,
                llmClient,
                promptFactory,
                objectMapper,
                geocodingService,
                districtResolver,
                deduplicationService,
                eventPublisher
        );
    }

    @Test
    void testExtractIncidents_NonRelevantContent_IsDiscardedWithoutCallingLLM() {
        // Arrange: Noticia sin palabras clave de delitos (e.g. gastronomía)
        NewsSource source = NewsSource.builder().id(1L).name("El Comercio").type(SourceType.NEWSPAPER_RSS).build();
        SourceItem item = SourceItem.builder()
                .id(100L)
                .newsSource(source)
                .title("Festival Gastronómico en Miraflores")
                .contentText("Este fin de semana se celebrará un festival gastronómico con los mejores platillos peruanos.")
                .status(SourceItemStatus.CONTENT_READY)
                .build();

        when(sourceItemRepository.findById(100L)).thenReturn(Optional.of(item));

        // Act
        extractionService.extractIncidents(100L);

        // Assert
        assertEquals(SourceItemStatus.DISCARDED, item.getStatus());
        verify(llmClient, never()).chat(any(), any()); // LLM no se llama para noticias no relevantes
        verify(sourceItemRepository).save(item);
    }

    @Test
    void testExtractIncidents_RelevantContent_ExtractsIncidentsAndPublishes() {
        // Arrange: Noticia sobre asalto armado
        NewsSource source = NewsSource.builder().id(2L).name("RPP").type(SourceType.NEWSPAPER_RSS).build();
        SourceItem item = SourceItem.builder()
                .id(200L)
                .newsSource(source)
                .title("Asalto a mano armada en San Isidro")
                .contentText("Delincuentes armados perpetraron un asalto y robo a mano armada en la Av. Javier Prado.")
                .publishedAt(LocalDateTime.now())
                .status(SourceItemStatus.CONTENT_READY)
                .build();

        when(sourceItemRepository.findById(200L)).thenReturn(Optional.of(item));
        when(promptFactory.buildExtractionPrompt()).thenReturn("System prompt");
        when(promptFactory.buildUserMessage(any(), any(), any())).thenReturn("User message");
        when(districtResolver.resolve("San Isidro", "Lima", "Lima"))
                .thenReturn(District.builder().ubigeo("150131").name("San Isidro").province("Lima").department("Lima")
                        .centroidLat(-12.098).centroidLng(-77.035).build());

        String recentTimestamp = LocalDateTime.now().minusDays(1).withNano(0).toString();
        String mockLlmJson = """
        {
          "incidents": [
            {
              "incident_type": "ROBBERY",
              "title": "Asalto armado en Av. Javier Prado",
              "summary": "Dos sujetos en moto asaltaron a transeúntes en San Isidro",
              "occurred_at": "%s",
              "location": {
                "address_text": "Av. Javier Prado Este 1200",
                "district": "San Isidro",
                "province": "Lima",
                "department": "Lima"
              },
              "confidence": 0.90,
              "victims_count": 1,
              "evidence_excerpt": "Los asaltantes dispararon al aire"
            }
          ]
        }
        """.formatted(recentTimestamp);

        when(llmClient.chat(any(), any())).thenReturn(mockLlmJson);
        doAnswer(invocation -> {
            Incident inc = invocation.getArgument(0);
            inc.setGeoPrecision(GeoPrecision.ADDRESS);
            inc.setLat(-12.089);
            inc.setLng(-77.034);
            return null;
        }).when(geocodingService).geocodeIncident(any(), any(), any(), any());
        when(deduplicationService.deduplicateOrCreate(any(), any())).thenAnswer(invocation -> {
            Incident inc = invocation.getArgument(0);
            inc.setId(500L);
            return inc;
        });

        // Act
        extractionService.extractIncidents(200L);

        // Assert
        verify(llmClient, times(1)).chat(any(), any());
        verify(geocodingService).geocodeIncident(any(), any(), any(), any());
        verify(deduplicationService).deduplicateOrCreate(any(), eq(item));
        verify(eventPublisher).publishEvent(any());
        assertEquals(SourceItemStatus.EXTRACTED, item.getStatus());
        verify(sourceItemRepository).save(item);
    }
}

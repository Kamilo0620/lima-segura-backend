package com.limasegura.limasegurabackend.ingestion.verify;

import com.limasegura.limasegurabackend.ingestion.fetch.ArticleFetcher;
import com.limasegura.limasegurabackend.ingestion.geo.GeocodingService;
import com.limasegura.limasegurabackend.model.NewsSource;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import com.limasegura.limasegurabackend.model.SourceType;
import com.limasegura.limasegurabackend.repository.IncidentRepository;
import com.limasegura.limasegurabackend.repository.NewsSourceRepository;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CrossVerificationServiceTest {

    @Mock private SourceItemRepository sourceItemRepository;
    @Mock private NewsSourceRepository newsSourceRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private ArticleFetcher articleFetcher;
    @Mock private GeocodingService geocodingService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TransactionTemplate transactionTemplate;

    private CrossVerificationService crossVerificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        crossVerificationService = new CrossVerificationService(
                sourceItemRepository,
                newsSourceRepository,
                incidentRepository,
                articleFetcher,
                new ExactEventParser(),
                geocodingService,
                eventPublisher,
                transactionTemplate
        );
        ReflectionTestUtils.setField(crossVerificationService, "webSearchEnabled", false);
    }

    @Test
    void fusesLocalCorroboratingOutletToPinpointIntersectionAndExactHour() {
        LocalDateTime published = LocalDateTime.of(2026, 11, 14, 10, 0);

        NewsSource src1 = NewsSource.builder().id(1L).name("Trome").type(SourceType.NEWSPAPER_RSS).credibility(7.8).build();
        NewsSource src2 = NewsSource.builder().id(2L).name("RPP Noticias").type(SourceType.NEWSPAPER_RSS).credibility(9.5).build();

        SourceItem primary = SourceItem.builder()
                .id(10L)
                .newsSource(src1)
                .title("Sicarios atacan a empresario en San Juan de Lurigancho")
                .url("https://trome.com/policiales/noticia-10")
                .publishedAt(published)
                .contentText("Un empresario fue atacado a balazos esta mañana en San Juan de Lurigancho.")
                .status(SourceItemStatus.CONTENT_READY)
                .build();

        SourceItem corroborating = SourceItem.builder()
                .id(20L)
                .newsSource(src2)
                .title("Empresario herido tras ataque de sicarios en San Juan de Lurigancho")
                .url("https://rpp.pe/lima/policiales/noticia-20")
                .publishedAt(published.plusMinutes(25))
                .contentText("El ataque a balazos ocurrió a las 8:40 a. m. en el cruce de la Av. Próceres con Av. Tusilagos.")
                .status(SourceItemStatus.CONTENT_READY)
                .build();

        when(sourceItemRepository.findRecentForCorroboration(any())).thenReturn(List.of(corroborating));

        CrossVerificationService.VerificationDossier dossier = crossVerificationService.verifyAndFuse(
                primary, primary.getTitle(), primary.getContentText(), published, "San Juan de Lurigancho");

        assertEquals(1, dossier.corroboratedSources().size());
        assertTrue(dossier.fusedNarrativeText().contains("RPP Noticias"));
        assertNotNull(dossier.suggestedExactAddress());
        assertTrue(dossier.suggestedExactAddress().toLowerCase().contains("tusilagos"));
        assertTrue(dossier.resolvedEventTime().exactHourFound());
        assertEquals(LocalDateTime.of(2026, 11, 14, 8, 40), dossier.resolvedEventTime().occurredAt());
    }
}

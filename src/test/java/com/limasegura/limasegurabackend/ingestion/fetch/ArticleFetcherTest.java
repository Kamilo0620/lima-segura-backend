package com.limasegura.limasegurabackend.ingestion.fetch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.limasegura.limasegurabackend.event.SourceItemContentReadyEvent;
import com.limasegura.limasegurabackend.model.NewsSource;
import com.limasegura.limasegurabackend.model.SourceItem;
import com.limasegura.limasegurabackend.model.SourceItemStatus;
import com.limasegura.limasegurabackend.model.SourceType;
import com.limasegura.limasegurabackend.repository.SourceItemRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleFetcherTest {

    @Mock
    private SourceItemRepository sourceItemRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ObjectMapper objectMapper;
    private ArticleFetcher articleFetcher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        articleFetcher = new ArticleFetcher(sourceItemRepository, eventPublisher, objectMapper);
    }

    @Test
    @DisplayName("Capa 1: Extrae articleBody íntegro desde JSON-LD Schema ignorando cualquier muro de pago o modal")
    void testExtractArticleBodyFromJsonLd_BypassesPaywalls() {
        String htmlWithPaywall = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Robo en Miraflores</title>
                    <script type="application/ld+json">
                    {
                        "@context": "https://schema.org",
                        "@type": "NewsArticle",
                        "headline": "Asalto armado en Miraflores",
                        "articleBody": "Dos delincuentes armados en una moto asaltaron a un cambista en la cuadra 5 de la avenida Larco en Miraflores, llevándose más de 20 mil soles. La Policía Nacional acordonó la zona e inició las investigaciones pertinentes."
                    }
                    </script>
                </head>
                <body>
                    <div id="didomi-notice">Acepta las cookies para continuar navegando</div>
                    <div class="tp-modal">
                        <h2>Contenido exclusivo para suscriptores</h2>
                        <p>Suscríbete ahora por S/ 1 el primer mes para seguir leyendo esta noticia.</p>
                        <button>Suscribirme</button>
                    </div>
                    <div class="paywall-overlay blurred">
                        <p>Texto bloqueado...</p>
                    </div>
                </body>
                </html>
                """;

        Document doc = Jsoup.parse(htmlWithPaywall);
        String extractedBody = articleFetcher.extractArticleBodyFromJsonLd(doc);

        assertThat(extractedBody).isNotNull();
        assertThat(extractedBody).contains("Dos delincuentes armados en una moto asaltaron a un cambista");
        assertThat(extractedBody).contains("Miraflores");
        assertThat(extractedBody).doesNotContain("Suscríbete ahora");
        assertThat(extractedBody).doesNotContain("Acepta las cookies");
    }

    @Test
    @DisplayName("Capa 1: Soporta JSON-LD con grafos complejos (@graph) utilizados por periódicos nacionales")
    void testExtractArticleBodyFromJsonLd_GraphStructure() {
        String htmlWithGraph = """
                <!DOCTYPE html>
                <html>
                <head>
                    <script type="application/ld+json">
                    {
                        "@context": "https://schema.org",
                        "@graph": [
                            {
                                "@type": "WebSite",
                                "name": "Diario La República",
                                "url": "https://larepublica.pe"
                            },
                            {
                                "@type": "NewsArticle",
                                "headline": "Balacera y extorsión en Comas",
                                "articleBody": "Presuntos miembros de una banda criminal dispararon contra un bus de transporte público en Comas exigiendo el pago de cupos diarios a los transportistas. Agentes del Depincri Comas incautaron casquillos en el lugar."
                            }
                        ]
                    }
                    </script>
                </head>
                <body>
                    <div class="muro-suscripcion">Regístrate para leer gratis</div>
                </body>
                </html>
                """;

        Document doc = Jsoup.parse(htmlWithGraph);
        String extractedBody = articleFetcher.extractArticleBodyFromJsonLd(doc);

        assertThat(extractedBody).isNotNull();
        assertThat(extractedBody).contains("Presuntos miembros de una banda criminal dispararon contra un bus");
        assertThat(extractedBody).contains("Comas");
        assertThat(extractedBody).doesNotContain("Regístrate para leer gratis");
    }

    @Test
    @DisplayName("Capa 2: Sanitiza y elimina banners de cookies (Didomi/OneTrust) y modales de paywalls del DOM")
    void testCleanPaywallAndCookieElements_StripsOverlays() {
        String html = """
                <html>
                <body>
                    <div id="onetrust-consent-sdk">
                        <div class="banner-cookies">Acepta todas las cookies para continuar navegando</div>
                    </div>
                    <div id="didomi-notice">Aviso de privacidad y cookies</div>
                    <div class="tp-modal">Muro de suscripción pago mensual</div>
                    <div class="paywall-content">Inicia sesión para continuar</div>
                    <div class="publicidad banner-ad">Publicidad de terceros</div>
                    <article>
                        <h1>Capturan a peligrosos sicarios en San Juan de Lurigancho</h1>
                        <p>Efectivos policiales de la división de homicidios intervinieron a tres sujetos armados en la zona de Huáscar en San Juan de Lurigancho.</p>
                        <p>Los detenidos tenían en su poder dos pistolas y municiones con las que pretendían cometer un nuevo crimen por encargo.</p>
                    </article>
                </body>
                </html>
                """;

        Document doc = Jsoup.parse(html);
        articleFetcher.cleanPaywallAndCookieElements(doc);

        String cleanedHtml = doc.html();
        assertThat(cleanedHtml).doesNotContain("Acepta todas las cookies");
        assertThat(cleanedHtml).doesNotContain("Aviso de privacidad y cookies");
        assertThat(cleanedHtml).doesNotContain("Muro de suscripción");
        assertThat(cleanedHtml).doesNotContain("Publicidad de terceros");
        assertThat(cleanedHtml).contains("Capturan a peligrosos sicarios en San Juan de Lurigancho");
        assertThat(cleanedHtml).contains("San Juan de Lurigancho");
    }

    @Test
    @DisplayName("Capa 5: Post-sanitizador elimina boilerplate residual de cookies y suscripciones")
    void testSanitizeText_RemovesPaywallBoilerplate() {
        String dirtyText = "Sujetos armados asaltaron un minimarket en Surco. Suscríbete a nuestro boletín para más noticias.";
        String clean = ArticleFetcher.sanitizeText(dirtyText);
        assertThat(clean).isEqualTo("Sujetos armados asaltaron un minimarket en Surco.");

        String dirtyText2 = "Asesinato por sicariato en Los Olivos en plena vía pública. Acepta todas las cookies para disfrutar del contenido.";
        String clean2 = ArticleFetcher.sanitizeText(dirtyText2);
        assertThat(clean2).isEqualTo("Asesinato por sicariato en Los Olivos en plena vía pública.");

        String dirtyText3 = "Robo a mano armada frustrado en el Callao. Contenido exclusivo para suscriptores. Todos los derechos reservados.";
        String clean3 = ArticleFetcher.sanitizeText(dirtyText3);
        assertThat(clean3).isEqualTo("Robo a mano armada frustrado en el Callao.");
    }

    @Test
    @DisplayName("Capa 4: Si falla la conexión HTTP web, hace fallback automático al resumen RSS")
    void testFetchContent_FallbackToRssWhenHttpFails() {
        NewsSource source = new NewsSource();
        source.setId(10L);
        source.setName("Diario Correo");
        source.setType(SourceType.NEWSPAPER_RSS);

        SourceItem item = new SourceItem();
        item.setId(99L);
        item.setNewsSource(source);
        item.setTitle("Asalto frustrado en San Martín de Porres");
        item.setUrl("http://127.0.0.1:65534/non-existent-article-url");
        item.setContentText("Delincuentes intentaron robar en una agencia bancaria en la avenida Universitaria en San Martín de Porres.");
        item.setStatus(SourceItemStatus.DISCOVERED);
        item.setAttempts(0);

        when(sourceItemRepository.findById(99L)).thenReturn(Optional.of(item));

        articleFetcher.fetchContent(99L);

        ArgumentCaptor<SourceItem> captor = ArgumentCaptor.forClass(SourceItem.class);
        verify(sourceItemRepository).save(captor.capture());

        SourceItem savedItem = captor.getValue();
        assertThat(savedItem.getStatus()).isEqualTo(SourceItemStatus.CONTENT_READY);
        assertThat(savedItem.getContentText()).contains("Asalto frustrado en San Martín de Porres");
        assertThat(savedItem.getContentText()).contains("San Martín de Porres");

        verify(eventPublisher).publishEvent(any(SourceItemContentReadyEvent.class));
    }
}

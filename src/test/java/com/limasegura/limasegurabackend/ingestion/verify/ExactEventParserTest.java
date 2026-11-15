package com.limasegura.limasegurabackend.ingestion.verify;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class ExactEventParserTest {

    private final ExactEventParser parser = new ExactEventParser();

    @Test
    void extractsIntersectionAndBlockAddressesAccurately() {
        String text1 = "El ataque ocurrió en el cruce de la Av. Javier Prado con Av. Aviación en San Borja.";
        String addr1 = parser.extractExactStreetAddress(text1);
        assertNotNull(addr1);
        assertTrue(addr1.toLowerCase().contains("javier prado"));
        assertTrue(addr1.toLowerCase().contains("aviaci"));
        assertTrue(parser.hasSpecificStreetDetail(addr1));

        String text2 = "Testigos reportaron disparos a la altura de la cuadra 14 de la avenida Abancay en el Cercado.";
        String addr2 = parser.extractExactStreetAddress(text2);
        assertNotNull(addr2);
        assertTrue(addr2.toLowerCase().contains("abancay"));
        assertTrue(addr2.contains("14"));
        assertTrue(parser.hasSpecificStreetDetail(addr2));
    }

    @Test
    void resolvesExactClockTimeAndRelativeExpressionsWithoutFutureTimestamps() {
        LocalDateTime published = LocalDateTime.of(2026, 11, 14, 14, 0);

        ExactEventParser.ResolvedEventTime nightHit = parser.resolveExactOccurredAt(
                "El sicario disparó anoche a las 10:35 de la noche en Miraflores.", published);
        assertTrue(nightHit.exactHourFound());
        assertEquals(LocalDateTime.of(2026, 11, 13, 22, 35), nightHit.occurredAt());

        ExactEventParser.ResolvedEventTime relative = parser.resolveExactOccurredAt(
                "La balacera se produjo hace 2 horas cerca del mercado.", published);
        assertTrue(relative.exactHourFound());
        assertEquals(LocalDateTime.of(2026, 11, 14, 12, 0), relative.occurredAt());
    }

    @Test
    void matchesSameDistrictCrimeReportsAndRejectsDifferentDistricts() {
        LocalDateTime t = LocalDateTime.of(2026, 11, 14, 11, 0);

        assertTrue(parser.isSameEventCandidate(
                "Asaltan camión blindado en la Av. Próceres de San Juan de Lurigancho",
                "Hampones interceptaron unidad en estación Caja de Agua.",
                "Balacera durante robo a camión de caudales en San Juan de Lurigancho",
                "El hecho ocurrió en la Av. Próceres de la Independencia cerca de Caja de Agua.",
                t, t.plusHours(1)));

        assertFalse(parser.isSameEventCandidate(
                "Sicarios asesinan a mototaxista en Comas",
                "Ocurrió en la Av. Túpac Amaru.",
                "Sicarios asesinan a mototaxista en Miraflores",
                "Ocurrió en el malecón.",
                t, t.plusHours(1)));
    }

    @Test
    void rejectsSubwordCaFalsePositivesFromBroadcastAndPrepositionWords() {
        assertNull(parser.extractExactStreetAddress("América TV Callao informó sobre la balacera ocurrida hoy."));
        assertNull(parser.extractExactStreetAddress("Ocurrió cerca de hospital y cerca de losa deportiva en Callao."));
        assertNull(parser.extractExactStreetAddress("Delincuente huyó por el Jirón de los hechos sin dejar rastro."));
        assertNull(parser.extractExactStreetAddress("Capturado en Jirón de, tras persecución en Callao."));
        assertNull(parser.extractExactStreetAddress("Cámaras de seguridad captaron robo: ca. Seg fue clave."));
        assertEquals("avenida El Derby", parser.extractExactStreetAddress("Ocurrió en la avenida El Derby. Los atacantes huyeron."));
        assertTrue(parser.isGenericJurisdictionAddress("Callao"));
        assertTrue(parser.isGenericJurisdictionAddress("Provincia Constitucional del Callao"));
        assertTrue(parser.isGenericJurisdictionAddress("Jurisdicción de Lima"));
        assertFalse(parser.isGenericJurisdictionAddress("Av. Javier Prado Este 4200"));
    }
}

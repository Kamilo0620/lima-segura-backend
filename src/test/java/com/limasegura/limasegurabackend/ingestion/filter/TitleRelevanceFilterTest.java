package com.limasegura.limasegurabackend.ingestion.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TitleRelevanceFilterTest {

    private final TitleRelevanceFilter filter = new TitleRelevanceFilter();

    @Test
    void acceptsRealStreetCrimesAndGangDismantlingInLimaAndCallao() {
        assertTrue(filter.isRelevantLimaCallaoStreetCrime(
                "Sicarios asesinan a balazos a conductor en San Juan de Lurigancho",
                "El ataque ocurrió en la vía pública de Canto Grande.",
                "https://rpp.pe/lima/policiales/noticia-1"));

        assertTrue(filter.isRelevantLimaCallaoStreetCrime(
                "PNP logra el desmantelamiento de una banda criminal extorsiva en Comas",
                "Capturaron a seis integrantes con granadas y armas en búnker.",
                "https://larepublica.pe/sociedad/noticia-2"));

        // Femenicidio real sigue siendo admitido aunque la nota mencione medidas de protección previas
        assertTrue(filter.isRelevantLimaCallaoStreetCrime(
                "Feminicidio en Los Olivos: sujeto atacó a su expareja frente a paradero",
                "Víctima contaba con medidas de protección por violencia familiar previa.",
                "https://rpp.pe/lima/noticia-3"));

        assertTrue(filter.isRelevantCrimeTitle(
                "PNP interviene a dos presuntas bandas criminales en Lima: dos sujetos fueron abatidos y se incautaron armas y municiones - Infobae",
                "https://www.infobae.com/peru/2026/11/14/pnp-interviene"));
    }

    @Test
    void rejectsDomesticViolencePsychologicalAbuseAndFootballBarras() {
        assertFalse(filter.isRelevantLimaCallaoStreetCrime(
                "Agresión en Surco: detienen a hombre por violencia familiar y psicológica",
                "Se dictaron medidas de protección bajo la Ley 30364 tras discusión.",
                "https://elcomercio.pe/lima/sucesos/noticia-4"));

        assertFalse(filter.isRelevantLimaCallaoStreetCrime(
                "Enfrentamiento entre barristas de Trinchera Norte y Comando Svr genera caos en Ate",
                "Hinchas lanzaron piedras antes del partido en vía pública.",
                "https://trome.com/actualidad/noticia-5"));
    }

    @Test
    void rejectsForeignAndProvincialNewsButAllowsLimaAvenuesNamedAfterCountries() {
        assertFalse(filter.isRelevantLimaCallaoStreetCrime(
                "Al menos 14 muertos tras tiroteo en EE. UU.",
                "Autoridades de Texas investigan el hecho.",
                "https://peru21.pe/mundo/noticia-6"));

        assertFalse(filter.isRelevantLimaCallaoStreetCrime(
                "Asaltan a comerciante en el centro de Arequipa",
                "Delincuentes huyeron hacia Yanahuara.",
                "https://larepublica.pe/peru/noticia-7"));

        assertTrue(filter.isRelevantLimaCallaoStreetCrime(
                "Asalto armado en plena vía pública de la Av. Brasil en Breña",
                "Delincuentes en moto interceptaron a un transeúnte.",
                "https://rpp.pe/lima/policiales/noticia-8"));
    }
}

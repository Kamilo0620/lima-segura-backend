package com.limasegura.limasegurabackend.ingestion.filter;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Filtro estricto previo a la descarga y extracción con IA.
 * Garantiza que ÚNICAMENTE se procesen y publiquen delitos callejeros y de seguridad ciudadana
 * que representen un riesgo real para las personas en Lima Metropolitana y el Callao
 * (robos, asaltos, sicariato, homicidios, balaceras, feminicidios, extorsiones, secuestros, agresiones).
 */

@Component
public class TitleRelevanceFilter {

    // Delitos reales de seguridad ciudadana que ponen en riesgo a los ciudadanos en la vía pública o negocios
    private static final List<String> STREET_CRIME_KEYWORDS = List.of(
            "robo", "roban", "robaron", "asalto", "asaltan", "asaltaron", "asaltante", "asaltantes",
            "hurto", "arrebato", "arrebatan", "raquetero", "raqueteo", "marcas", "asaltabancos",
            "sicario", "sicarios", "sicariato", "extorsion", "extorsionador", "extorsionadores", "extorsionan", "cobro de cupos",
            "balacera", "balean", "baleado", "disparo", "disparan", "dispararon", "tiroteo", "acribillan", "acribillado",
            "asesinan", "asesinato", "asesinado", "homicidio", "feminicidio", "matan a", "hallan sin vida", "hallan muerta", "hallan muerto",
            "herido de bala", "arma de fuego", "armados", "encanonan", "abate a delincuente", "abatio a presunto delincuente",
            "secuestro", "secuestran", "secuestrado", "intento de secuestro", "raptan",
            "delincuente", "delincuentes", "delincuencia", "hampon", "hampones", "malhechor", "ladron", "ladrones",
            "banda criminal", "banda extorsiva", "organizacion criminal", "desarticulan", "capturan a",
            "explosivo", "dinamita", "granada", "detonan", "atentado", "artefacto explosivo",
            "agresion", "irrumpen", "barristas", "enfrentamiento", "atacan a balazos", "persecucion"
    );

    // Exclusiones estrictas: países extranjeros, provincias fuera de Lima/Callao, lavado de activos/farándula/judicial/accidentes
    private static final List<String> STRICT_EXCLUSIONS = List.of(
            // Internacionales y agencias extranjeras
            "/colombia/", "/mexico/", "/argentina/", "/espana/", "/estados-unidos/", "/mundo/", "/deportes/", "/espectaculos/",
            "china", "pekin", "shanxi", "shaanxi", "polonia", "cracovia", "nueva jersey", "new jersey", "westfield",
            "estados unidos", "ee. uu.", "ee.uu.", "eeuu", "miami", "california", "texas", "florida", "nueva york",
            "colombia", "bogota", "medellin", "cali", "argentina", "buenos aires", "ushuaia", "milei",
            "mexico", "tamaulipas", "rio bravo", "cdmx", "tlahuac", "sinaloa", "jalisco",
            "cuba", "habana", "habanera", "chile", "santiago de chile", "ecuador", "quito", "guayaquil",
            "venezuela", "caracas", "bolivia", "brasil", "paraguay", "uruguay", "espana", "madrid", "barcelona",
            "ucrania", "rusia", "israel", "gaza", "francia", "italia", "alemania", "japon", "(efe)", "agencia efe",
            "papa leon", "el papa", "vaticano",
            // Provincias del Perú fuera de Lima Metropolitana y Callao
            "arequipa", "trujillo", "la esperanza", "el porvenir", "piura", "sullana", "talara",
            "cusco", "cuzco", "puno", "juliaca", "chiclayo", "lambayeque", "ica", "pisco", "chincha", "nazca",
            "tacna", "huancayo", "junin", "iquitos", "loreto", "pucallpa", "ucayali", "tarapoto", "moyobamba",
            "ayacucho", "cajamarca", "jaen", "huanuco", "tumbes", "moquegua", "apurimac", "huancavelica",
            "pasco", "madre de dios", "ancash", "chimbote", "huaraz", "pataz",
            "huaura", "huacho", "barranca", "supe", "huaral", "chancay", "canete", "imperial", "mala", "oyon", "yauyos", "cajatambo",
            // Delitos de cuello blanco, incautaciones patrimoniales de famosos, judiciales antiguos, accidentes e institucionales
            "lavado de activos", "pronabi", "karla casos", "incautan mansion", "allanan mansion", "incauta mansion",
            "condenan a", "sentencian a", "prision preventiva", "cadena perpetua", "anos de carcel", "anos de prision",
            "fue detenido 18 anos despues", "18 anos despues", "extraditan", "extradicion",
            "pistas clandestinas", "sucamec", "plan operativo interinstitucional", "salud mental",
            "ahogarse", "a punto de ahogarse", "choque de su moto", "accidente de transito",
            "carlos ascues", "estado de ebriedad", "pasarse luz roja", "cobro coima", "recibir s/3.000", "no multar a chofer",
            "convocan a movilizacion", "propuestas urgentes", "que hacer contra el crimen",
            "cristiano ronaldo", "messi", "lapadula", "torneo clausura", "liga 1", "play-offs", "sporting cristal",
            "universitario vs", "alianza lima vs", "horoscopo", "tarantulas"
    );

    public boolean isRelevantCrimeTitle(String title, String url) {
        return isRelevantLimaCallaoStreetCrime(title, "", url);
    }

    /**
     * Evalúa tanto el título como el cuerpo y la URL para asegurar que sea un delito callejero real
     * y no pertenezca a ninguna categoría excluida (extranjero, provincias, lavado de activos, accidentes, notas judiciales).
     */
    public boolean isRelevantLimaCallaoStreetCrime(String title, String content, String url) {
        if (title == null || title.isBlank()) {
            return false;
        }

        String normTitle = normalize(title);
        String normUrl = url != null ? normalize(url) : "";
        // Las exclusiones se buscan SOLO en el título y la URL, y como PALABRAS COMPLETAS.
        // Antes se buscaban como pedazos de texto en todo el artículo: "ica" (la ciudad) coincidía con
        // "larepublica.pe" o "pública", y se descartaban delitos reales de Lima.
        // El cuerpo de la noticia suele mencionar otros lugares de pasada; la ubicación real la decide
        // después la IA y la validación de distrito de Lima/Callao.
        String fullText = normTitle + " " + normUrl;

        // 1. Descartar cualquier noticia internacional, de provincias, lavado de activos, accidentes o notas institucionales
        for (String excluded : STRICT_EXCLUSIONS) {
            if (containsTerm(fullText, excluded)) {
                // Excepción si es el nombre de una avenida de Lima (ej. "Av. Venezuela", "Av. Argentina", "Av. Arequipa", "Av. Brasil", "Av. México", "Av. Tacna", "Av. Canadá")
                if (isLimaAvenueMention(fullText, excluded)) {
                    continue;
                }
                return false;
            }
        }

        // 2. Exigir al menos un término de delito callejero real en el título
        for (String keyword : STREET_CRIME_KEYWORDS) {
            if (normTitle.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /** Coincidencia por palabra completa (las rutas tipo "/mundo/" se buscan tal cual). */
    private static boolean containsTerm(String text, String term) {
        if (term.startsWith("/") || term.startsWith("(")) {
            return text.contains(term);
        }
        int from = 0;
        while (true) {
            int idx = text.indexOf(term, from);
            if (idx < 0) return false;
            int end = idx + term.length();
            boolean leftOk = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
            boolean rightOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (leftOk && rightOk) return true;
            from = idx + 1;
        }
    }

    private boolean isLimaAvenueMention(String normText, String term) {
        if (!List.of("venezuela", "argentina", "arequipa", "brasil", "mexico", "espana", "tacna", "colombia", "ecuador", "bolivia").contains(term)) {
            return false;
        }
        return normText.contains("av. " + term)
                || normText.contains("av " + term)
                || normText.contains("avenida " + term)
                || normText.contains("puente " + term)
                || normText.contains("estacion " + term)
                || normText.contains("jr. " + term)
                || normText.contains("jiron " + term);
    }

    public static String decodeHtmlEntities(String text) {
        if (text == null || text.isEmpty()) return text;
        return text
                .replace("&#225;", "á").replace("&#193;", "Á")
                .replace("&#233;", "é").replace("&#201;", "É")
                .replace("&#237;", "í").replace("&#205;", "Í")
                .replace("&#243;", "ó").replace("&#211;", "Ó")
                .replace("&#250;", "ú").replace("&#218;", "Ú")
                .replace("&#241;", "ñ").replace("&#209;", "Ñ")
                .replace("&#191;", "¿").replace("&#161;", "¡")
                .replace("&aacute;", "á").replace("&eacute;", "é")
                .replace("&iacute;", "í").replace("&oacute;", "ó")
                .replace("&uacute;", "ú").replace("&ntilde;", "ñ")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    private static String normalize(String input) {
        String decoded = decodeHtmlEntities(input);
        String nfd = Normalizer.normalize(decoded.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }
}

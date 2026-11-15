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
            "sicario", "sicarios", "sicariato", "extorsion", "extorsionador", "extorsionadores", "extorsionan",
            "extorsiva", "extorsivo", "nota extorsiva", "carta extorsiva", "cobro de cupos",
            "balacera", "balean", "baleado", "disparo", "disparan", "dispararon", "tiroteo", "acribillan", "acribillado",
            "asesinan", "asesinato", "asesinado", "homicidio", "feminicidio", "matan a", "hallan sin vida", "hallan muerta", "hallan muerto",
            "herido de bala", "arma de fuego", "armas de fuego", "armas y municiones", "armas de guerra", "armas incautadas",
            "armado", "armados", "enfrentamiento armado", "encanonan",
            "abate a delincuente", "abatio a presunto delincuente", "abatido", "abatidos",
            "secuestro", "secuestran", "secuestrado", "secuestradores", "intento de secuestro", "raptan",
            "delincuente", "delincuentes", "delincuencia", "hampon", "hampones", "malhechor", "ladron", "ladrones",
            "pistolero", "pistoleros", "raqueteros",
            "banda criminal", "bandas criminales", "banda extorsiva", "organizacion criminal",
            "desarticula", "desarticulan", "desmantelan", "desmantelamiento",
            "desmantela banda", "desarticulan banda", "capturan a banda", "cae banda", "capturan a",
            "explosivo", "dinamita", "granada", "detonan", "atentado", "artefacto explosivo",
            "agresion", "irrumpen", "atacan a balazos", "persecucion"
    );

    // Violencia doméstica / intrafamiliar / psicológica (excluida salvo que exista un homicidio o feminicidio consumado)
    private static final List<String> DOMESTIC_VIOLENCE_EXCLUSIONS = List.of(
            "violencia familiar", "violencia intrafamiliar", "violencia contra la mujer e integrantes",
            "maltrato familiar", "violencia psicologica", "maltrato psicologico",
            "ley 30364", "ley nro 30364", "medida de proteccion", "medidas de proteccion",
            "agrede a su expareja", "agrede a su pareja", "pension de alimentos", "regimen de visitas"
    );

    // Exclusiones estrictas: barras bravas, países extranjeros, provincias fuera de Lima/Callao, cuello blanco/farándula/judicial/accidentes
    private static final List<String> STRICT_EXCLUSIONS = List.of(
            // Peleas entre barras bravas / hinchas de fútbol
            "barristas", "barrista", "barra brava", "barras bravas", "hinchas de",
            "pelea entre hinchas", "enfrentamiento entre hinchas", "enfrentamiento entre barristas",
            "trinchera norte", "comando svr", "extremo celeste",
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
            "cusco", "cuzco", "puno", "juliaca", "chiclayo", "lambayeque", "en ica", "region ica", "pisco", "chincha", "nazca",
            "tacna", "huancayo", "junin", "iquitos", "loreto", "pucallpa", "ucayali", "tarapoto", "moyobamba",
            "ayacucho", "cajamarca", "jaen", "huanuco", "tumbes", "moquegua", "apurimac", "huancavelica",
            "pasco", "madre de dios", "ancash", "chimbote", "huaraz", "pataz",
            "huaura", "huacho", "barranca", "en supe", "huaral", "chancay", "canete", "imperial", "en mala", "distrito de mala", "oyon", "yauyos", "cajatambo",
            // Delitos de cuello blanco, incautaciones patrimoniales de famosos, judiciales antiguos, accidentes e institucionales
            "lavado de activos", "pronabi", "karla casos", "incautan mansion", "allanan mansion", "incauta mansion",
            "condenan a", "sentencian a", "prision preventiva", "cadena perpetua", "anos de carcel", "anos de prision",
            "fue detenido 18 anos despues", "18 anos despues", "extraditan", "extradicion",
            "pistas clandestinas", "sucamec", "plan operativo interinstitucional", "salud mental",
            "servicio policial efectuado", "conduccion en estado de ebriedad",
            "incendio en", "tras un incendio", "ahogarse", "a punto de ahogarse", "choque de su moto", "accidente de transito",
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
     * y no pertenezca a ninguna categoría excluida (violencia familiar, barras bravas, extranjero, provincias, lavado de activos, accidentes, notas judiciales).
     */
    public boolean isRelevantLimaCallaoStreetCrime(String title, String content, String url) {
        if (title == null || title.isBlank()) {
            return false;
        }

        String normTitle = normalize(title);
        String normContent = content != null ? normalize(content) : "";
        String normUrl = url != null ? normalize(url) : "";
        String combinedTitleUrl = normTitle + " " + normUrl;
        String fullText = combinedTitleUrl + " " + normContent;

        if (isExcludedText(normTitle, fullText)) {
            return false;
        }

        // 2. Exigir al menos un término de delito callejero real en el título
        for (String keyword : STREET_CRIME_KEYWORDS) {
            if (normTitle.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Evalúa si un texto (por ejemplo, fila SIDPOL/GEOMININTER o nota periodística) pertenece a una
     * categoría excluida no relevante para alertas callejeras en Lima y Callao.
     */
    public boolean isExcludedCrimeCategory(String title, String detail) {
        String normTitle = title != null ? normalize(title) : "";
        String normDetail = detail != null ? normalize(detail) : "";
        return isExcludedText(normTitle, normTitle + " " + normDetail);
    }

    private boolean isExcludedText(String normTitle, String fullText) {
        boolean isFemicideOrHomicide = normTitle.contains("feminicidio")
                || normTitle.contains("asesinan")
                || normTitle.contains("hallan sin vida");

        if (!isFemicideOrHomicide) {
            for (String domestic : DOMESTIC_VIOLENCE_EXCLUSIONS) {
                if (fullText.contains(domestic)) {
                    return true;
                }
            }
        }

        for (String excluded : STRICT_EXCLUSIONS) {
            if (fullText.contains(excluded)) {
                if (isLimaAvenueMention(fullText, excluded)) {
                    continue;
                }
                return true;
            }
        }
        return false;
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

package com.limasegura.limasegurabackend.ingestion.verify;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser determinístico para extraer con alta precisión calles/cruces de Lima y Callao,
 * hora real de ocurrencia del hecho delictivo y similitud entre notas de distintos medios
 * sobre un mismo suceso.
 */
@Component
public class ExactEventParser {

    public static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");

    private static final Set<String> SPANISH_STOPWORDS = Set.of(
            "el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "en", "con", "por",
            "para", "que", "su", "sus", "al", "se", "le", "les", "lo", "como", "mas", "pero", "tras",
            "luego", "entre", "sobre", "hacia", "desde", "hasta", "ante", "bajo", "sin", "son", "fue",
            "fueron", "era", "esta", "este", "estos", "estas", "ese", "esa", "esos", "esas", "haber",
            "habia", "quien", "quienes", "donde", "cuando", "mientras", "durante", "dos", "tres",
            "cuatro", "cinco", "ano", "anos", "dia", "dias", "hora", "horas", "lima", "callao", "peru",
            "noticia", "noticias", "ultimo", "ultima", "ultimas", "minuto", "video", "fotos", "foto",
            "rpp", "trome", "larepublica", "elcomercio", "peru21", "exitosa", "latina", "panamericana",
            "america", "andina", "policia", "pnp", "agentes", "serenazgo", "sujeto", "sujetos", "hombre",
            "mujer", "persona", "personas", "victima", "delincuente", "delincuentes", "presunto", "presuntos"
    );

    private static final List<String> LIMA_DISTRICTS = List.of(
            "cercado de lima", "san martin de porres", "san juan de lurigancho", "san juan de miraflores",
            "villa maria del triunfo", "villa el salvador", "santiago de surco", "magdalena del mar",
            "pueblo libre", "jesus maria", "san isidro", "miraflores", "san borja", "la molina",
            "barranco", "chorrillos", "los olivos", "comas", "independencia", "carabayllo",
            "puente piedra", "ancón", "ancon", "santa rosa", "rimac", "breña", "brena", "la victoria",
            "san luis", "el agustino", "santa anita", "ate", "chaclacayo", "lurigancho", "chosica",
            "cieneguilla", "lurin", "pachacamac", "punta hermosa", "punta negra", "san bartolo", "pucusana",
            "lince", "surquillo", "san miguel", "bellavista", "la perla", "la punta", "carmen de la legua",
            "ventanilla", "mi peru"
    );

    private static final Map<String, Integer> MONTHS_ES = Map.ofEntries(
            Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3),
            Map.entry("abril", 4), Map.entry("mayo", 5), Map.entry("junio", 6),
            Map.entry("julio", 7), Map.entry("agosto", 8), Map.entry("septiembre", 9),
            Map.entry("setiembre", 9), Map.entry("octubre", 10), Map.entry("noviembre", 11),
            Map.entry("diciembre", 12)
    );

    // Cruces o intersecciones explícitas (mayor prioridad de ubicación puntual)
    private static final Pattern INTERSECTION_PATTERN = Pattern.compile(
            "(?:cruce|esquina|intersecci[oó]n)\\s+(?:de\\s+)?(?:la[s]?\\s+)?"
                    + "((?<![\\p{L}\\p{N}])(?:av\\.?|avenida|jr\\.?|jir[oó]n|ca\\.?|calle|pasaje|psj\\.?|alameda|malec[oó]n)"
                    + "\\s+[A-ZÁÉÍÓÚÑa-záéíóúñ0-9°.'\\- ]{3,42}?"
                    + "\\s+(?:con|y|e)\\s+(?:la\\s+|el\\s+)?"
                    + "(?:(?<![\\p{L}\\p{N}])(?:av\\.?|avenida|jr\\.?|jir[oó]n|ca\\.?|calle|pasaje|psj\\.?))?\\s*"
                    + "[A-ZÁÉÍÓÚÑa-záéíóúñ0-9°.'\\-]{3,35}(?:\\s+[A-ZÁÉÍÓÚÑa-záéíóúñ0-9°.'\\-]{2,20})?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // "cuadra X de la avenida/jirón/calle Y"
    private static final Pattern CUADRA_OF_STREET_PATTERN = Pattern.compile(
            "(?:a\\s+la\\s+altura\\s+de\\s+la\\s+|en\\s+la\\s+)?cuadra\\s+(\\d{1,3})\\s+(?:de\\s+la\\s+|del\\s+)"
                    + "((?<![\\p{L}\\p{N}])(?:av\\.?|avenida|jr\\.?|jir[oó]n|ca\\.?|calle|pasaje|psj\\.?|alameda|malec[oó]n)"
                    + "\\s+[A-ZÁÉÍÓÚÑa-záéíóúñ0-9°.'\\-]{2,35}(?:\\s+[A-ZÁÉÍÓÚÑa-záéíóúñ0-9°.'\\-]{2,20}){0,3})",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // "Av./Jr./Calle/Pasaje/Carretera X [cdra. N / km N / N° N]"
    private static final Pattern STREET_WITH_DETAIL_PATTERN = Pattern.compile(
            "((?<![\\p{L}\\p{N}])(?:av\\.?|avenida|jr\\.?|jir[oó]n|ca\\.?|calle|pasaje|psj\\.?|alameda|malec[oó]n|"
                    + "carretera\\s+central|panamericana\\s+norte|panamericana\\s+sur|v[ií]a\\s+expresa|"
                    + "autopista\\s+ramiro\\s+prial[eé]|ovalo|óvalo|paradero|estaci[oó]n|puente)"
                    + "\\s+[A-ZÁÉÍÓÚÑa-záéíóúñ0-9°.'\\-]{2,35}(?:\\s+(?!en\\b|del\\b|de\\s+la\\b|el\\b|la\\b|para\\b|que\\b|cuando\\b|tras\\b|donde\\b)[A-ZÁÉÍÓÚÑa-záéíóúñ0-9°.'\\-]{2,20}){0,3}"
                    + "(?:\\s*,?\\s*(?:cdra\\.?|cuadra|n[°ºo.]?|nro\\.?|km\\.?|kil[oó]metro)\\s*\\d{1,5})?)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    // Urbanización / Asentamiento humano / Mercado
    private static final Pattern ZONE_MICRO_PATTERN = Pattern.compile(
            "((?:urb\\.?|urbanizaci[oó]n|aa\\.?\\s?hh\\.?|asentamiento\\s+humano|coop\\.?|cooperativa|mercado)\\s+"
                    + "[A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ0-9.'\\-]{2,25}(?:\\s+[A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑa-záéíóúñ0-9.'\\-]{2,20}){0,3})",
            Pattern.UNICODE_CASE
    );

    // Tiempo relativo ("hace 2 horas", "hace 35 minutos")
    private static final Pattern RELATIVE_TIME_PATTERN = Pattern.compile(
            "hace\\s+(\\d{1,2})\\s+(minutos?|mins?|horas?|hrs?)",
            Pattern.CASE_INSENSITIVE
    );

    // Hora exacta del hecho ("a las 10:30 p. m.", "alrededor de las 3 de la madrugada", "22:15 horas")
    private static final Pattern EXACT_CLOCK_PATTERN = Pattern.compile(
            "(?:a\\s+las|cerca\\s+de\\s+las|alrededor\\s+de\\s+las|pasadas?\\s+las|promediar\\s+las|hacia\\s+las|siendo\\s+las)?\\s*"
                    + "(\\d{1,2})(?::(\\d{2}))?\\s*"
                    + "(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?|horas|hrs|de\\s+la\\s+madrugada|de\\s+la\\s+manana|de\\s+la\\s+mañana|de\\s+la\\s+tarde|de\\s+la\\s+noche)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private static final Pattern EXPLICIT_DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)(?:\\s+(?:de|del)\\s+(20\\d{2}))?",
            Pattern.CASE_INSENSITIVE
    );

    public record ResolvedEventTime(LocalDateTime occurredAt, boolean exactHourFound, String evidence) {
    }

    /**
     * Extrae la dirección más específica posible de un texto periodístico peruano,
     * priorizando cruces de calles y cuadras exactas sobre nombres genéricos.
     */
    public String extractExactStreetAddress(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String best = null;
        int bestScore = -1;

        Matcher inter = INTERSECTION_PATTERN.matcher(text);
        if (inter.find()) {
            String candidate = cleanStreetCandidate("Cruce " + inter.group(1));
            if (candidate != null && candidate.length() >= 8) {
                int score = scoreAddressCandidate(candidate) + 40;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        Matcher cuadra = CUADRA_OF_STREET_PATTERN.matcher(text);
        if (cuadra.find()) {
            String num = cuadra.group(1);
            String street = cleanStreetCandidate(cuadra.group(2));
            if (street != null) {
                String candidate = cleanStreetCandidate(street + " cdra. " + num);
                if (candidate != null) {
                    int score = scoreAddressCandidate(candidate) + 30;
                    if (score > bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }

        Matcher streetMatcher = STREET_WITH_DETAIL_PATTERN.matcher(text);
        while (streetMatcher.find()) {
            String candidate = cleanStreetCandidate(streetMatcher.group(1));
            if (candidate == null || candidate.length() < 6) {
                continue;
            }
            int score = scoreAddressCandidate(candidate);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best == null || bestScore < 15) {
            Matcher zone = ZONE_MICRO_PATTERN.matcher(text);
            if (zone.find()) {
                String candidate = cleanStreetCandidate(zone.group(1));
                if (candidate != null && scoreAddressCandidate(candidate) > bestScore) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    /**
     * Indica si una dirección contiene precisión puntual (cuadra, cruce, km o numeración).
     */
    public boolean hasSpecificStreetDetail(String address) {
        if (address == null || address.isBlank() || isGenericJurisdictionAddress(address)) {
            return false;
        }
        String norm = normalize(address);
        return norm.matches(".*\\b(cuadra|cdra|cruce|esquina|interseccion|km|nro|numero)\\b.*")
                || norm.contains(" con ")
                || norm.matches(".*\\b\\d{2,4}\\b.*");
    }

    /**
     * Determina si la dirección es puramente genérica de distrito/jurisdicción
     * (sin calle, cruce ni punto específico) y no debe marcarse como ADDRESS.
     */
    public boolean isGenericJurisdictionAddress(String address) {
        if (address == null || address.isBlank()) {
            return true;
        }
        String norm = normalize(address).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        if (norm.isEmpty()
                || norm.startsWith("sector urbano")
                || norm.startsWith("jurisdiccion de")
                || norm.startsWith("provincia constitucional")
                || norm.equals("callao")
                || norm.equals("lima")
                || norm.equals("lima peru")
                || norm.equals("callao peru")) {
            return true;
        }
        String stripped = norm
                .replaceAll("\\b(?:cercado de lima|provincia constitucional del callao|lima metropolitana|callao|lima|peru)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (stripped.isEmpty()) {
            return true;
        }
        for (String d : LIMA_DISTRICTS) {
            if (norm.equals(d) || stripped.equals(d) || norm.equals(d + " lima") || norm.equals(d + " callao")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resuelve la fecha/hora exacta de ocurrencia en horario America/Lima a partir del texto
     * noticioso y la fecha de publicación de referencia, evitando horas futuras.
     */
    public ResolvedEventTime resolveExactOccurredAt(String text, LocalDateTime referencePublishedAt) {
        LocalDateTime ref = referencePublishedAt != null ? referencePublishedAt : LocalDateTime.now(LIMA_ZONE);
        LocalDateTime nowLima = LocalDateTime.now(LIMA_ZONE);
        if (ref.isAfter(nowLima)) {
            ref = nowLima;
        }
        if (text == null || text.isBlank()) {
            return new ResolvedEventTime(ref, false, "fallback_published_at");
        }

        String norm = normalize(text);

        // 1. Relativos ("hace 3 horas", "hace 40 minutos")
        Matcher rel = RELATIVE_TIME_PATTERN.matcher(norm);
        if (rel.find()) {
            long qty = Long.parseLong(rel.group(1));
            String unit = rel.group(2);
            LocalDateTime resolved = unit.startsWith("min")
                    ? ref.minusMinutes(Math.min(qty, 360))
                    : ref.minusHours(Math.min(qty, 72));
            return new ResolvedEventTime(clampPast(resolved, nowLima), true, rel.group(0));
        }

        // Determinar fecha base del texto (fecha explícita o ayer/anoche/hoy)
        LocalDate baseDate = resolveExplicitOrRelativeDate(norm, ref);

        // 2. Reloj explícito ("a las 10:30 de la noche", "8:15 p. m.", "03:40 horas")
        Matcher clock = EXACT_CLOCK_PATTERN.matcher(norm);
        while (clock.find()) {
            int rawHour = Integer.parseInt(clock.group(1));
            int minute = clock.group(2) != null ? Integer.parseInt(clock.group(2)) : 0;
            String suffix = clock.group(3) != null ? clock.group(3) : "";

            if (rawHour > 23 || minute > 59) {
                continue;
            }

            int hour24 = convertTo24Hour(rawHour, suffix);
            // Evitar falsos positivos de números sueltos cuando dice "24 horas" o similar
            if (hour24 < 0 || ("horas".equals(suffix) && clock.group(2) == null && rawHour > 23)) {
                continue;
            }

            // Buscar pista de "ayer"/"anoche" cerca del match
            int startCtx = Math.max(0, clock.start() - 45);
            String localContext = norm.substring(startCtx, Math.min(norm.length(), clock.end() + 25));
            LocalDate eventDate = resolveDateForClockMatch(localContext, baseDate, ref, hour24);

            LocalDateTime candidate = LocalDateTime.of(eventDate, LocalTime.of(hour24, minute));
            if (candidate.isAfter(ref.plusMinutes(20))) {
                candidate = candidate.minusDays(1);
            }
            if (Duration.between(candidate, nowLima).toDays() <= 30) {
                return new ResolvedEventTime(clampPast(candidate, nowLima), true, clock.group(0).trim());
            }
        }

        // 3. Periodos del día sin reloj exacto
        if (norm.contains("esta madrugada") || norm.contains("en la madrugada")) {
            LocalDate d = ref.getHour() < 3 ? ref.toLocalDate().minusDays(1) : ref.toLocalDate();
            LocalDateTime candidate = LocalDateTime.of(d, LocalTime.of(3, 30));
            return new ResolvedEventTime(clampPast(candidate.isAfter(ref) ? candidate.minusDays(1) : candidate, nowLima),
                    false, "madrugada");
        }
        if (norm.contains("anoche") || norm.contains("ayer por la noche") || norm.contains("noche de ayer")) {
            LocalDateTime candidate = LocalDateTime.of(ref.toLocalDate().minusDays(1), LocalTime.of(22, 0));
            return new ResolvedEventTime(clampPast(candidate, nowLima), false, "anoche");
        }
        if (norm.contains("ayer por la tarde") || norm.contains("tarde de ayer")) {
            LocalDateTime candidate = LocalDateTime.of(ref.toLocalDate().minusDays(1), LocalTime.of(16, 30));
            return new ResolvedEventTime(clampPast(candidate, nowLima), false, "ayer_tarde");
        }
        if (norm.contains("esta manana") || norm.contains("hoy por la manana")) {
            LocalDateTime candidate = LocalDateTime.of(ref.toLocalDate(), LocalTime.of(9, 0));
            if (candidate.isAfter(ref)) {
                candidate = ref.minusHours(1);
            }
            return new ResolvedEventTime(clampPast(candidate, nowLima), false, "esta_manana");
        }
        if (norm.contains("ayer")) {
            LocalDateTime candidate = LocalDateTime.of(ref.toLocalDate().minusDays(1), ref.toLocalTime());
            return new ResolvedEventTime(clampPast(candidate, nowLima), false, "ayer");
        }

        return new ResolvedEventTime(clampPast(ref, nowLima), false, "published_at");
    }

    /**
     * Genera una consulta concisa en español para buscar notas corroboradoras del mismo hecho
     * en Google News Perú / Bing News Perú.
     */
    public String buildCorroborationQuery(String title, String districtName) {
        Set<String> tokens = extractSalientTokens(title);
        List<String> selected = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() >= 4 || token.matches("\\d+")) {
                selected.add(token);
            }
            if (selected.size() >= 5) {
                break;
            }
        }
        if (districtName != null && !districtName.isBlank()
                && !"Lima Metropolitana".equalsIgnoreCase(districtName)) {
            String normDist = normalize(districtName);
            boolean alreadyInQuery = selected.stream().anyMatch(normDist::contains);
            if (!alreadyInQuery) {
                selected.add(districtName);
            }
        }
        selected.add("Lima");
        return String.join(" ", selected);
    }

    /**
     * Evalúa determinísticamente si dos noticias corresponden al mismo hecho delictivo real
     * (evitando falsos positivos entre distritos distintos o fechas alejadas).
     */
    public boolean isSameEventCandidate(
            String titleA,
            String bodyA,
            String titleB,
            String bodyB,
            LocalDateTime timeA,
            LocalDateTime timeB
    ) {
        if (titleA == null || titleB == null || titleA.isBlank() || titleB.isBlank()) {
            return false;
        }
        if (timeA != null && timeB != null && Math.abs(Duration.between(timeA, timeB).toHours()) > 60) {
            return false;
        }

        String textA = normalize(titleA + " " + (bodyA != null ? bodyA : ""));
        String textB = normalize(titleB + " " + (bodyB != null ? bodyB : ""));

        String distA = detectSingleDistrict(textA);
        String distB = detectSingleDistrict(textB);
        if (distA != null && distB != null && !distA.equals(distB)) {
            return false;
        }

        Set<String> tokensTitleA = extractSalientTokens(titleA);
        Set<String> tokensTitleB = extractSalientTokens(titleB);
        Set<String> intersection = new HashSet<>(tokensTitleA);
        intersection.retainAll(tokensTitleB);

        if (intersection.size() >= 3) {
            return true;
        }

        Set<String> allA = extractSalientTokens(titleA + " " + truncate(bodyA, 450));
        Set<String> allB = extractSalientTokens(titleB + " " + truncate(bodyB, 450));
        Set<String> fullIntersect = new HashSet<>(allA);
        fullIntersect.retainAll(allB);

        Set<String> union = new HashSet<>(tokensTitleA);
        union.addAll(tokensTitleB);
        double titleJaccard = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();

        if (titleJaccard >= 0.25 && intersection.size() >= 2) {
            return true;
        }
        // Coincidencia fuerte en distrito + calle o apellido/banda específica en cuerpo
        return distA != null && distA.equals(distB) && fullIntersect.size() >= 4 && !intersection.isEmpty();
    }

    public String detectSingleDistrict(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return null;
        }
        String matched = null;
        for (String district : LIMA_DISTRICTS) {
            if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(district) + "(?![a-z0-9])")
                    .matcher(normalizedText).find()) {
                if (matched == null) {
                    matched = district;
                } else if (!matched.equals(district)) {
                    return null; // Ambiguo si menciona varios distritos
                }
            }
        }
        return matched;
    }

    public Set<String> extractSalientTokens(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        String norm = normalize(text).replaceAll("[^a-z0-9\\s]", " ");
        Set<String> out = new LinkedHashSet<>();
        for (String token : norm.split("\\s+")) {
            if (token.length() < 3 || SPANISH_STOPWORDS.contains(token)) {
                continue;
            }
            out.add(token);
        }
        return out;
    }

    public static String normalize(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }

    private int scoreAddressCandidate(String candidate) {
        if (candidate == null) return -1;
        String norm = normalize(candidate);
        int score = 10;
        if (norm.contains(" con ") || norm.contains("cruce") || norm.contains("esquina")) score += 35;
        if (norm.contains("cdra") || norm.contains("cuadra") || norm.contains("km")) score += 25;
        if (norm.matches(".*\\b\\d{1,5}\\b.*")) score += 15;
        if (norm.contains("ovalo") || norm.contains("estacion") || norm.contains("puente") || norm.contains("mercado")) score += 12;
        if (candidate.length() > 65) score -= 15;
        return score;
    }

    private String cleanStreetCandidate(String raw) {
        if (raw == null) return null;
        String sentenceTrimmed = raw
                .replaceFirst("(?i)(?<!\\b(?:av|jr|ca|psj|urb|cdra|km|nro|sta|sto|san|sra|dr|gral))\\.\\s+.*$", "")
                .replaceFirst("\\s*[|;]\\s*.*$", "");
        String cleaned = sentenceTrimmed.replaceAll("\\s+", " ")
                .replaceAll("^[,;:.\\-\\s]+|[,;:.\\-\\s]+$", "")
                .replaceAll("(?i)(?:\\s+(?:de|del|la|el|los|las|en|con|sin|para|por|y))+$", "")
                .trim();
        if (cleaned.length() < 6) return null;
        String norm = normalize(cleaned).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        // Evitar falsos positivos de sufijos como "America TV", "ca. Seg" (cámaras de seguridad) o prefijos truncados
        if (norm.matches("^(?:av|avenida|jr|jiron|ca|calle|pasaje|psj|alameda|malecon)$")
                || norm.matches("^(?:av|avenida|jr|jiron|ca|calle|pasaje|psj|alameda|malecon)\\s+[a-z0-9]{1,3}$")
                || norm.matches("^(?:ca\\s+)?(?:tv|america\\s+tv|rpp|latina|atv|panamericana|exitosa|trome|correo|el\\s+comercio|la\\s+republica|peru21|seg|seguridad|camara|camaras|video|policia|serenazgo)\\b.*")
                || norm.matches("^ca\\s+(?:de|del|al|en|con|negra|alegre|seg)\\b.*")
                || norm.matches("^(?:av|avenida|jr|jiron|ca|calle|pasaje|psj)\\s+(?:de\\s+los\\s+hechos|de\\s+la\\s+zona|del\\s+lugar)\\b.*")) {
            return null;
        }
        return cleaned.length() > 95 ? cleaned.substring(0, 95).trim() : cleaned;
    }

    private LocalDate resolveExplicitOrRelativeDate(String norm, LocalDateTime ref) {
        Matcher m = EXPLICIT_DATE_PATTERN.matcher(norm);
        if (m.find()) {
            int day = Integer.parseInt(m.group(1));
            Integer month = MONTHS_ES.get(m.group(2));
            int year = m.group(3) != null ? Integer.parseInt(m.group(3)) : ref.getYear();
            if (month != null && day >= 1 && day <= 31) {
                try {
                    LocalDate explicit = LocalDate.of(year, month, day);
                    if (explicit.isAfter(ref.toLocalDate())) {
                        explicit = explicit.minusYears(1);
                    }
                    if (!explicit.isBefore(ref.toLocalDate().minusDays(45))) {
                        return explicit;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (norm.contains("anoche") || norm.contains("ayer")) {
            return ref.toLocalDate().minusDays(1);
        }
        return ref.toLocalDate();
    }

    private LocalDate resolveDateForClockMatch(String localContext, LocalDate baseDate, LocalDateTime ref, int hour24) {
        if (localContext.contains("anoche") || localContext.contains("ayer")) {
            return ref.toLocalDate().minusDays(1);
        }
        if (localContext.contains("madrugada") && hour24 >= 0 && hour24 <= 6) {
            return ref.getHour() < 6 ? ref.toLocalDate() : ref.toLocalDate();
        }
        return baseDate;
    }

    private int convertTo24Hour(int rawHour, String suffix) {
        String s = suffix.replace(".", "").replace(" ", "").toLowerCase(Locale.ROOT);
        if (s.contains("pm") || s.contains("tarde") || s.contains("noche")) {
            if (rawHour == 12) return 12;
            if (rawHour >= 1 && rawHour <= 11) return rawHour + 12;
            return rawHour;
        }
        if (s.contains("am") || s.contains("madrugada") || s.contains("manana") || s.contains("mañana")) {
            if (rawHour == 12) return 0;
            if (rawHour >= 0 && rawHour <= 11) return rawHour;
            return -1;
        }
        if ("horas".equals(s) || "hrs".equals(s)) {
            return (rawHour >= 0 && rawHour <= 23) ? rawHour : -1;
        }
        return (rawHour >= 0 && rawHour <= 23) ? rawHour : -1;
    }

    private LocalDateTime clampPast(LocalDateTime candidate, LocalDateTime nowLima) {
        return candidate.isAfter(nowLima) ? nowLima : candidate;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }
}

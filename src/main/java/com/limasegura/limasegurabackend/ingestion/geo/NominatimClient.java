package com.limasegura.limasegurabackend.ingestion.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.limasegura.limasegurabackend.model.District;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class NominatimClient {

    private static final double MIN_LIMA_LAT = -12.50;
    private static final double MAX_LIMA_LAT = -11.55;
    private static final double MIN_LIMA_LNG = -77.22;
    private static final double MAX_LIMA_LNG = -76.62;

    private static final Pattern INTERSECTION_SPLIT = Pattern.compile(
            "^(?:cruce\\s+(?:de\\s+)?(?:las?\\s+)?)?(.+?)\\s+(?:con|y|e)\\s+(?:las?\\s+|el\\s+)?(.+?)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Optional<Double[]>> cache = new ConcurrentHashMap<>();
    private final String googleMapsApiKey;
    private long lastNominatimCallAtMs = 0L;

    @Autowired
    public NominatimClient(
            ObjectMapper objectMapper,
            @Value("${google.maps.api-key:}") String googleMapsApiKey
    ) {
        this.objectMapper = objectMapper;
        this.googleMapsApiKey = googleMapsApiKey != null ? googleMapsApiKey.trim() : "";
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4500);
        factory.setReadTimeout(6500);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public NominatimClient(ObjectMapper objectMapper) {
        this(objectMapper, "");
    }

    /**
     * Consulta Nominatim estructurado dentro de Lima Metropolitana y Callao.
     */
    public Double[] geocode(String address) {
        if (address == null || address.isBlank()) return null;

        String clean = address.trim();
        String query = clean.toLowerCase(Locale.ROOT).endsWith("peru") ? clean : clean + ", Peru";
        Optional<Double[]> cached = cache.get("nom:" + query);
        if (cached != null) {
            return cached.orElse(null);
        }

        try {
            throttleNominatim();
            String responseStr = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("nominatim.openstreetmap.org")
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "jsonv2")
                            .queryParam("countrycodes", "pe")
                            .queryParam("viewbox", "-77.22,-11.70,-76.62,-12.42")
                            .queryParam("bounded", 1)
                            .queryParam("accept-language", "es")
                            .queryParam("limit", 3)
                            .build())
                    .header(HttpHeaders.USER_AGENT, "LimaSeguraBot/2.0 (Academic Safety Observatory)")
                    .retrieve()
                    .body(String.class);

            JsonNode arrayNode = objectMapper.readTree(responseStr);
            if (arrayNode.isArray()) {
                for (JsonNode item : arrayNode) {
                    String category = item.path("category").asText("");
                    String type = item.path("type").asText("");
                    if ("boundary".equals(category) && "administrative".equals(type)) {
                        continue; // Nunca aceptar centroides administrativos genéricos como calles
                    }
                    double lat = item.path("lat").asDouble(Double.NaN);
                    double lon = item.path("lon").asDouble(Double.NaN);
                    if (isInsideMetroBox(lat, lon)) {
                        Double[] coords = new Double[]{lat, lon};
                        cache.put("nom:" + query, Optional.of(coords));
                        return coords;
                    }
                }
            }
            cache.put("nom:" + query, Optional.empty());
        } catch (Exception e) {
            log.debug("Nominatim lookup failed for '{}': {}", query, e.getMessage());
        }
        return null;
    }

    /**
     * Resuelve con precisión calle, cuadra o cruce mediante:
     * 1) Google Geocoding API (si hay GOOGLE_MAPS_API_KEY configurado),
     * 2) Overpass API (cruce exacto de dos vías en OpenStreetMap),
     * 3) Photon by Komoot (búsqueda geográfica sesgada al centroide del distrito).
     */
    public Double[] geocodePrecisionContext(String rawAddress, District district) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return null;
        }
        if (!googleMapsApiKey.isEmpty()) {
            Double[] google = geocodeWithGoogle(rawAddress, district);
            if (google != null) {
                return google;
            }
        }
        Double[] overpass = tryOverpassIntersection(rawAddress, district);
        if (overpass != null) {
            return overpass;
        }
        return geocodeWithPhoton(rawAddress, district);
    }

    public Double[] geocodeWithGoogle(String rawAddress, District district) {
        if (googleMapsApiKey.isEmpty() || rawAddress == null || rawAddress.isBlank()) {
            return null;
        }
        String province = resolveProvinceLabel(district);
        String distName = district != null && district.getName() != null ? district.getName() : "";
        String query = rawAddress.trim()
                + (!distName.isBlank() ? ", " + distName : "")
                + ", " + province + ", Peru";
        String key = "gmap:" + query.toLowerCase(Locale.ROOT);
        Optional<Double[]> cached = cache.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }
        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("maps.googleapis.com")
                            .path("/maps/api/geocode/json")
                            .queryParam("address", query)
                            .queryParam("components", "country:PE")
                            .queryParam("bounds", "-12.38,-77.22|-11.68,-76.65")
                            .queryParam("language", "es")
                            .queryParam("key", googleMapsApiKey)
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(body);
            if ("OK".equals(root.path("status").asText()) && root.path("results").size() > 0) {
                JsonNode loc = root.path("results").get(0).path("geometry").path("location");
                double lat = loc.path("lat").asDouble(Double.NaN);
                double lng = loc.path("lng").asDouble(Double.NaN);
                if (isInsideMetroBox(lat, lng)) {
                    Double[] coords = new Double[]{lat, lng};
                    cache.put(key, Optional.of(coords));
                    return coords;
                }
            }
            cache.put(key, Optional.empty());
        } catch (Exception e) {
            log.debug("Google Geocoding fallback unavailable for '{}': {}", query, e.getMessage());
        }
        return null;
    }

    public Double[] tryOverpassIntersection(String rawAddress, District district) {
        if (rawAddress == null) return null;
        String lower = normalizeAscii(rawAddress);
        if (!lower.contains(" con ") && !lower.contains("cruce") && !lower.contains("esquina") && !lower.contains(" y ")) {
            return null;
        }
        Matcher m = INTERSECTION_SPLIT.matcher(rawAddress.trim());
        if (!m.find()) {
            return null;
        }
        String tokenA = extractCoreRoadToken(m.group(1));
        String tokenB = extractCoreRoadToken(m.group(2));
        if (tokenA.length() < 3 || tokenB.length() < 3 || tokenA.equals(tokenB)) {
            return null;
        }
        String cacheKey = "ovp:" + (district != null ? district.getUbigeo() : "all") + ":" + tokenA + "|" + tokenB;
        Optional<Double[]> cached = cache.get(cacheKey);
        if (cached != null) {
            return cached.orElse(null);
        }

        double minLat = -12.30, maxLat = -11.78, minLng = -77.18, maxLng = -76.78;
        if (district != null && district.getCentroidLat() != null && district.getCentroidLng() != null) {
            minLat = district.getCentroidLat() - 0.055;
            maxLat = district.getCentroidLat() + 0.055;
            minLng = district.getCentroidLng() - 0.055;
            maxLng = district.getCentroidLng() + 0.055;
        }
        String ql = String.format(Locale.US,
                "[out:json][timeout:5];"
                        + "way[\"highway\"][\"name\"~\"%s\",i](%.4f,%.4f,%.4f,%.4f)->.w1;"
                        + "way[\"highway\"][\"name\"~\"%s\",i](%.4f,%.4f,%.4f,%.4f)->.w2;"
                        + "node(w.w1)(w.w2);"
                        + "out body 1;",
                tokenA, minLat, minLng, maxLat, maxLng,
                tokenB, minLat, minLng, maxLat, maxLng);
        try {
            String body = restClient.post()
                    .uri("https://overpass-api.de/api/interpreter")
                    .contentType(MediaType.TEXT_PLAIN)
                    .header(HttpHeaders.USER_AGENT, "LimaSeguraBot/2.0 (Academic Safety Observatory)")
                    .body(ql)
                    .retrieve()
                    .body(String.class);
            JsonNode elements = objectMapper.readTree(body).path("elements");
            if (elements.isArray() && elements.size() > 0) {
                double lat = elements.get(0).path("lat").asDouble(Double.NaN);
                double lon = elements.get(0).path("lon").asDouble(Double.NaN);
                if (isInsideMetroBox(lat, lon)) {
                    Double[] coords = new Double[]{lat, lon};
                    cache.put(cacheKey, Optional.of(coords));
                    return coords;
                }
            }
            cache.put(cacheKey, Optional.empty());
        } catch (Exception e) {
            log.debug("Overpass intersection lookup skipped ({}): {}", tokenA + " x " + tokenB, e.getMessage());
        }
        return null;
    }

    public Double[] geocodeWithPhoton(String rawAddress, District district) {
        if (rawAddress == null || rawAddress.isBlank()) {
            return null;
        }
        String formatted = rawAddress.trim()
                .replaceAll("(?i)\\b(?:cdra\\.?|cuadra)\\s*(\\d{1,3})\\b", "$100")
                .replaceAll("(?i)^cruce\\s+", "");
        String province = resolveProvinceLabel(district);
        String distName = district != null && district.getName() != null ? district.getName() : "";
        String q = formatted + (!distName.isBlank() ? ", " + distName : "") + ", " + province + ", Peru";
        String key = "photon:" + q.toLowerCase(Locale.ROOT);
        Optional<Double[]> cached = cache.get(key);
        if (cached != null) {
            return cached.orElse(null);
        }

        double biasLat = district != null && district.getCentroidLat() != null ? district.getCentroidLat() : -12.0464;
        double biasLng = district != null && district.getCentroidLng() != null ? district.getCentroidLng() : -77.0428;

        try {
            String body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("photon.komoot.io")
                            .path("/api")
                            .queryParam("q", q)
                            .queryParam("lat", String.format(Locale.US, "%.5f", biasLat))
                            .queryParam("lon", String.format(Locale.US, "%.5f", biasLng))
                            .queryParam("limit", 5)
                            .queryParam("lang", "es")
                            .build())
                    .header(HttpHeaders.USER_AGENT, "LimaSeguraBot/2.0 (Academic Safety Observatory)")
                    .retrieve()
                    .body(String.class);
            JsonNode features = objectMapper.readTree(body).path("features");
            if (features.isArray()) {
                for (JsonNode feature : features) {
                    JsonNode props = feature.path("properties");
                    String cc = props.path("countrycode").asText("");
                    if (!cc.isEmpty() && !"PE".equalsIgnoreCase(cc)) {
                        continue;
                    }
                    String osmValue = props.path("osm_value").asText("");
                    if ("administrative".equalsIgnoreCase(osmValue) || "city".equalsIgnoreCase(osmValue) || "state".equalsIgnoreCase(osmValue)) {
                        continue;
                    }
                    JsonNode coordsNode = feature.path("geometry").path("coordinates");
                    if (coordsNode.isArray() && coordsNode.size() >= 2) {
                        double lon = coordsNode.get(0).asDouble(Double.NaN);
                        double lat = coordsNode.get(1).asDouble(Double.NaN);
                        if (isInsideMetroBox(lat, lon)) {
                            Double[] coords = new Double[]{lat, lon};
                            cache.put(key, Optional.of(coords));
                            return coords;
                        }
                    }
                }
            }
            cache.put(key, Optional.empty());
        } catch (Exception e) {
            log.debug("Photon lookup failed for '{}': {}", q, e.getMessage());
        }
        return null;
    }

    private synchronized void throttleNominatim() throws InterruptedException {
        long now = System.currentTimeMillis();
        long waitMs = 1050L - (now - lastNominatimCallAtMs);
        if (waitMs > 0) {
            Thread.sleep(waitMs);
        }
        lastNominatimCallAtMs = System.currentTimeMillis();
    }

    private static String resolveProvinceLabel(District district) {
        if (district != null && district.getUbigeo() != null && district.getUbigeo().startsWith("0701")) {
            return "Callao";
        }
        if (district != null && district.getProvince() != null && !district.getProvince().isBlank()) {
            return district.getProvince();
        }
        return "Lima";
    }

    private static boolean isInsideMetroBox(double lat, double lon) {
        return !Double.isNaN(lat) && !Double.isNaN(lon)
                && lat >= MIN_LIMA_LAT && lat <= MAX_LIMA_LAT
                && lon >= MIN_LIMA_LNG && lon <= MAX_LIMA_LNG;
    }

    private static String extractCoreRoadToken(String raw) {
        String norm = normalizeAscii(raw)
                .replaceAll("^(?:la|el|los|las)\\s+", "")
                .replaceAll("^(?:av|avenida|jr|jiron|ca|calle|pasaje|psj|alameda|malecon|prolongacion|prol)\\.?\\s+", "")
                .replaceAll("\\s+(?:en|cdra|cuadra|km).*$", "")
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return norm;
    }

    private static String normalizeAscii(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}

package com.limasegura.limasegurabackend.ingestion.geo;

import com.limasegura.limasegurabackend.ingestion.verify.ExactEventParser;
import com.limasegura.limasegurabackend.model.District;
import com.limasegura.limasegurabackend.model.GeoPrecision;
import com.limasegura.limasegurabackend.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final DistrictResolver districtResolver;
    private final NominatimClient nominatimClient;
    private final ExactEventParser exactEventParser = new ExactEventParser();

    // Puntos exactos (plazas, óvalos, puentes, estaciones, malls, urbanizaciones pequeñas)
    private static final Map<String, double[]> PINPOINT_LIMA_LOCATIONS = new LinkedHashMap<>();
    // Corredores extensos multi-cuadra / multi-distrito con vector inicio -> fin {lat1, lng1, lat2, lng2}
    private static final Map<String, double[]> CORRIDOR_LIMA_LOCATIONS = new LinkedHashMap<>();

    static {
        // Puntos exactos de alta precisión (plazas, puentes, estaciones, malls, sectores delimitados)
        PINPOINT_LIMA_LOCATIONS.put("bajada armendariz", new double[]{-12.1342, -77.0278});
        PINPOINT_LIMA_LOCATIONS.put("bajada balta", new double[]{-12.1255, -77.0302});
        PINPOINT_LIMA_LOCATIONS.put("parque simon bolivar", new double[]{-12.0789, -77.0871});
        PINPOINT_LIMA_LOCATIONS.put("via expresa javier prado", new double[]{-12.0908, -77.0261});
        PINPOINT_LIMA_LOCATIONS.put("puente mexico", new double[]{-12.0769, -77.0282});
        PINPOINT_LIMA_LOCATIONS.put("estacion mexico", new double[]{-12.0769, -77.0282});
        PINPOINT_LIMA_LOCATIONS.put("estacion canada", new double[]{-12.0823, -77.0270});
        PINPOINT_LIMA_LOCATIONS.put("via expresa grau", new double[]{-12.0586, -77.0322});
        PINPOINT_LIMA_LOCATIONS.put("estacion central", new double[]{-12.0568, -77.0365});
        PINPOINT_LIMA_LOCATIONS.put("centro civico", new double[]{-12.0566, -77.0372});
        PINPOINT_LIMA_LOCATIONS.put("museo metropolitano", new double[]{-12.0628, -77.0369});
        PINPOINT_LIMA_LOCATIONS.put("parque de la exposicion", new double[]{-12.0625, -77.0362});
        PINPOINT_LIMA_LOCATIONS.put("plaza san martin", new double[]{-12.0516, -77.0346});
        PINPOINT_LIMA_LOCATIONS.put("plaza dos de mayo", new double[]{-12.0464, -77.0428});
        PINPOINT_LIMA_LOCATIONS.put("plaza bolognesi", new double[]{-12.0605, -77.0416});
        PINPOINT_LIMA_LOCATIONS.put("av espana", new double[]{-12.0575, -77.0402});
        PINPOINT_LIMA_LOCATIONS.put("av el derby", new double[]{-12.0998, -76.9688});
        PINPOINT_LIMA_LOCATIONS.put("el derby", new double[]{-12.0998, -76.9688});
        PINPOINT_LIMA_LOCATIONS.put("jockey plaza", new double[]{-12.0856, -76.9765});
        PINPOINT_LIMA_LOCATIONS.put("mesa redonda", new double[]{-12.0528, -77.0252});
        PINPOINT_LIMA_LOCATIONS.put("barrios altos", new double[]{-12.0485, -77.0155});
        PINPOINT_LIMA_LOCATIONS.put("las malvinas", new double[]{-12.0432, -77.0484});
        PINPOINT_LIMA_LOCATIONS.put("jr de la union", new double[]{-12.0484, -77.0329});
        PINPOINT_LIMA_LOCATIONS.put("gamarra", new double[]{-12.0667, -77.0136});
        PINPOINT_LIMA_LOCATIONS.put("parque canepa", new double[]{-12.0672, -77.0152});
        PINPOINT_LIMA_LOCATIONS.put("la parada", new double[]{-12.0642, -77.0098});
        PINPOINT_LIMA_LOCATIONS.put("matute", new double[]{-12.0685, -77.0221});
        PINPOINT_LIMA_LOCATIONS.put("balconcillo", new double[]{-12.0792, -77.0218});
        PINPOINT_LIMA_LOCATIONS.put("chuquitanta", new double[]{-11.9465, -77.0982});
        PINPOINT_LIMA_LOCATIONS.put("fiori", new double[]{-12.0092, -77.0598});
        PINPOINT_LIMA_LOCATIONS.put("caqueta", new double[]{-12.0358, -77.0435});
        PINPOINT_LIMA_LOCATIONS.put("puente caqueta", new double[]{-12.0358, -77.0435});
        PINPOINT_LIMA_LOCATIONS.put("habich", new double[]{-12.0245, -77.0522});
        PINPOINT_LIMA_LOCATIONS.put("condevilla", new double[]{-12.0128, -77.0785});
        PINPOINT_LIMA_LOCATIONS.put("plaza norte", new double[]{-12.0064, -77.0592});
        PINPOINT_LIMA_LOCATIONS.put("megaplaza", new double[]{-11.9950, -77.0612});
        PINPOINT_LIMA_LOCATIONS.put("mercado unicachi", new double[]{-11.9332, -77.0585});
        PINPOINT_LIMA_LOCATIONS.put("urbanizacion pro", new double[]{-11.9388, -77.0752});
        PINPOINT_LIMA_LOCATIONS.put("primera de pro", new double[]{-11.9388, -77.0752});
        PINPOINT_LIMA_LOCATIONS.put("huandoy", new double[]{-11.9642, -77.0768});
        PINPOINT_LIMA_LOCATIONS.put("collique", new double[]{-11.9185, -77.0365});
        PINPOINT_LIMA_LOCATIONS.put("zapallal", new double[]{-11.8425, -77.0862});
        PINPOINT_LIMA_LOCATIONS.put("canto grande", new double[]{-11.9839, -76.9985});
        PINPOINT_LIMA_LOCATIONS.put("bayovar", new double[]{-11.9585, -76.9862});
        PINPOINT_LIMA_LOCATIONS.put("huascar", new double[]{-11.9612, -76.9995});
        PINPOINT_LIMA_LOCATIONS.put("zarate", new double[]{-12.0235, -77.0052});
        PINPOINT_LIMA_LOCATIONS.put("caja de agua", new double[]{-12.0298, -77.0128});
        PINPOINT_LIMA_LOCATIONS.put("mangomarca", new double[]{-12.0122, -76.9815});
        PINPOINT_LIMA_LOCATIONS.put("mariscal caceres", new double[]{-11.9448, -76.9812});
        PINPOINT_LIMA_LOCATIONS.put("puente nuevo", new double[]{-12.0336, -77.0019});
        PINPOINT_LIMA_LOCATIONS.put("ovalo santa anita", new double[]{-12.0572, -76.9712});
        PINPOINT_LIMA_LOCATIONS.put("huaycan", new double[]{-12.0128, -76.8225});
        PINPOINT_LIMA_LOCATIONS.put("ceres", new double[]{-12.0268, -76.9195});
        PINPOINT_LIMA_LOCATIONS.put("santa clara", new double[]{-12.0165, -76.8852});
        PINPOINT_LIMA_LOCATIONS.put("marquez", new double[]{-11.9389, -77.1319});
        PINPOINT_LIMA_LOCATIONS.put("ramon castilla", new double[]{-12.0211, -77.1185});
        PINPOINT_LIMA_LOCATIONS.put("sarita colonia", new double[]{-12.0155, -77.1292});
        PINPOINT_LIMA_LOCATIONS.put("barracones", new double[]{-12.0638, -77.1415});
        PINPOINT_LIMA_LOCATIONS.put("minka", new double[]{-12.0462, -77.1095});
        PINPOINT_LIMA_LOCATIONS.put("bocanegra", new double[]{-12.0195, -77.0995});
        PINPOINT_LIMA_LOCATIONS.put("urbanizacion aeropuerto", new double[]{-12.0225, -77.1025});
        PINPOINT_LIMA_LOCATIONS.put("aeropuerto", new double[]{-12.0219, -77.1143});
        PINPOINT_LIMA_LOCATIONS.put("ovalo la perla", new double[]{-12.0672, -77.1145});
        PINPOINT_LIMA_LOCATIONS.put("carrillo albornoz", new double[]{-12.0540, -77.1235});
        PINPOINT_LIMA_LOCATIONS.put("dos de mayo", new double[]{-12.0612, -77.1318});
        PINPOINT_LIMA_LOCATIONS.put("guardia chalaca", new double[]{-12.0545, -77.1192});
        PINPOINT_LIMA_LOCATIONS.put("oquendo", new double[]{-11.9885, -77.1215});
        PINPOINT_LIMA_LOCATIONS.put("dulanto", new double[]{-12.0510, -77.1270});
        PINPOINT_LIMA_LOCATIONS.put("santa marina", new double[]{-12.0485, -77.1310});
        PINPOINT_LIMA_LOCATIONS.put("ciudad pachacutec", new double[]{-11.8528, -77.1482});
        PINPOINT_LIMA_LOCATIONS.put("proyecto pachacutec", new double[]{-11.8528, -77.1482});
        PINPOINT_LIMA_LOCATIONS.put("ovalo higuereta", new double[]{-12.1282, -77.0018});
        PINPOINT_LIMA_LOCATIONS.put("puente atocongo", new double[]{-12.1485, -76.9812});
        PINPOINT_LIMA_LOCATIONS.put("mall del sur", new double[]{-12.1542, -76.9822});
        PINPOINT_LIMA_LOCATIONS.put("pamplona alta", new double[]{-12.1425, -76.9585});
        PINPOINT_LIMA_LOCATIONS.put("ciudad de dios", new double[]{-12.1585, -76.9735});

        // Corredores arteriales con segmento {lat1, lng1, lat2, lng2} para repartir puntos en todo el eje real
        CORRIDOR_LIMA_LOCATIONS.put("costa verde", new double[]{-12.0920, -77.0785, -12.1540, -77.0245});
        CORRIDOR_LIMA_LOCATIONS.put("circuito de playas", new double[]{-12.0920, -77.0785, -12.1540, -77.0245});
        CORRIDOR_LIMA_LOCATIONS.put("av de los patriotas", new double[]{-12.0845, -77.0925, -12.0735, -77.0815});
        CORRIDOR_LIMA_LOCATIONS.put("malecon costanera", new double[]{-12.0855, -77.0905, -12.0815, -77.0785});
        CORRIDOR_LIMA_LOCATIONS.put("av costanera", new double[]{-12.0855, -77.0905, -12.0815, -77.0785});
        CORRIDOR_LIMA_LOCATIONS.put("via expresa", new double[]{-12.0615, -77.0325, -12.1185, -77.0245});
        CORRIDOR_LIMA_LOCATIONS.put("paseo de la republica", new double[]{-12.0585, -77.0335, -12.1215, -77.0245});
        CORRIDOR_LIMA_LOCATIONS.put("av abancay", new double[]{-12.0475, -77.0298, -12.0558, -77.0265});
        CORRIDOR_LIMA_LOCATIONS.put("av alfonso ugarte", new double[]{-12.0468, -77.0428, -12.0608, -77.0416});
        CORRIDOR_LIMA_LOCATIONS.put("av grau", new double[]{-12.0598, -77.0355, -12.0552, -77.0185});
        CORRIDOR_LIMA_LOCATIONS.put("av colonial", new double[]{-12.0458, -77.0455, -12.0545, -77.1185});
        CORRIDOR_LIMA_LOCATIONS.put("av argentina", new double[]{-12.0432, -77.0485, -12.0472, -77.1145});
        CORRIDOR_LIMA_LOCATIONS.put("av venezuela", new double[]{-12.0552, -77.0425, -12.0588, -77.1055});
        CORRIDOR_LIMA_LOCATIONS.put("av aviacion", new double[]{-12.0665, -77.0115, -12.1125, -77.0015});
        CORRIDOR_LIMA_LOCATIONS.put("av mexico", new double[]{-12.0752, -77.0285, -12.0748, -77.0055});
        CORRIDOR_LIMA_LOCATIONS.put("av universitaria", new double[]{-11.9455, -77.0745, -12.0585, -77.0815});
        CORRIDOR_LIMA_LOCATIONS.put("av tupac amaru", new double[]{-11.8855, -77.0345, -12.0185, -77.0535});
        CORRIDOR_LIMA_LOCATIONS.put("panamericana norte", new double[]{-11.8955, -77.0645, -12.0225, -77.0585});
        CORRIDOR_LIMA_LOCATIONS.put("canta callao", new double[]{-11.9585, -77.1015, -12.0055, -77.0885});
        CORRIDOR_LIMA_LOCATIONS.put("av gran chimu", new double[]{-12.0245, -77.0115, -12.0225, -76.9955});
        CORRIDOR_LIMA_LOCATIONS.put("av proceres de la independencia", new double[]{-12.0215, -77.0085, -11.9685, -77.0015});
        CORRIDOR_LIMA_LOCATIONS.put("carretera central", new double[]{-12.0445, -76.9685, -12.0185, -76.8955});
        CORRIDOR_LIMA_LOCATIONS.put("av saenz pena", new double[]{-12.0575, -77.1315, -12.0625, -77.1455});
        CORRIDOR_LIMA_LOCATIONS.put("av faucett", new double[]{-12.0125, -77.1105, -12.0595, -77.0995});
        CORRIDOR_LIMA_LOCATIONS.put("av elmer faucett", new double[]{-12.0125, -77.1105, -12.0595, -77.0995});
        CORRIDOR_LIMA_LOCATIONS.put("av nestor gambetta", new double[]{-11.9085, -77.1315, -12.0365, -77.1295});
        CORRIDOR_LIMA_LOCATIONS.put("av arequipa", new double[]{-12.0665, -77.0365, -12.1185, -77.0295});
        CORRIDOR_LIMA_LOCATIONS.put("av javier prado", new double[]{-12.0925, -77.0495, -12.0835, -76.9685});
        CORRIDOR_LIMA_LOCATIONS.put("av la marina", new double[]{-12.0785, -77.0955, -12.0765, -77.0645});
        CORRIDOR_LIMA_LOCATIONS.put("av brasil", new double[]{-12.0625, -77.0435, -12.0925, -77.0685});
        CORRIDOR_LIMA_LOCATIONS.put("av angamos", new double[]{-12.1135, -77.0345, -12.1115, -76.9885});
        CORRIDOR_LIMA_LOCATIONS.put("av benavides", new double[]{-12.1255, -77.0295, -12.1295, -76.9825});
        CORRIDOR_LIMA_LOCATIONS.put("av huaylas", new double[]{-12.1645, -77.0225, -12.1915, -77.0115});
        CORRIDOR_LIMA_LOCATIONS.put("av canada", new double[]{-12.0835, -77.0295, -12.0815, -76.9855});
    }

    public void geocodeIncident(Incident incident) {
        geocodeIncident(incident, null, null, null);
    }

    public void geocodeIncident(Incident incident, Double llmLat, Double llmLng, String landmark) {
        District district = incident.getDistrict();
        String sanitizedAddress = sanitizeIncomingAddress(incident.getAddress());
        if (sanitizedAddress != null && !sanitizedAddress.equals(incident.getAddress())) {
            incident.setAddress(sanitizedAddress);
        } else if (sanitizedAddress == null && incident.getAddress() != null && !incident.getAddress().isBlank()) {
            // Limpiar pseudo-calles truncadas ("ca. Seg") para que no contaminen UI ni geocoders
            if (exactEventParser.normalize(incident.getAddress()).matches("^ca\\.?\\s+seg\\.?$")) {
                incident.setAddress(district != null ? "Jurisdicción de " + district.getName() : null);
            }
        }

        String address = incident.getAddress();
        boolean genericAddress = exactEventParser.isGenericJurisdictionAddress(address);

        String rawSearch = ((address != null && !genericAddress ? address : "") + " "
                + (landmark != null ? landmark : "") + " "
                + (incident.getTitle() != null ? incident.getTitle() : "")).trim();
        String normSearch = normalizeForGazetteer(rawSearch);

        boolean hasSpecificStreet = !genericAddress && exactEventParser.hasSpecificStreetDetail(address);
        boolean isIntersection = hasSpecificStreet && isIntersectionAddress(address);

        // 1. Si hay cuadra, intersección, km o número exacto: Overpass/Photon/Google primero, luego Nominatim
        if (hasSpecificStreet) {
            Double[] exactCoords = resolveExternalStreetCoords(address, district);
            if (exactCoords != null) {
                double[] clamped = clampToLimaLand(exactCoords[0], exactCoords[1]);
                incident.setLat(clamped[0]);
                incident.setLng(clamped[1]);
                incident.setGeoPrecision(isIntersection ? GeoPrecision.INTERSECTION : GeoPrecision.ADDRESS);
                log.info("Geocoded exact {} '{}' to [{}, {}]",
                        incident.getGeoPrecision(), address, clamped[0], clamped[1]);
                return;
            }
        }

        // 2. Puntos exactos conocidos (plazas, óvalos, puentes, estaciones, malls, sectores delimitados)
        if (applyGazetteerMatch(incident, normSearch, PINPOINT_LIMA_LOCATIONS, "Lima pinpoint gazetteer",
                genericAddress ? GeoPrecision.NEIGHBORHOOD : GeoPrecision.ADDRESS)) {
            return;
        }

        // 3. Direcciones de calles específicas (sin cuadra ni número) solo cuando NO son nombres genéricos de distrito
        if (!hasSpecificStreet && !genericAddress && address != null && !address.isBlank()) {
            Double[] coords = resolveExternalStreetCoords(address, district);
            if (coords != null) {
                double[] clamped = clampToLimaLand(coords[0], coords[1]);
                incident.setLat(clamped[0]);
                incident.setLng(clamped[1]);
                incident.setGeoPrecision(GeoPrecision.ADDRESS);
                log.info("Geocoded address '{}' to [{}, {}]", address, clamped[0], clamped[1]);
                return;
            }
        }

        // 4. Coordenadas puntuales extraídas por Gemini (validadas estrictamente dentro del radio del distrito)
        if (llmLat != null && llmLng != null && isValidLimaCallaoCoord(llmLat, llmLng, district)) {
            double microJitterLat = (Math.random() - 0.5) * 0.0003;
            double microJitterLng = (Math.random() - 0.5) * 0.0003;
            double[] clamped = clampToLimaLand(llmLat + microJitterLat, llmLng + microJitterLng);
            incident.setLat(clamped[0]);
            incident.setLng(clamped[1]);
            incident.setGeoPrecision(!genericAddress ? GeoPrecision.ADDRESS : GeoPrecision.DISTRICT);
            log.info("Geocoded '{}' via Gemini precision coordinates -> [{}, {}]",
                    incident.getTitle(), incident.getLat(), incident.getLng());
            return;
        }

        // 5. Fallback de corredor vial largo distribuido a lo largo del eje real dentro del distrito
        if (applyCorridorSegmentMatch(incident, normSearch)) {
            return;
        }

        // 6. Fallback final: distribución orgánica multi-nodo en áreas urbanas terrestres del distrito
        if (district != null) {
            double[] distributed = distributeWithinDistrict(incident, district);
            double[] clamped = clampToLimaLand(distributed[0], distributed[1], incidentSeed(incident));
            incident.setLat(clamped[0]);
            incident.setLng(clamped[1]);
            incident.setGeoPrecision(GeoPrecision.DISTRICT);
            return;
        }

        log.warn("Could not geocode incident: {}", incident.getTitle());
        incident.setGeoPrecision(GeoPrecision.DEPARTMENT);
    }

    private Double[] resolveExternalStreetCoords(String rawAddress, District district) {
        Double[] precisionCoords = nominatimClient.geocodePrecisionContext(rawAddress, district);
        if (precisionCoords != null && isValidLimaCallaoCoord(precisionCoords[0], precisionCoords[1], district)) {
            return precisionCoords;
        }
        return tryNominatimQueries(rawAddress, district);
    }

    private Double[] tryNominatimQueries(String rawAddress, District district) {
        if (rawAddress == null || rawAddress.isBlank() || exactEventParser.isGenericJurisdictionAddress(rawAddress)) {
            return null;
        }
        String trimmed = rawAddress.trim();

        // Convertir "cdra. 5" / "cuadra 5" a numeración "500" y "Cruce X con Y" a "X y Y"
        String osmFormatted = trimmed
                .replaceAll("(?i)\\b(?:cdra\\.?|cuadra)\\s*(\\d{1,3})\\b", "$100")
                .replaceAll("(?i)^cruce\\s+", "")
                .replaceAll("(?i)\\s+(?:con|e)\\s+", " y ");

        String province = resolveProvinceLabel(district);
        Set<String> queries = new LinkedHashSet<>();
        if (district != null && district.getName() != null && !district.getName().isBlank()) {
            queries.add(osmFormatted + ", " + district.getName() + ", " + province + ", Peru");
            if (!"Lima".equalsIgnoreCase(province)) {
                queries.add(osmFormatted + ", " + district.getName() + ", Callao, Peru");
            }
            if (!osmFormatted.equals(trimmed)) {
                queries.add(trimmed + ", " + district.getName() + ", " + province + ", Peru");
            }
        }
        queries.add(osmFormatted + ", " + province + ", Peru");
        queries.add(trimmed + ", Peru");

        for (String q : queries) {
            Double[] coords = nominatimClient.geocode(q);
            if (coords != null && isValidLimaCallaoCoord(coords[0], coords[1], district)) {
                return coords;
            }
        }
        return null;
    }

    private boolean applyGazetteerMatch(Incident incident, String normSearch, Map<String, double[]> gazetteer,
                                        String label, GeoPrecision targetPrecision) {
        for (Map.Entry<String, double[]> entry : gazetteer.entrySet()) {
            String key = entry.getKey();
            if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(key) + "(?![a-z0-9])").matcher(normSearch).find()) {
                double[] base = entry.getValue();
                if (!isValidLimaCallaoCoord(base[0], base[1], incident.getDistrict())) {
                    log.debug("Skipping out-of-district gazetteer match '{}' for district '{}'",
                            key, incident.getDistrict() != null ? incident.getDistrict().getName() : "unknown");
                    continue;
                }
                long h = incidentSeed(incident);
                double jLat = ((((h >>> 8) & 0xfffL) / (double) 0x1000L) - 0.5) * 0.0005;
                double jLng = ((((h >>> 24) & 0xfffL) / (double) 0x1000L) - 0.5) * 0.0005;
                double[] clamped = clampToLimaLand(base[0] + jLat, base[1] + jLng, h);
                incident.setLat(clamped[0]);
                incident.setLng(clamped[1]);
                incident.setGeoPrecision(targetPrecision);
                log.info("Geocoded '{}' via {} ('{}') -> [{}, {}]",
                        incident.getTitle(), label, key, incident.getLat(), incident.getLng());
                return true;
            }
        }
        return false;
    }

    private boolean applyCorridorSegmentMatch(Incident incident, String normSearch) {
        for (Map.Entry<String, double[]> entry : CORRIDOR_LIMA_LOCATIONS.entrySet()) {
            String key = entry.getKey();
            if (!Pattern.compile("(?<![a-z0-9])" + Pattern.quote(key) + "(?![a-z0-9])").matcher(normSearch).find()) {
                continue;
            }
            double[] seg = entry.getValue();
            long h1 = incidentSeed(incident);
            long h2 = mix64(h1 ^ 0x517cc1b727220a95L);
            double chosenLat = Double.NaN;
            double chosenLng = Double.NaN;
            double baseT = ((h1 >>> 12) & 0xffffL) / (double) 0x10000L;

            for (int attempt = 0; attempt < 12; attempt++) {
                double t = (baseT + attempt * 0.0833) % 1.0;
                double lat = seg[0] + t * (seg[2] - seg[0]);
                double lng = seg[1] + t * (seg[3] - seg[1]);
                if (isValidLimaCallaoCoord(lat, lng, incident.getDistrict())) {
                    double offsetLat = ((((h2 >>> 10) & 0xfffL) / (double) 0x1000L) - 0.5) * 0.0006;
                    double offsetLng = ((((h2 >>> 26) & 0xfffL) / (double) 0x1000L) - 0.5) * 0.0006;
                    chosenLat = lat + offsetLat;
                    chosenLng = lng + offsetLng;
                    break;
                }
            }
            if (!Double.isNaN(chosenLat) && isValidLimaCallaoCoord(chosenLat, chosenLng, incident.getDistrict())) {
                double[] clamped = clampToLimaLand(chosenLat, chosenLng, h1);
                incident.setLat(clamped[0]);
                incident.setLng(clamped[1]);
                incident.setGeoPrecision(GeoPrecision.ADDRESS);
                log.info("Geocoded '{}' via Lima corridor segment ('{}') -> [{}, {}]",
                        incident.getTitle(), key, incident.getLat(), incident.getLng());
                return true;
            }
        }
        return false;
    }

    /**
     * Garantiza matemáticamente que ninguna coordenada en la bahía de Lima/Callao
     * (San Miguel, Magdalena, San Isidro, Miraflores, Barranco, Chorrillos, La Perla) caiga en el mar.
     */
    private static final Map<String, double[][]> DISTRICT_URBAN_ANCHORS = Map.ofEntries(
            Map.entry("070101", new double[][]{
                    {-12.0582, -77.1310}, // Sáenz Peña / Callao Centro
                    {-12.0510, -77.1270}, // Santa Marina Norte
                    {-12.0545, -77.1192}, // Guardia Chalaca
                    {-12.0462, -77.1095}, // Av. Argentina / Minka
                    {-12.0368, -77.0985}, // Av. Faucett / Quilca
                    {-12.0195, -77.0995}, // Urb. Bocanegra
                    {-12.0165, -77.1270}, // Sarita Colonia
                    {-11.9885, -77.1215}, // Oquendo / Gambetta
                    {-12.0425, -77.1215}, // Av. Colonial / San José
                    {-12.0315, -77.0865}, // Previ / Bertello
                    {-12.0255, -77.1155}, // Urb. Sesquicentenario
                    {-11.9595, -77.1295}  // Márquez / Oquendo Norte
            }),
            Map.entry("070107", new double[][]{
                    {-11.8768, -77.1260}, // Metro Ventanilla / Ciudad del Deporte
                    {-11.8565, -77.1335}, // Pachacútec Sector E
                    {-11.8945, -77.1255}, // Av. Gambetta / Angamos
                    {-11.8680, -77.1185}, // Ciudad Satélite Ventanilla
                    {-11.8445, -77.1375}, // Pachacútec Norte
                    {-11.8835, -77.1355}  // Villa Los Reyes
            }),
            Map.entry("150103", new double[][]{
                    {-12.0265, -76.9210}, // Ceres / Carretera Central
                    {-12.0315, -76.9065}, // Santa Clara
                    {-12.0465, -76.9475}, // Salamanca
                    {-12.0125, -76.8835}, // Huaycán
                    {-12.0385, -76.9355}, // Valdiviezo
                    {-12.0195, -76.8965}  // Gloria Grande
            }),
            Map.entry("150106", new double[][]{
                    {-11.8725, -77.0325}, // Av. Túpac Amaru Km 19 / San Pedro
                    {-11.8535, -77.0225}, // El Progreso
                    {-11.8845, -77.0435}, // Santa Isabel / Chimpu Ocllo
                    {-11.8615, -77.0485}, // Tungasuca / Torre Blanca
                    {-11.8415, -77.0185}  // Lomas de Carabayllo / Km 22
            }),
            Map.entry("150128", new double[][]{
                    {-12.0295, -77.0335}, // Av. Alcázar
                    {-12.0165, -77.0275}, // Amancaes / Flor de Amancaes
                    {-12.0385, -77.0265}, // Alameda de los Descalzos / Acho
                    {-12.0245, -77.0395}  // Villa El Ángel / Caquetá Rímac
            }),
            Map.entry("150132", new double[][]{
                    {-12.0155, -77.0035}, // Zárate
                    {-12.0025, -77.0075}, // Próceres / Caja de Agua
                    {-11.9885, -77.0045}, // Las Flores / Canto Grande
                    {-11.9725, -76.9995}, // San Carlos / Wiesse
                    {-11.9585, -76.9895}, // Bayóvar
                    {-11.9465, -76.9815}  // Mariscal Cáceres
            }),
            Map.entry("150135", new double[][]{
                    {-12.0285, -77.0585}, // Caquetá / Habich
                    {-12.0135, -77.0715}, // Av. Perú
                    {-11.9995, -77.0845}, // Tomás Valle / Universitaria
                    {-11.9855, -77.0895}, // Canta Callao
                    {-12.0085, -77.0945}, // Dominicos / San Diego
                    {-11.9915, -77.0655}  // Fiori / Gerardo Unger
            }),
            Map.entry("150110", new double[][]{
                    {-11.9495, -77.0585}, // Belaunde / Túpac Amaru
                    {-11.9365, -77.0515}, // El Retablo
                    {-11.9245, -77.0435}, // Collique
                    {-11.9585, -77.0645}  // Santa Luzmila
            }),
            Map.entry("150142", new double[][]{
                    {-12.2125, -76.9395}, // Óvalo La Paloma
                    {-12.2015, -76.9475}, // Av. El Sol
                    {-12.2235, -76.9295}, // Pastor Sevilla
                    {-12.1925, -76.9565}  // Separadora Industrial
            }),
            Map.entry("150143", new double[][]{
                    {-12.1625, -76.9435}, // Pesquero / Pachacútec
                    {-12.1515, -76.9315}, // José Carlos Mariátegui
                    {-12.1725, -76.9215}, // Tablada de Lurín
                    {-12.1445, -76.9495}  // Nueva Esperanza
            }),
            Map.entry("150108", new double[][]{
                    {-12.1695, -77.0185}, // Matellini / Huaylas
                    {-12.1845, -77.0085}, // La Campiña
                    {-12.1775, -76.9965}, // Delicias de Villa
                    {-12.1935, -77.0135}  // Los Cedros de Villa
            }),
            Map.entry("150101", new double[][]{
                    {-12.0465, -77.0305}, // Jr. de la Unión / Abancay
                    {-12.0555, -77.0365}, // Centro Cívico / Av. España
                    {-12.0445, -77.0515}, // Av. Argentina / Malvinas
                    {-12.0515, -77.0155}, // Barrios Altos
                    {-12.0615, -77.0415}, // Plaza Bolognesi / Guzmán Blanco
                    {-12.0435, -77.0685}, // Dueñas / Óvalo Conde de Superunda
                    {-12.0535, -77.0615}, // Urb. Chacra Ríos Norte
                    {-12.0585, -77.0235}, // Av. Grau / Parinacochas Cercado
                    {-12.0415, -77.0415}, // Av. Emancipación Oeste / Tacna Norte
                    {-12.0565, -77.0485}  // Cipriano Dulanto / Elio
            })
    );

    // Puntos de referencia costeros (norte a sur) para mantener todo incidente sobre tierra firme sin muros artificiales.
    private static final double[][] COASTLINE_LAND_LIMITS = {
            {-11.770, -77.168},
            {-11.830, -77.152},
            {-11.880, -77.145},
            {-11.950, -77.136},
            {-12.020, -77.134},
            {-12.055, -77.145},
            {-12.070, -77.132},
            {-12.078, -77.112},
            {-12.088, -77.094},
            {-12.098, -77.074},
            {-12.108, -77.056},
            {-12.122, -77.039},
            {-12.142, -77.025},
            {-12.165, -77.023},
            {-12.205, -77.018},
            {-12.250, -76.935}
    };

    private static long incidentSeed(Incident incident) {
        String key = (incident.getId() != null ? incident.getId() + "|" : "")
                + (incident.getTitle() != null ? incident.getTitle() : "") + "|"
                + (incident.getAddress() != null ? incident.getAddress() : "") + "|"
                + (incident.getDistrict() != null ? incident.getDistrict().getUbigeo() : "");
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < key.length(); i++) {
            h ^= key.charAt(i);
            h *= 0x100000001b3L;
        }
        return mix64(h);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private static final Set<String> EXPANSIVE_DISTRICTS = Set.of(
            "150102", // Ancón
            "150103", // Ate
            "150106", // Carabayllo
            "150109", // Cieneguilla
            "150118", // Lurigancho
            "150119", // Lurín
            "150123", // Pachacámac
            "150132", // San Juan de Lurigancho
            "150142", // Villa El Salvador
            "070107"  // Ventanilla
    );

    private static double[] distributeWithinDistrict(Incident incident, District district) {
        long h1 = incidentSeed(incident);
        long h2 = mix64(h1 ^ 0x9e3779b97f4a7c15L);
        double[][] anchors = district.getUbigeo() != null ? DISTRICT_URBAN_ANCHORS.get(district.getUbigeo()) : null;
        double baseLat;
        double baseLng;
        double maxRadius;
        if (anchors != null && anchors.length > 0) {
            int idx = (int) Long.remainderUnsigned(h1, anchors.length);
            baseLat = anchors[idx][0];
            baseLng = anchors[idx][1];
            maxRadius = 0.0068;
        } else {
            baseLat = district.getCentroidLat();
            baseLng = district.getCentroidLng();
            maxRadius = 0.0068;
        }
        double u = ((h1 >>> 11) & 0x1fffffL) / (double) 0x200000L;
        double v = ((h2 >>> 11) & 0x1fffffL) / (double) 0x200000L;
        double r = 0.0010 + Math.sqrt(u) * (maxRadius - 0.0010);
        double theta = v * 2.0 * Math.PI;
        return new double[]{baseLat + r * Math.sin(theta), baseLng + r * Math.cos(theta)};
    }

    public static double[] clampToLimaLand(double lat, double lng) {
        long seed = Double.doubleToLongBits(lat * 10000.0 + lng);
        return clampToLimaLand(lat, lng, mix64(seed));
    }

    public static double[] clampToLimaLand(double lat, double lng, long seed) {
        double safeLng = lng;
        double minLandLng = interpolateCoastMinLng(lat);
        if (safeLng < minLandLng) {
            double jitterEast = 0.0008 + (((seed >>> 8) & 0xfffL) / (double) 0x1000L) * 0.0036;
            safeLng = minLandLng + jitterEast;
        }
        return new double[]{lat, safeLng};
    }

    private static double interpolateCoastMinLng(double lat) {
        if (lat >= COASTLINE_LAND_LIMITS[0][0]) {
            return COASTLINE_LAND_LIMITS[0][1];
        }
        int last = COASTLINE_LAND_LIMITS.length - 1;
        if (lat <= COASTLINE_LAND_LIMITS[last][0]) {
            return COASTLINE_LAND_LIMITS[last][1];
        }
        for (int i = 0; i < last; i++) {
            double latA = COASTLINE_LAND_LIMITS[i][0];
            double lngA = COASTLINE_LAND_LIMITS[i][1];
            double latB = COASTLINE_LAND_LIMITS[i + 1][0];
            double lngB = COASTLINE_LAND_LIMITS[i + 1][1];
            if (lat <= latA && lat >= latB) {
                double t = (latA - lat) / (latA - latB);
                return lngA + t * (lngB - lngA);
            }
        }
        return -77.135;
    }

    public boolean isValidLimaCallaoCoord(double lat, double lng, District district) {
        if (lat < -12.52 || lat > -11.55 || lng < -77.22 || lng > -76.62) {
            return false;
        }
        if (district != null && district.getCentroidLat() != null && district.getCentroidLng() != null) {
            double dLat = lat - district.getCentroidLat();
            double dLng = lng - district.getCentroidLng();
            double approxKm = Math.hypot(dLat, dLng) * 111.0;
            double maxKm = (district.getUbigeo() != null && EXPANSIVE_DISTRICTS.contains(district.getUbigeo()))
                    ? 16.0
                    : 10.5;
            if (approxKm > maxKm) {
                return false;
            }
        }
        return true;
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

    private String sanitizeIncomingAddress(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String norm = ExactEventParser.normalize(raw);
        if (norm.matches("^ca\\.?\\s+seg\\.?$")) {
            return null;
        }
        String trimmed = raw
                .replaceFirst("(?i)(?<!\\b(?:av|jr|ca|psj|urb|cdra|km|nro|sta|sto|san|sra|dr|gral))\\.\\s+.*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isIntersectionAddress(String address) {
        if (address == null) return false;
        String norm = normalize(address);
        return norm.contains("cruce") || norm.contains("esquina") || norm.contains("interseccion") || norm.contains(" con ");
    }

    private static String normalizeForGazetteer(String input) {
        String norm = normalize(input);
        return norm
                .replaceAll("\\bavenida\\b", "av")
                .replaceAll("\\bjiron\\b", "jr")
                .replaceAll("\\bpasaje\\b", "psj")
                .replaceAll("\\bprolongacion\\b", "prol")
                .replaceAll("\\boscar\\s+r\\s+benavides\\b", "av colonial")
                .replaceAll("\\bav\\s+av\\b", "av")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}

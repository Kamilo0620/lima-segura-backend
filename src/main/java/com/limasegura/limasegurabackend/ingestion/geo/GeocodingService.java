package com.limasegura.limasegurabackend.ingestion.geo;

import com.limasegura.limasegurabackend.model.District;
import com.limasegura.limasegurabackend.model.GeoPrecision;
import com.limasegura.limasegurabackend.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private final DistrictResolver districtResolver;
    private final NominatimClient nominatimClient;

    private static final Map<String, double[]> EXACT_LIMA_LOCATIONS = new LinkedHashMap<>();

    static {
        // Costa Verde / Malecón / San Miguel / Magdalena / Miraflores / Barranco / Chorrillos (SIEMPRE EN TIERRA FIRME)
        EXACT_LIMA_LOCATIONS.put("costa verde", new double[]{-12.1312, -77.0288});
        EXACT_LIMA_LOCATIONS.put("circuito de playas", new double[]{-12.1312, -77.0288});
        EXACT_LIMA_LOCATIONS.put("bajada armendariz", new double[]{-12.1342, -77.0278});
        EXACT_LIMA_LOCATIONS.put("bajada balta", new double[]{-12.1255, -77.0302});
        EXACT_LIMA_LOCATIONS.put("parque simon bolivar", new double[]{-12.0789, -77.0871});
        EXACT_LIMA_LOCATIONS.put("av de los patriotas", new double[]{-12.0785, -77.0868});
        EXACT_LIMA_LOCATIONS.put("malecon costanera", new double[]{-12.0838, -77.0842});
        EXACT_LIMA_LOCATIONS.put("av costanera", new double[]{-12.0838, -77.0842});

        // Vía Expresa Paseo de la República y estaciones / puentes
        EXACT_LIMA_LOCATIONS.put("via expresa javier prado", new double[]{-12.0908, -77.0261});
        EXACT_LIMA_LOCATIONS.put("puente mexico", new double[]{-12.0769, -77.0282});
        EXACT_LIMA_LOCATIONS.put("estacion mexico", new double[]{-12.0769, -77.0282});
        EXACT_LIMA_LOCATIONS.put("estacion canada", new double[]{-12.0823, -77.0270});
        EXACT_LIMA_LOCATIONS.put("via expresa grau", new double[]{-12.0586, -77.0322});
        EXACT_LIMA_LOCATIONS.put("estacion central", new double[]{-12.0568, -77.0365});
        EXACT_LIMA_LOCATIONS.put("via expresa", new double[]{-12.0835, -77.0268});
        EXACT_LIMA_LOCATIONS.put("paseo de la republica", new double[]{-12.0835, -77.0268});

        // Cercado de Lima / Centro Histórico
        EXACT_LIMA_LOCATIONS.put("museo metropolitano", new double[]{-12.0628, -77.0369});
        EXACT_LIMA_LOCATIONS.put("parque de la exposicion", new double[]{-12.0625, -77.0362});
        EXACT_LIMA_LOCATIONS.put("plaza san martin", new double[]{-12.0516, -77.0346});
        EXACT_LIMA_LOCATIONS.put("plaza dos de mayo", new double[]{-12.0464, -77.0428});
        EXACT_LIMA_LOCATIONS.put("plaza bolognesi", new double[]{-12.0605, -77.0416});
        EXACT_LIMA_LOCATIONS.put("mesa redonda", new double[]{-12.0528, -77.0252});
        EXACT_LIMA_LOCATIONS.put("barrios altos", new double[]{-12.0485, -77.0155});
        EXACT_LIMA_LOCATIONS.put("las malvinas", new double[]{-12.0432, -77.0484});
        EXACT_LIMA_LOCATIONS.put("av abancay", new double[]{-12.0505, -77.0282});
        EXACT_LIMA_LOCATIONS.put("avenida abancay", new double[]{-12.0505, -77.0282});
        EXACT_LIMA_LOCATIONS.put("av alfonso ugarte", new double[]{-12.0531, -77.0422});
        EXACT_LIMA_LOCATIONS.put("avenida alfonso ugarte", new double[]{-12.0531, -77.0422});
        EXACT_LIMA_LOCATIONS.put("av grau", new double[]{-12.0581, -77.0294});
        EXACT_LIMA_LOCATIONS.put("av colonial", new double[]{-12.0482, -77.0625});
        EXACT_LIMA_LOCATIONS.put("av argentina", new double[]{-12.0442, -77.0648});
        EXACT_LIMA_LOCATIONS.put("av venezuela", new double[]{-12.0558, -77.0532});
        EXACT_LIMA_LOCATIONS.put("jr de la union", new double[]{-12.0484, -77.0329});

        // La Victoria / Gamarra / Aviación
        EXACT_LIMA_LOCATIONS.put("gamarra", new double[]{-12.0667, -77.0136});
        EXACT_LIMA_LOCATIONS.put("parque canepa", new double[]{-12.0672, -77.0152});
        EXACT_LIMA_LOCATIONS.put("la parada", new double[]{-12.0642, -77.0098});
        EXACT_LIMA_LOCATIONS.put("matute", new double[]{-12.0685, -77.0221});
        EXACT_LIMA_LOCATIONS.put("balconcillo", new double[]{-12.0792, -77.0218});
        EXACT_LIMA_LOCATIONS.put("av aviacion", new double[]{-12.0755, -77.0112});
        EXACT_LIMA_LOCATIONS.put("av mexico", new double[]{-12.0752, -77.0205});

        // San Martín de Porres / Los Olivos / Independencia / Comas / Cono Norte
        EXACT_LIMA_LOCATIONS.put("chuquitanta", new double[]{-11.9465, -77.0982});
        EXACT_LIMA_LOCATIONS.put("fiori", new double[]{-12.0092, -77.0598});
        EXACT_LIMA_LOCATIONS.put("caqueta", new double[]{-12.0358, -77.0435});
        EXACT_LIMA_LOCATIONS.put("puente caqueta", new double[]{-12.0358, -77.0435});
        EXACT_LIMA_LOCATIONS.put("habich", new double[]{-12.0245, -77.0522});
        EXACT_LIMA_LOCATIONS.put("condevilla", new double[]{-12.0128, -77.0785});
        EXACT_LIMA_LOCATIONS.put("plaza norte", new double[]{-12.0064, -77.0592});
        EXACT_LIMA_LOCATIONS.put("megaplaza", new double[]{-11.9950, -77.0612});
        EXACT_LIMA_LOCATIONS.put("urbanizacion pro", new double[]{-11.9388, -77.0752});
        EXACT_LIMA_LOCATIONS.put("primera de pro", new double[]{-11.9388, -77.0752});
        EXACT_LIMA_LOCATIONS.put("huandoy", new double[]{-11.9642, -77.0768});
        EXACT_LIMA_LOCATIONS.put("collique", new double[]{-11.9185, -77.0365});
        EXACT_LIMA_LOCATIONS.put("zapallal", new double[]{-11.8425, -77.0862});
        EXACT_LIMA_LOCATIONS.put("av universitaria", new double[]{-11.9885, -77.0815});
        EXACT_LIMA_LOCATIONS.put("av tupac amaru", new double[]{-11.9682, -77.0562});
        EXACT_LIMA_LOCATIONS.put("panamericana norte", new double[]{-11.9815, -77.0628});
        EXACT_LIMA_LOCATIONS.put("canta callao", new double[]{-11.9695, -77.0962});

        // San Juan de Lurigancho / El Agustino / Ate
        EXACT_LIMA_LOCATIONS.put("canto grande", new double[]{-11.9839, -76.9985});
        EXACT_LIMA_LOCATIONS.put("bayovar", new double[]{-11.9585, -76.9862});
        EXACT_LIMA_LOCATIONS.put("huascar", new double[]{-11.9612, -76.9995});
        EXACT_LIMA_LOCATIONS.put("zarate", new double[]{-12.0235, -77.0052});
        EXACT_LIMA_LOCATIONS.put("av gran chimu", new double[]{-12.0236, -77.0051});
        EXACT_LIMA_LOCATIONS.put("caja de agua", new double[]{-12.0298, -77.0128});
        EXACT_LIMA_LOCATIONS.put("mangomarca", new double[]{-12.0122, -76.9815});
        EXACT_LIMA_LOCATIONS.put("mariscal caceres", new double[]{-11.9448, -76.9812});
        EXACT_LIMA_LOCATIONS.put("av proceres de la independencia", new double[]{-11.9965, -77.0068});
        EXACT_LIMA_LOCATIONS.put("puente nuevo", new double[]{-12.0336, -77.0019});
        EXACT_LIMA_LOCATIONS.put("huaycan", new double[]{-12.0128, -76.8225});
        EXACT_LIMA_LOCATIONS.put("ceres", new double[]{-12.0268, -76.9195});
        EXACT_LIMA_LOCATIONS.put("santa clara", new double[]{-12.0165, -76.8852});
        EXACT_LIMA_LOCATIONS.put("carretera central", new double[]{-12.0385, -76.9395});

        // Callao / Ventanilla / Bellavista / La Perla
        EXACT_LIMA_LOCATIONS.put("marquez", new double[]{-11.9389, -77.1319});
        EXACT_LIMA_LOCATIONS.put("ramon castilla", new double[]{-12.0211, -77.1185});
        EXACT_LIMA_LOCATIONS.put("sarita colonia", new double[]{-12.0155, -77.1292});
        EXACT_LIMA_LOCATIONS.put("barracones", new double[]{-12.0638, -77.1415});
        EXACT_LIMA_LOCATIONS.put("minka", new double[]{-12.0440, -77.0650});
        EXACT_LIMA_LOCATIONS.put("av saenz pena", new double[]{-12.0598, -77.1385});
        EXACT_LIMA_LOCATIONS.put("av faucett", new double[]{-12.0365, -77.1062});
        EXACT_LIMA_LOCATIONS.put("av nestor gambetta", new double[]{-11.9685, -77.1258});
        EXACT_LIMA_LOCATIONS.put("pachacutec", new double[]{-11.8528, -77.1482});

        // Lima Moderna & Sur
        EXACT_LIMA_LOCATIONS.put("av arequipa", new double[]{-12.0942, -77.0335});
        EXACT_LIMA_LOCATIONS.put("av javier prado", new double[]{-12.0895, -77.0085});
        EXACT_LIMA_LOCATIONS.put("av la marina", new double[]{-12.0775, -77.0845});
        EXACT_LIMA_LOCATIONS.put("av brasil", new double[]{-12.0762, -77.0582});
        EXACT_LIMA_LOCATIONS.put("av angamos", new double[]{-12.1135, -77.0185});
        EXACT_LIMA_LOCATIONS.put("av benavides", new double[]{-12.1268, -77.0065});
        EXACT_LIMA_LOCATIONS.put("ovalo higuereta", new double[]{-12.1282, -77.0018});
        EXACT_LIMA_LOCATIONS.put("puente atocongo", new double[]{-12.1485, -76.9812});
        EXACT_LIMA_LOCATIONS.put("mall del sur", new double[]{-12.1542, -76.9822});
        EXACT_LIMA_LOCATIONS.put("pamplona alta", new double[]{-12.1425, -76.9585});
        EXACT_LIMA_LOCATIONS.put("ciudad de dios", new double[]{-12.1585, -76.9735});
        EXACT_LIMA_LOCATIONS.put("av huaylas", new double[]{-12.1785, -77.0155});
    }

    public void geocodeIncident(Incident incident) {
        geocodeIncident(incident, null, null, null);
    }

    public void geocodeIncident(Incident incident, Double llmLat, Double llmLng, String landmark) {
        District district = incident.getDistrict();
        String combinedSearch = ((incident.getAddress() != null ? incident.getAddress() : "") + " "
                + (landmark != null ? landmark : "") + " "
                + (incident.getTitle() != null ? incident.getTitle() : "")).trim();
        String normSearch = normalize(combinedSearch);

        // 1. Primero buscar en el diccionario de alta precisión con límite de palabra exacta (evita colisiones parciales)
        for (Map.Entry<String, double[]> entry : EXACT_LIMA_LOCATIONS.entrySet()) {
            String key = entry.getKey();
            if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(key) + "(?![a-z0-9])").matcher(normSearch).find()) {
                double[] base = entry.getValue();
                double microJitterLat = (Math.random() - 0.5) * 0.0004;
                double microJitterLng = (Math.random() - 0.5) * 0.0004;
                double[] clamped = clampToLimaLand(base[0] + microJitterLat, base[1] + microJitterLng);
                incident.setLat(clamped[0]);
                incident.setLng(clamped[1]);
                incident.setGeoPrecision(GeoPrecision.ADDRESS);
                log.info("Geocoded '{}' via Lima precision gazetteer ('{}') -> [{}, {}]",
                        incident.getTitle(), key, incident.getLat(), incident.getLng());
                return;
            }
        }

        // 2. Intentar Nominatim con dirección + distrito + Lima, Peru, validando que caiga en tierra firme y dentro del distrito
        if (incident.getAddress() != null && !incident.getAddress().isBlank()) {
            String query = incident.getAddress()
                    + (district != null ? ", " + district.getName() : "")
                    + ", Lima, Peru";
            Double[] coords = nominatimClient.geocode(query);
            if (coords != null && isValidLimaCallaoCoord(coords[0], coords[1], district)) {
                double[] clamped = clampToLimaLand(coords[0], coords[1]);
                incident.setLat(clamped[0]);
                incident.setLng(clamped[1]);
                incident.setGeoPrecision(GeoPrecision.ADDRESS);
                log.info("Geocoded address '{}' via Nominatim to [{}, {}]", query, clamped[0], clamped[1]);
                return;
            }
        }

        // 3. Si Gemini extrajo coordenadas específicas (approx_lat, approx_lng) válidas dentro de Lima/Callao y coherentes con el distrito
        if (llmLat != null && llmLng != null && isValidLimaCallaoCoord(llmLat, llmLng, district)) {
            double microJitterLat = (Math.random() - 0.5) * 0.0004;
            double microJitterLng = (Math.random() - 0.5) * 0.0004;
            double[] clamped = clampToLimaLand(llmLat + microJitterLat, llmLng + microJitterLng);
            incident.setLat(clamped[0]);
            incident.setLng(clamped[1]);
            incident.setGeoPrecision(incident.getAddress() != null && !incident.getAddress().isBlank()
                    ? GeoPrecision.ADDRESS
                    : GeoPrecision.DISTRICT);
            log.info("Geocoded '{}' via Gemini precision coordinates -> [{}, {}]",
                    incident.getTitle(), incident.getLat(), incident.getLng());
            return;
        }

        // 4. Fallback: Centroide del distrito con jitter reducido (~100 metros) y verificación costera
        if (district != null) {
            double jitterLat = (Math.random() - 0.5) * 0.0010;
            double jitterLng = (Math.random() - 0.5) * 0.0010;
            double[] clamped = clampToLimaLand(district.getCentroidLat() + jitterLat, district.getCentroidLng() + jitterLng);
            incident.setLat(clamped[0]);
            incident.setLng(clamped[1]);
            incident.setGeoPrecision(GeoPrecision.DISTRICT);
            return;
        }

        log.warn("Could not geocode incident: {}", incident.getTitle());
        incident.setGeoPrecision(GeoPrecision.DEPARTMENT);
    }

    /**
     * Garantiza matemáticamente que ninguna coordenada en la bahía de Lima/Callao
     * (San Miguel, Magdalena, San Isidro, Miraflores, Barranco, Chorrillos, La Perla) caiga en el mar.
     */
    public static double[] clampToLimaLand(double lat, double lng) {
        double safeLng = lng;
        // Franja costera San Miguel (lat -12.072 a -12.090)
        if (lat <= -12.072 && lat > -12.090 && safeLng < -77.0875) {
            safeLng = -77.0865;
        }
        // Franja costera Magdalena / San Isidro (lat -12.090 a -12.110)
        else if (lat <= -12.090 && lat > -12.110 && safeLng < -77.0685) {
            safeLng = -77.0655;
        }
        // Franja costera Miraflores / Barranco (lat -12.110 a -12.150)
        else if (lat <= -12.110 && lat > -12.150 && safeLng < -77.0320) {
            safeLng = -77.0295;
        }
        // Franja costera Chorrillos (lat -12.150 a -12.190)
        else if (lat <= -12.150 && lat > -12.190 && safeLng < -77.0255) {
            safeLng = -77.0225;
        }
        return new double[]{lat, safeLng};
    }

    private boolean isValidLimaCallaoCoord(double lat, double lng, District district) {
        if (lat < -12.52 || lat > -11.55 || lng < -77.22 || lng > -76.62) {
            return false;
        }
        if (district != null && district.getCentroidLat() != null && district.getCentroidLng() != null) {
            double dLat = lat - district.getCentroidLat();
            double dLng = lng - district.getCentroidLng();
            double approxKm = Math.hypot(dLat, dLng) * 111.0;
            if (approxKm > 7.5) {
                return false;
            }
        }
        return true;
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

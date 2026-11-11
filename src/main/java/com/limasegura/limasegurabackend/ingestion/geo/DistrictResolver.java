package com.limasegura.limasegurabackend.ingestion.geo;

import com.limasegura.limasegurabackend.model.District;
import com.limasegura.limasegurabackend.repository.DistrictRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class DistrictResolver {

    private final DistrictRepository districtRepository;

    private static final Map<String, String> ALIAS_TO_CANONICAL = new LinkedHashMap<>();

    static {
        ALIAS_TO_CANONICAL.put("cercado de lima", "Lima");
        ALIAS_TO_CANONICAL.put("centro de lima", "Lima");
        ALIAS_TO_CANONICAL.put("lima cercado", "Lima");
        ALIAS_TO_CANONICAL.put("damero de pizarro", "Lima");
        ALIAS_TO_CANONICAL.put("plaza san martin", "Lima");
        ALIAS_TO_CANONICAL.put("plaza dos de mayo", "Lima");
        ALIAS_TO_CANONICAL.put("av abancay", "Lima");
        ALIAS_TO_CANONICAL.put("avenida abancay", "Lima");
        ALIAS_TO_CANONICAL.put("barrios altos", "Lima");
        ALIAS_TO_CANONICAL.put("mesa redonda", "Lima");
        ALIAS_TO_CANONICAL.put("las malvinas", "Lima");
        ALIAS_TO_CANONICAL.put("injertos del centro", "Lima");

        ALIAS_TO_CANONICAL.put("via expresa", "La Victoria");
        ALIAS_TO_CANONICAL.put("paseo de la republica", "La Victoria");
        ALIAS_TO_CANONICAL.put("gamarra", "La Victoria");
        ALIAS_TO_CANONICAL.put("balconcillo", "La Victoria");
        ALIAS_TO_CANONICAL.put("matute", "La Victoria");
        ALIAS_TO_CANONICAL.put("la parada", "La Victoria");

        ALIAS_TO_CANONICAL.put("costa verde", "Miraflores");
        ALIAS_TO_CANONICAL.put("circuito de playas", "Miraflores");
        ALIAS_TO_CANONICAL.put("bajada armendariz", "Miraflores");
        ALIAS_TO_CANONICAL.put("bajada balta", "Miraflores");

        ALIAS_TO_CANONICAL.put("chuquitanta", "San Martin de Porres");
        ALIAS_TO_CANONICAL.put("fiori", "San Martin de Porres");
        ALIAS_TO_CANONICAL.put("caqueta", "San Martin de Porres");
        ALIAS_TO_CANONICAL.put("habich", "San Martin de Porres");
        ALIAS_TO_CANONICAL.put("condevilla", "San Martin de Porres");
        ALIAS_TO_CANONICAL.put("smp", "San Martin de Porres");

        ALIAS_TO_CANONICAL.put("canto grande", "San Juan de Lurigancho");
        ALIAS_TO_CANONICAL.put("zarate", "San Juan de Lurigancho");
        ALIAS_TO_CANONICAL.put("bayovar", "San Juan de Lurigancho");
        ALIAS_TO_CANONICAL.put("huascar", "San Juan de Lurigancho");
        ALIAS_TO_CANONICAL.put("mangomarca", "San Juan de Lurigancho");
        ALIAS_TO_CANONICAL.put("caja de agua", "San Juan de Lurigancho");
        ALIAS_TO_CANONICAL.put("mariscal caceres", "San Juan de Lurigancho");
        ALIAS_TO_CANONICAL.put("sjl", "San Juan de Lurigancho");

        ALIAS_TO_CANONICAL.put("ate vitarte", "Ate");
        ALIAS_TO_CANONICAL.put("vitarte", "Ate");
        ALIAS_TO_CANONICAL.put("huaycan", "Ate");
        ALIAS_TO_CANONICAL.put("santa clara", "Ate");
        ALIAS_TO_CANONICAL.put("ceres", "Ate");

        ALIAS_TO_CANONICAL.put("surco", "Santiago de Surco");
        ALIAS_TO_CANONICAL.put("monterrico", "Santiago de Surco");

        ALIAS_TO_CANONICAL.put("chosica", "Lurigancho");
        ALIAS_TO_CANONICAL.put("lurigancho chosica", "Lurigancho");
        ALIAS_TO_CANONICAL.put("huachipa", "Lurigancho");

        ALIAS_TO_CANONICAL.put("pamplona alta", "San Juan de Miraflores");
        ALIAS_TO_CANONICAL.put("pamplona baja", "San Juan de Miraflores");
        ALIAS_TO_CANONICAL.put("ciudad de dios", "San Juan de Miraflores");
        ALIAS_TO_CANONICAL.put("sjm", "San Juan de Miraflores");

        ALIAS_TO_CANONICAL.put("ves", "Villa El Salvador");
        ALIAS_TO_CANONICAL.put("vmt", "Villa Maria del Triunfo");
        ALIAS_TO_CANONICAL.put("magdalena", "Magdalena del Mar");
        ALIAS_TO_CANONICAL.put("carmen de la legua", "Carmen de la Legua Reynoso");
        ALIAS_TO_CANONICAL.put("collique", "Comas");
        ALIAS_TO_CANONICAL.put("trapiche", "Comas");
        ALIAS_TO_CANONICAL.put("zapallal", "Puente Piedra");
        ALIAS_TO_CANONICAL.put("huandoy", "Los Olivos");
        ALIAS_TO_CANONICAL.put("urbanizacion pro", "Los Olivos");
        ALIAS_TO_CANONICAL.put("urb pro", "Los Olivos");
        ALIAS_TO_CANONICAL.put("primera de pro", "Los Olivos");
        ALIAS_TO_CANONICAL.put("tahihuantisuyo", "Independencia");
        ALIAS_TO_CANONICAL.put("tahuantinsuyo", "Independencia");
        ALIAS_TO_CANONICAL.put("ermitano", "Independencia");
        ALIAS_TO_CANONICAL.put("bocana", "Callao");
        ALIAS_TO_CANONICAL.put("sarita colonia", "Callao");
        ALIAS_TO_CANONICAL.put("barracones", "Callao");
        ALIAS_TO_CANONICAL.put("marquez", "Callao");
        ALIAS_TO_CANONICAL.put("minka", "Callao");
        ALIAS_TO_CANONICAL.put("pachacutec", "Ventanilla");
    }

    public District resolveFromText(String text) {
        if (text == null || text.isBlank()) return null;
        String normText = normalize(text);
        List<District> candidates = districtRepository.findAll();

        // 1. Buscar nombres exactos de distritos (ordenados por longitud descendente para priorizar "San Juan de Lurigancho" sobre "Lurigancho" o "Lima")
        candidates.sort((a, b) -> Integer.compare(b.getName().length(), a.getName().length()));
        for (District d : candidates) {
            if ("Lima".equalsIgnoreCase(d.getName())) {
                continue; // Evitar que cualquier mención genérica a "Lima" se asigne antes de revisar barrios/aliases
            }
            String normDistrict = normalize(d.getName());
            if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(normDistrict) + "(?![a-z0-9])").matcher(normText).find()) {
                return d;
            }
        }

        // 2. Buscar por alias de zonas/barrios/avenidas emblemáticas de Lima y Callao
        for (Map.Entry<String, String> entry : ALIAS_TO_CANONICAL.entrySet()) {
            String alias = entry.getKey();
            if (Pattern.compile("(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])").matcher(normText).find()) {
                District matched = findByNormalizedName(candidates, entry.getValue());
                if (matched != null) {
                    return matched;
                }
            }
        }

        return null;
    }

    public District resolve(String districtName, String provinceName, String departmentName) {
        if (districtName == null || districtName.isBlank()) return null;

        String normInput = normalize(districtName);
        List<District> candidates = districtRepository.findAll();

        // 1. Coincidencia directa ignorando tildes/mayúsculas
        District direct = findByNormalizedName(candidates, normInput);
        if (direct != null) {
            return direct;
        }

        // 2. Coincidencia por alias conocido (ej. "Cercado de Lima", "Surco", "Ate Vitarte")
        String canonical = ALIAS_TO_CANONICAL.get(normInput);
        if (canonical != null) {
            District aliased = findByNormalizedName(candidates, canonical);
            if (aliased != null) {
                return aliased;
            }
        }

        // 3. Búsqueda de texto libre dentro del campo districtName
        return resolveFromText(districtName);
    }

    private District findByNormalizedName(List<District> candidates, String nameOrNormalized) {
        String target = normalize(nameOrNormalized);
        for (District d : candidates) {
            if (normalize(d.getName()).equals(target)) {
                return d;
            }
        }
        return null;
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

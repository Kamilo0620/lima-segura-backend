package com.limasegura.limasegurabackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "ingestion")
@Getter
@Setter
public class IngestionConfig {
    private String pollInterval = "PT5M";
    private int maxAttempts = 5;
    private double minConfidence = 0.75;
    private List<String> relevanceKeywords = List.of(
        "robo", "asalto", "extorsion", "sicariato", "balacera",
        "homicidio", "secuestro", "estafa", "feminicidio",
        "asesinato", "hurto", "atraco", "crimen", "delito",
        "delincuencia", "violacion", "abuso", "vandalismo",
        "narcotráfico", "drogas", "pandillaje", "inseguridad",
        "arma", "bala", "apuñalado", "muerto", "herido",
        "tiroteo", "asaltante", "ladrón", "criminal",
        "policía", "PNP", "comisaría", "denuncia"
    );
}

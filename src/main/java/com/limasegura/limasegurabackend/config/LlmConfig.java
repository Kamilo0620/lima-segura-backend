package com.limasegura.limasegurabackend.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@ConfigurationProperties(prefix = "llm")
@Getter
@Setter
@ToString(exclude = {"apiKey", "apiKeys"})
public class LlmConfig {
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai";
    private String apiKey;
    private String apiKeys;
    private String model = "gemini-3.8-flash";
    /** Modelos de respaldo (opcional): solo se prueban, en orden, si el anterior falla. */
    private String models;
    private double temperature = 0;
    private int maxTokens = 4096;

    /**
     * Usamos UNA sola API key. Rotar claves de varias cuentas para esquivar los límites
     * del plan gratuito puede ir contra los términos del proveedor y hacer que bloqueen las cuentas.
     * Por compatibilidad, si solo está LLM_API_KEYS, se toma la primera.
     */
    public List<String> getEffectiveApiKeys() {
        if (apiKey != null && !apiKey.isBlank()) {
            return List.of(apiKey.trim());
        }
        if (apiKeys != null && !apiKeys.isBlank()) {
            return Arrays.stream(apiKeys.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .limit(1)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    /** Modelo principal primero; después los de respaldo, sin repetir. */
    public List<String> getEffectiveModels() {
        List<String> result = new ArrayList<>();
        if (model != null && !model.isBlank()) {
            result.add(model.trim());
        }
        if (models != null && !models.isBlank()) {
            result.addAll(Arrays.stream(models.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty() && !result.contains(s))
                    .collect(Collectors.toList()));
        }
        return result.isEmpty() ? List.of("gemini-3.8-flash") : result;
    }
}

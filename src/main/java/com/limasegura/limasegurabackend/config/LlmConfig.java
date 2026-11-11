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
    private String model = "gemini-3.5-flash";
    private String models = "gemini-3.5-flash,gemini-3.6-flash,gemini-3.7-flash";
    private double temperature = 0;
    private int maxTokens = 4096;

    public List<String> getEffectiveApiKeys() {
        List<String> result = new ArrayList<>();
        if (apiKeys != null && !apiKeys.isBlank()) {
            result.addAll(Arrays.stream(apiKeys.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        }
        if (apiKey != null && !apiKey.isBlank() && !result.contains(apiKey.trim())) {
            result.add(apiKey.trim());
        }
        return result;
    }

    public List<String> getEffectiveModels() {
        List<String> result = new ArrayList<>();
        if (models != null && !models.isBlank()) {
            result.addAll(Arrays.stream(models.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList()));
        }
        if (result.isEmpty() && model != null && !model.isBlank()) {
            result.add(model.trim());
        }
        return result.isEmpty() ? List.of("gemini-3.5-flash") : result;
    }
}

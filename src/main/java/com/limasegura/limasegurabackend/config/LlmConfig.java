package com.limasegura.limasegurabackend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "llm")
@Getter
@Setter
public class LlmConfig {
    private String baseUrl = "https://api.groq.com/openai/v1";
    private String apiKey;
    private String model = "llama-3.1-70b-versatile";
    private double temperature = 0;
    private int maxTokens = 4096;
}

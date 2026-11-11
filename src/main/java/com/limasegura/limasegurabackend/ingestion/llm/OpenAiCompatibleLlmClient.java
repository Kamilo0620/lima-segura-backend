package com.limasegura.limasegurabackend.ingestion.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.limasegura.limasegurabackend.config.LlmConfig;
import com.limasegura.limasegurabackend.exception.LlmExtractionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = createRestClient();

    private static RestClient createRestClient() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(25));
        return RestClient.builder().requestFactory(factory).build();
    }

    private final AtomicInteger keyCursor = new AtomicInteger(0);
    private final AtomicInteger modelCursor = new AtomicInteger(0);
    private final ThreadLocal<String> lastUsedModel = new ThreadLocal<>();

    @Override
    public String getLastUsedModel() {
        String used = lastUsedModel.get();
        return used != null ? used : llmConfig.getModel();
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        List<String> keys = llmConfig.getEffectiveApiKeys();
        List<String> models = llmConfig.getEffectiveModels();

        if (keys.isEmpty()) {
            throw new LlmExtractionException("No LLM API keys configured in environment (.env)");
        }

        int totalKeys = keys.size();
        int totalModels = models.size();
        int maxAttempts = Math.max(totalKeys * totalModels, 1);

        int startKeyIdx = Math.floorMod(keyCursor.getAndIncrement(), totalKeys);
        int startModelIdx = Math.floorMod(modelCursor.getAndIncrement(), totalModels);

        Exception lastError = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int keyIdx = (startKeyIdx + attempt) % totalKeys;
            int modelIdx = (startModelIdx + attempt) % totalModels;

            String currentKey = keys.get(keyIdx);
            String currentModel = models.get(modelIdx);
            String maskedKeyId = "Key#" + (keyIdx + 1);

            try {
                ObjectNode requestBody = objectMapper.createObjectNode();
                requestBody.put("model", currentModel);
                requestBody.put("temperature", llmConfig.getTemperature());
                requestBody.put("max_tokens", llmConfig.getMaxTokens() > 0 ? llmConfig.getMaxTokens() : 2000);

                ArrayNode messages = requestBody.putArray("messages");

                ObjectNode systemNode = messages.addObject();
                systemNode.put("role", "system");
                systemNode.put("content", systemPrompt);

                ObjectNode userNode = messages.addObject();
                userNode.put("role", "user");
                userNode.put("content", userMessage);

                String responseStr = restClient.post()
                        .uri(llmConfig.getBaseUrl() + "/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + currentKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody.toString())
                        .retrieve()
                        .body(String.class);

                JsonNode responseJson = objectMapper.readTree(responseStr);
                String content = responseJson.path("choices").path(0).path("message").path("content").asText();
                if (content == null || content.isBlank()) {
                    throw new LlmExtractionException("Empty content returned by model " + currentModel);
                }

                lastUsedModel.set(currentModel);
                log.debug("LLM extraction succeeded using {} with model {}", maskedKeyId, currentModel);
                return content;
            } catch (Exception e) {
                lastError = e;
                String safeMsg = redactSecrets(e.getMessage(), keys);
                log.warn("LLM call attempt {}/{} failed on {} with model {}: {}",
                        attempt + 1, maxAttempts, maskedKeyId, currentModel, safeMsg);
            }
        }

        String safeError = lastError != null ? redactSecrets(lastError.getMessage(), keys) : "Unknown LLM error";
        log.error("All {} LLM key/model combinations failed. Last error: {}", maxAttempts, safeError);
        throw new LlmExtractionException("Error calling LLM API across pool: " + safeError);
    }

    private String redactSecrets(String input, List<String> secrets) {
        if (input == null) return "null";
        String sanitized = input;
        for (String secret : secrets) {
            if (secret != null && secret.length() > 6) {
                sanitized = sanitized.replace(secret, "[REDACTED_API_KEY]");
            }
        }
        return sanitized;
    }
}

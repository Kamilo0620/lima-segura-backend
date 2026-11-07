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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatibleLlmClient implements LlmClient {

    private final LlmConfig llmConfig;
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Override
    public String chat(String systemPrompt, String userMessage) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", llmConfig.getModel());
            requestBody.put("temperature", llmConfig.getTemperature());
            requestBody.put("max_tokens", 2000);

            ArrayNode messages = requestBody.putArray("messages");
            
            ObjectNode systemNode = messages.addObject();
            systemNode.put("role", "system");
            systemNode.put("content", systemPrompt);

            ObjectNode userNode = messages.addObject();
            userNode.put("role", "user");
            userNode.put("content", userMessage);

            String responseStr = restClient.post()
                    .uri(llmConfig.getBaseUrl() + "/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + llmConfig.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode responseJson = objectMapper.readTree(responseStr);
            return responseJson.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            log.error("Error communicating with LLM", e);
            throw new LlmExtractionException("Error calling LLM API: " + e.getMessage());
        }
    }
}

package com.limasegura.limasegurabackend.ingestion.llm;

public interface LlmClient {
    String chat(String systemPrompt, String userMessage);
}

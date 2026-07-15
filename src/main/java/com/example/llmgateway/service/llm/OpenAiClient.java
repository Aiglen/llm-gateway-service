package com.example.llmgateway.service.llm;

import com.example.llmgateway.config.ModelProperties;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OpenAI 官方 /v1/chat/completions 协议客户端。
 */
public class OpenAiClient extends AbstractOpenAiCompatibleClient {

    public OpenAiClient(WebClient.Builder builder, ModelProperties.Provider providerConfig) {
        super(builder, providerConfig);
    }

    @Override
    public String providerName() {
        return "openai";
    }
}

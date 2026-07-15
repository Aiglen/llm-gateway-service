package com.example.llmgateway.service.llm;

import com.example.llmgateway.config.ModelProperties;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 本地/私有化部署模型客户端，适用于本身就实现了 OpenAI 兼容 /v1/chat/completions 协议的
 * 本地推理服务：vLLM（--served-model-name + OpenAI 兼容 API）、LM Studio、Xinference、
 * text-generation-webui（开启 openai 扩展）等。
 * <p>
 * 因为协议本身就是 OpenAI 兼容的，直接复用 {@link AbstractOpenAiCompatibleClient} 即可，
 * 通常不需要配置 apiKey（本地服务默认不鉴权）。
 */
public class LocalOpenAiCompatibleClient extends AbstractOpenAiCompatibleClient {

    public LocalOpenAiCompatibleClient(WebClient.Builder builder, ModelProperties.Provider providerConfig) {
        super(builder, providerConfig);
    }

    @Override
    public String providerName() {
        return "local";
    }
}

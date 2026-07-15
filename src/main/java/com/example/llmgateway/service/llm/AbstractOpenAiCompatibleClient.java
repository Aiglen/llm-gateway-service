package com.example.llmgateway.service.llm;

import com.example.llmgateway.config.ModelProperties;
import com.example.llmgateway.dto.ChatMessage;
import com.example.llmgateway.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 很多厂商（OpenAI 官方、通义千问 DashScope 兼容模式、以及大量国产模型网关）
 * 都实现了与 OpenAI /v1/chat/completions 基本一致的协议，因此抽出公共基类，
 * 避免每个 provider 重复实现 HTTP 调用、SSE 解析、token 统计等逻辑。
 */
@Slf4j
public abstract class AbstractOpenAiCompatibleClient implements LlmClient {

    protected final WebClient webClient;
    protected final ModelProperties.Provider providerConfig;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected AbstractOpenAiCompatibleClient(WebClient.Builder builder, ModelProperties.Provider providerConfig) {
        this.providerConfig = providerConfig;
        WebClient.Builder configured = builder.baseUrl(providerConfig.getBaseUrl());
        // 本地模型服务（vLLM / LM Studio / Xinference 等）通常不需要鉴权，
        // 只有配置了 apiKey 时才附加 Authorization 头，避免发送 "Bearer "（空值）这种无意义请求头。
        if (providerConfig.getApiKey() != null && !providerConfig.getApiKey().isBlank()) {
            configured = configured.defaultHeader("Authorization", "Bearer " + providerConfig.getApiKey());
        }
        this.webClient = configured.build();
    }

    @Override
    public boolean supports(String model) {
        return providerConfig.getModels() != null && providerConfig.getModels().contains(model);
    }

    @Override
    public Mono<ChatResponse> chat(String model, List<ChatMessage> messages, double temperature, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", toOpenAiMessages(messages),
                "temperature", temperature,
                "max_tokens", maxTokens,
                "stream", false
        );

        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(providerConfig.getTimeoutMs()))
                .map(json -> parseChatResponse(model, json));
    }

    @Override
    public Flux<String> chatStream(String model, List<ChatMessage> messages, double temperature, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", toOpenAiMessages(messages),
                "temperature", temperature,
                "max_tokens", maxTokens,
                "stream", true
        );

        return webClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofMillis(providerConfig.getTimeoutMs()))
                .filter(line -> !line.isBlank() && !"[DONE]".equals(line.trim()))
                .mapNotNull(this::extractDeltaContent);
    }

    /** 将统一的 ChatMessage 转成 OpenAI 协议要求的 messages 数组 */
    private List<Map<String, Object>> toOpenAiMessages(List<ChatMessage> messages) {
        return messages.stream().map(m -> {
            if ("function".equals(m.getRole())) {
                return Map.<String, Object>of("role", "function", "name", m.getName(), "content", m.getContent());
            }
            return Map.<String, Object>of("role", m.getRole(), "content", m.getContent());
        }).collect(Collectors.toList());
    }

    private ChatResponse parseChatResponse(String model, JsonNode json) {
        JsonNode choice = json.path("choices").path(0).path("message");
        String content = choice.path("content").asText("");
        JsonNode usage = json.path("usage");
        return ChatResponse.builder()
                .model(model)
                .provider(providerName())
                .content(content)
                .promptTokens(usage.path("prompt_tokens").asInt(0))
                .completionTokens(usage.path("completion_tokens").asInt(0))
                .totalTokens(usage.path("total_tokens").asInt(0))
                .degraded(false)
                .build();
    }

    /** 解析 SSE 增量数据行，提取 delta.content 文本片段 */
    private String extractDeltaContent(String rawLine) {
        try {
            String payload = rawLine.startsWith("data:") ? rawLine.substring(5).trim() : rawLine.trim();
            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                return null;
            }
            JsonNode json = objectMapper.readTree(payload);
            JsonNode delta = json.path("choices").path(0).path("delta");
            String text = delta.path("content").asText("");
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            log.debug("忽略无法解析的流式数据行: {}", rawLine);
            return null;
        }
    }
}

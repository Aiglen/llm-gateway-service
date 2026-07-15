package com.example.llmgateway.service.llm;

import com.example.llmgateway.config.ModelProperties;
import com.example.llmgateway.dto.ChatMessage;
import com.example.llmgateway.dto.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
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
 * Ollama 原生协议客户端（{@code /api/chat}），用于本地跑开源模型（qwen2.5、llama3.1、deepseek-r1 等）。
 * <p>
 * 与 OpenAI 协议的主要差异：
 *  1) 响应结构是 {@code message.content} 而不是 {@code choices[0].message.content}；
 *  2) 流式响应是 NDJSON（每行一个完整 JSON 对象，Content-Type: application/x-ndjson），
 *     不是 SSE 的 "data: {...}" 格式，因此用 {@code bodyToFlux(JsonNode.class)} 直接按行解析；
 *  3) 本地服务默认无需鉴权。
 * <p>
 * 如果本地服务是通过 vLLM / LM Studio 等已经实现了 OpenAI 兼容协议的方式启动的，
 * 应该使用 {@link LocalOpenAiCompatibleClient}（provider type = local）而不是本类。
 */
@Slf4j
public class OllamaClient implements LlmClient {

    private final WebClient webClient;
    private final ModelProperties.Provider providerConfig;

    public OllamaClient(WebClient.Builder builder, ModelProperties.Provider providerConfig) {
        this.providerConfig = providerConfig;
        this.webClient = builder.baseUrl(providerConfig.getBaseUrl()).build();
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    @Override
    public boolean supports(String model) {
        return providerConfig.getModels() != null && providerConfig.getModels().contains(model);
    }

    @Override
    public Mono<ChatResponse> chat(String model, List<ChatMessage> messages, double temperature, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", toOllamaMessages(messages),
                "stream", false,
                "options", Map.of("temperature", temperature, "num_predict", maxTokens)
        );

        return webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(providerConfig.getTimeoutMs()))
                .map(json -> parseResponse(model, json));
    }

    @Override
    public Flux<String> chatStream(String model, List<ChatMessage> messages, double temperature, int maxTokens) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", toOllamaMessages(messages),
                "stream", true,
                "options", Map.of("temperature", temperature, "num_predict", maxTokens)
        );

        return webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(JsonNode.class)
                .timeout(Duration.ofMillis(providerConfig.getTimeoutMs()))
                .mapNotNull(this::extractDeltaContent);
    }

    private String extractDeltaContent(JsonNode json) {
        String text = json.path("message").path("content").asText("");
        return text.isEmpty() ? null : text;
    }

    private ChatResponse parseResponse(String model, JsonNode json) {
        String content = json.path("message").path("content").asText("");
        // Ollama 返回的是 prompt_eval_count（输入 token 数）/ eval_count（输出 token 数），
        // 字段名和 OpenAI 的 usage.prompt_tokens/completion_tokens 不同，这里做统一映射。
        int promptTokens = json.path("prompt_eval_count").asInt(0);
        int completionTokens = json.path("eval_count").asInt(0);
        return ChatResponse.builder()
                .model(model)
                .provider(providerName())
                .content(content)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .degraded(false)
                .build();
    }

    private List<Map<String, Object>> toOllamaMessages(List<ChatMessage> messages) {
        return messages.stream()
                .map(m -> Map.<String, Object>of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());
    }
}

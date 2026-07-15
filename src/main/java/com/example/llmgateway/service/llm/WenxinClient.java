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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 百度文心一言（千帆 ERNIE）客户端。
 * 与 OpenAI 协议的主要差异：
 *  1) 鉴权采用 API Key + Secret Key 换取 access_token（需要缓存与刷新）；
 *  2) 请求/响应字段结构不同（result 而非 choices[0].message.content）；
 *  3) system 消息需要单独放在顶层 "system" 字段，不放入 messages 数组。
 * 这里做了最小可运行的演示实现，重点展示"多模型路由下如何适配协议差异"。
 */
@Slf4j
public class WenxinClient implements LlmClient {

    private final WebClient webClient;
    private final ModelProperties.Provider providerConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** access_token 简单内存缓存，生产环境建议存 Redis 并在过期前主动刷新 */
    private final AtomicReference<String> cachedAccessToken = new AtomicReference<>();

    public WenxinClient(WebClient.Builder builder, ModelProperties.Provider providerConfig) {
        this.providerConfig = providerConfig;
        this.webClient = builder.baseUrl(providerConfig.getBaseUrl()).build();
    }

    @Override
    public String providerName() {
        return "wenxin";
    }

    @Override
    public boolean supports(String model) {
        return providerConfig.getModels() != null && providerConfig.getModels().contains(model);
    }

    @Override
    public Mono<ChatResponse> chat(String model, List<ChatMessage> messages, double temperature, int maxTokens) {
        return resolveAccessToken().flatMap(token -> {
            String system = extractSystemPrompt(messages);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("messages", toWenxinMessages(messages));
            body.put("temperature", temperature);
            if (system != null) {
                body.put("system", system);
            }

            return webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/" + model).queryParam("access_token", token).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .timeout(Duration.ofMillis(providerConfig.getTimeoutMs()))
                    .map(json -> parseResponse(model, json));
        });
    }

    @Override
    public Flux<String> chatStream(String model, List<ChatMessage> messages, double temperature, int maxTokens) {
        return resolveAccessToken().flatMapMany(token -> {
            String system = extractSystemPrompt(messages);
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("messages", toWenxinMessages(messages));
            body.put("temperature", temperature);
            body.put("stream", true);
            if (system != null) {
                body.put("system", system);
            }

            return webClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/" + model).queryParam("access_token", token).build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class)
                    .timeout(Duration.ofMillis(providerConfig.getTimeoutMs()))
                    .mapNotNull(this::extractStreamChunk);
        });
    }

    private String extractStreamChunk(String rawLine) {
        try {
            String payload = rawLine.startsWith("data:") ? rawLine.substring(5).trim() : rawLine.trim();
            if (payload.isEmpty()) return null;
            JsonNode json = objectMapper.readTree(payload);
            String text = json.path("result").asText("");
            return text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }

    private ChatResponse parseResponse(String model, JsonNode json) {
        String content = json.path("result").asText("");
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

    private List<Map<String, Object>> toWenxinMessages(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> !"system".equals(m.getRole()))
                .map(m -> Map.<String, Object>of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());
    }

    private String extractSystemPrompt(List<ChatMessage> messages) {
        return messages.stream()
                .filter(m -> "system".equals(m.getRole()))
                .map(ChatMessage::getContent)
                .findFirst()
                .orElse(null);
    }

    /** 换取/复用 access_token。真实实现应校验过期时间并加锁刷新，这里做简化演示。 */
    private Mono<String> resolveAccessToken() {
        String cached = cachedAccessToken.get();
        if (cached != null) {
            return Mono.just(cached);
        }
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https").host("aip.baidubce.com").path("/oauth/2.0/token")
                        .queryParam("grant_type", "client_credentials")
                        .queryParam("client_id", providerConfig.getApiKey())
                        .queryParam("client_secret", providerConfig.getModels()) // 演示占位，实际应单独配置 secretKey
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    String token = json.path("access_token").asText();
                    cachedAccessToken.set(token);
                    return token;
                })
                .onErrorResume(e -> {
                    log.error("获取文心一言 access_token 失败", e);
                    return Mono.error(new IllegalStateException("文心一言鉴权失败", e));
                });
    }
}

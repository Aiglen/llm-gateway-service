package com.example.llmgateway.service.rag;

import com.example.llmgateway.config.ModelProperties;
import com.example.llmgateway.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 文本向量化服务，调用 embedding 模型接口（默认复用 OpenAI 兼容的
 * /v1/embeddings 协议，通义千问 DashScope 兼容模式同样支持该协议）。
 */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final WebClient.Builder webClientBuilder;
    private final ModelProperties modelProperties;
    private final RagProperties ragProperties;

    public Mono<float[]> embed(String text) {
        ModelProperties.Provider provider = modelProperties.getProviders().get(modelProperties.getDefaultProvider());

        WebClient client = webClientBuilder
                .baseUrl(provider.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + provider.getApiKey())
                .build();

        Map<String, Object> body = Map.of(
                "model", ragProperties.getEmbeddingModel(),
                "input", text
        );

        return client.post()
                .uri("/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::extractVector);
    }

    private float[] extractVector(JsonNode json) {
        JsonNode arr = json.path("data").path(0).path("embedding");
        float[] vector = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            vector[i] = (float) arr.get(i).asDouble();
        }
        return vector;
    }
}

package com.example.llmgateway.service.rag;

import com.example.llmgateway.config.RagProperties;
import com.example.llmgateway.dto.KnowledgeChunk;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Milvus 向量库客户端，使用其 REST v2 API（/v2/vectordb/entities/search、/insert）。
 * 若切换到 Pinecone / Chroma，只需新增对应实现并在 @Bean 中替换即可，
 * 上层 RagService 代码零改动 —— 体现"向量库可插拔"的设计。
 */
@Slf4j
public class MilvusVectorStoreClient implements VectorStoreClient {

    private final WebClient webClient;
    private final RagProperties ragProperties;

    public MilvusVectorStoreClient(WebClient.Builder builder, RagProperties ragProperties) {
        this.ragProperties = ragProperties;
        this.webClient = builder
                .baseUrl("http://" + ragProperties.getHost() + ":" + ragProperties.getPort())
                .build();
    }

    @Override
    public Mono<List<KnowledgeChunk>> similaritySearch(float[] queryVector, int topK) {
        Map<String, Object> body = Map.of(
                "collectionName", ragProperties.getCollectionName(),
                "vector", queryVector,
                "limit", topK,
                "outputFields", List.of("text", "source")
        );

        return webClient.post()
                .uri("/v2/vectordb/entities/search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::parseSearchResult)
                .onErrorResume(e -> {
                    log.error("Milvus 检索失败，返回空结果集，RAG 将退化为纯 LLM 问答", e);
                    return Mono.just(List.of());
                });
    }

    @Override
    public Mono<Void> upsert(List<KnowledgeChunk> chunks, List<float[]> vectors) {
        List<Map<String, Object>> data = IntStream.range(0, chunks.size())
                .mapToObj(i -> {
                    KnowledgeChunk chunk = chunks.get(i);
                    return Map.<String, Object>of(
                            "id", chunk.getId(),
                            "text", chunk.getText(),
                            "source", chunk.getSource() == null ? "" : chunk.getSource(),
                            "vector", vectors.get(i)
                    );
                }).collect(Collectors.toList());

        Map<String, Object> body = Map.of(
                "collectionName", ragProperties.getCollectionName(),
                "data", data
        );

        return webClient.post()
                .uri("/v2/vectordb/entities/insert")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class);
    }

    @Override
    public Mono<Void> deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Mono.empty();
        }
        Map<String, Object> body = Map.of(
                "collectionName", ragProperties.getCollectionName(),
                "id", ids
        );

        return webClient.post()
                .uri("/v2/vectordb/entities/delete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(e -> {
                    log.error("Milvus 删除向量失败: ids={}", ids, e);
                    return Mono.error(new IllegalStateException("删除知识库文档失败", e));
                });
    }

    private List<KnowledgeChunk> parseSearchResult(JsonNode json) {
        List<KnowledgeChunk> result = new ArrayList<>();
        JsonNode dataArray = json.path("data");
        if (dataArray.isArray()) {
            for (JsonNode item : dataArray) {
                double score = item.path("distance").asDouble(0);
                if (score < ragProperties.getScoreThreshold()) {
                    continue;
                }
                result.add(new KnowledgeChunk(
                        item.path("id").asText(),
                        item.path("text").asText(""),
                        score,
                        item.path("source").asText("")
                ));
            }
        }
        return result;
    }

    /** 向量库客户端装配：当前默认使用 Milvus，切换 Pinecone/Chroma 时替换本 Bean 实现即可 */
    @Configuration
    static class Wiring {
        @Bean
        @Primary
        public VectorStoreClient vectorStoreClient(WebClient.Builder builder, RagProperties ragProperties) {
            return new MilvusVectorStoreClient(builder, ragProperties);
        }
    }
}

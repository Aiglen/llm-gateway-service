package com.example.llmgateway.service.agent.tools;

import com.example.llmgateway.dto.KnowledgeChunk;
import com.example.llmgateway.service.agent.FunctionTool;
import com.example.llmgateway.service.rag.EmbeddingService;
import com.example.llmgateway.service.rag.VectorStoreClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 示例工具：让 Agent 可以在多轮推理过程中主动检索知识库，
 * 而不是只能在 /api/rag/query 这种"一问一答"场景里使用 RAG。
 * <p>
 * 注意这里直接复用 EmbeddingService + VectorStoreClient 做纯检索，
 * 不经过 RagService（RagService 内部会再调一次 LLM 生成答案，
 * 而工具函数应该只返回"事实性的原始信息"，交由 Agent 的主模型统一决策如何使用，
 * 避免出现"工具里悄悄调了一次模型"这种不透明的行为）。
 */
@Component
@RequiredArgsConstructor
public class SearchKnowledgeBaseTool implements FunctionTool {

    private final EmbeddingService embeddingService;
    private final VectorStoreClient vectorStoreClient;

    @Override
    public String name() {
        return "search_knowledge_base";
    }

    @Override
    public String description() {
        return "在企业知识库中检索与关键词相关的资料片段，用于回答需要查阅内部文档的问题";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of(
                "query", "string, 检索关键词或问题",
                "topK", "number, 可选，返回片段数量，默认 3"
        );
    }

    @Override
    public Mono<String> execute(Map<String, Object> arguments) {
        String query = String.valueOf(arguments.getOrDefault("query", ""));
        if (query.isBlank()) {
            return Mono.just("错误：缺少 query 参数");
        }
        int topK = parseTopK(arguments.get("topK"));

        return embeddingService.embed(query)
                .flatMap(vector -> vectorStoreClient.similaritySearch(vector, topK))
                .map(this::formatResult);
    }

    private String formatResult(List<KnowledgeChunk> chunks) {
        if (chunks.isEmpty()) {
            return "未在知识库中检索到相关内容";
        }
        return chunks.stream()
                .map(c -> "[来源: %s, 相关度: %.2f] %s".formatted(c.getSource(), c.getScore(), c.getText()))
                .collect(Collectors.joining("\n---\n"));
    }

    private int parseTopK(Object raw) {
        if (raw == null) return 3;
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(raw)));
        } catch (NumberFormatException e) {
            return 3;
        }
    }
}

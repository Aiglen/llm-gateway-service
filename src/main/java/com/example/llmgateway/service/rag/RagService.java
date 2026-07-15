package com.example.llmgateway.service.rag;

import com.example.llmgateway.config.RagProperties;
import com.example.llmgateway.dto.ChatMessage;
import com.example.llmgateway.dto.ChatResponse;
import com.example.llmgateway.dto.KnowledgeChunk;
import com.example.llmgateway.service.llm.LlmRouterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG（检索增强生成）主流程编排：
 * 1) 对用户问题做 embedding；
 * 2) 到向量库检索 top-K 相关知识片段；
 * 3) 将检索结果拼接进 Prompt，约束模型基于知识库内容作答；
 * 4) 通过 LlmRouterService 完成多模型路由调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final EmbeddingService embeddingService;
    private final VectorStoreClient vectorStoreClient;
    private final LlmRouterService llmRouterService;
    private final RagProperties ragProperties;

    public Mono<ChatResponse> answerWithKnowledgeBase(String question, String model, Integer topKOverride) {
        int topK = topKOverride != null ? topKOverride : ragProperties.getTopK();

        return embeddingService.embed(question)
                .flatMap(vector -> vectorStoreClient.similaritySearch(vector, topK))
                .flatMap(chunks -> {
                    List<ChatMessage> messages = buildPrompt(question, chunks);
                    return llmRouterService.chat(model, messages, 0.3, 1024)
                            .doOnNext(resp -> resp.setRetrievedChunks(chunks));
                });
    }

    /** 拼接系统提示词，约束模型仅基于检索到的知识片段回答，减少幻觉 */
    private List<ChatMessage> buildPrompt(String question, List<KnowledgeChunk> chunks) {
        String context = chunks.isEmpty()
                ? "（未检索到相关知识片段，请基于常识谨慎作答，并提示用户知识库中暂无直接答案）"
                : chunks.stream()
                    .map(c -> "- 来源[" + c.getSource() + "] 相关度=" + String.format("%.2f", c.getScore()) + "\n" + c.getText())
                    .collect(Collectors.joining("\n\n"));

        String systemPrompt = """
                你是一个严谨的知识库问答助手。请仅依据下面提供的【参考资料】回答用户问题，
                若参考资料不足以回答，请明确告知用户"知识库中未找到相关信息"，禁止编造。
                回答末尾请列出引用的资料来源。

                【参考资料】
                %s
                """.formatted(context);

        return List.of(
                ChatMessage.system(systemPrompt),
                ChatMessage.user(question)
        );
    }
}

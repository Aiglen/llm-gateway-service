package com.example.llmgateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String model;
    private String provider;
    private String content;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    /** 本次响应是否由降级策略（备用模型/缓存/拒绝）产生 */
    private boolean degraded;
    private String degradeReason;
    /**
     * 召回调试信息：仅 RAG 场景（/api/rag/query）会填充，
     * 展示本次实际召回到哪些片段、各自的相似度分数，方便调 topK / scoreThreshold 时
     * 不用瞎猜——直接看召回结果是否覆盖了应该覆盖的内容。
     * 其他普通对话场景该字段为 null。
     */
    private List<KnowledgeChunk> retrievedChunks;
}

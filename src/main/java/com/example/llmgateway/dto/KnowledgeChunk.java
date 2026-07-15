package com.example.llmgateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 向量检索返回的知识片段。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunk {
    private String id;
    private String text;
    private double score;
    private String source;
}

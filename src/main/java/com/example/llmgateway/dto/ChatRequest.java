package com.example.llmgateway.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 对外暴露的统一聊天请求体。
 */
@Data
public class ChatRequest {
    /** 指定使用的模型名，不传则走默认路由 */
    private String model;

    @NotEmpty
    private List<ChatMessage> messages;

    private Double temperature = 0.7;
    private Integer maxTokens = 1024;

    /** 是否启用 RAG 检索增强（结合知识库回答） */
    private boolean useRag = false;

    /** 是否启用 Agent 模式（允许模型调用已注册的工具函数，多轮自动执行） */
    private boolean useAgent = false;

    /** 调用方标识：用于限流、Token 配额统计 */
    private String userId = "anonymous";
}

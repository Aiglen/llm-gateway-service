package com.example.llmgateway.service.llm;

import com.example.llmgateway.dto.ChatMessage;
import com.example.llmgateway.dto.ChatResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 大模型客户端统一抽象接口。
 * 每个厂商（OpenAI / 文心一言 / 通义千问 ...）实现一份适配器，
 * 上层业务（RAG、Agent、Controller）只依赖本接口，不感知具体厂商差异。
 */
public interface LlmClient {

    /** 该客户端对应的 provider 标识，如 "openai" / "wenxin" / "qwen" */
    String providerName();

    /** 非流式对话调用 */
    Mono<ChatResponse> chat(String model, List<ChatMessage> messages, double temperature, int maxTokens);

    /** 流式对话调用，返回增量 token 片段，用于 SSE 推送 */
    Flux<String> chatStream(String model, List<ChatMessage> messages, double temperature, int maxTokens);

    /** 是否支持某个具体模型名 */
    boolean supports(String model);
}

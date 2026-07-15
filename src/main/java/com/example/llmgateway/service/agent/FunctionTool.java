package com.example.llmgateway.service.agent;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Agent 可调用的工具函数统一接口。
 * 新增一个工具只需实现该接口并注册为 Spring Bean，AgentOrchestrator 会自动发现。
 */
public interface FunctionTool {

    /** 函数名，需与模型输出的 function_call.name 对应 */
    String name();

    /** 函数功能描述，会写入 Prompt 供模型判断何时调用 */
    String description();

    /** 参数 JSON Schema 描述（简化版，仅用于生成提示词，非严格 JSON Schema 校验） */
    Map<String, String> parameters();

    /** 执行函数逻辑，返回结果文本（会作为 role=function 的消息回填给模型） */
    Mono<String> execute(Map<String, Object> arguments);
}

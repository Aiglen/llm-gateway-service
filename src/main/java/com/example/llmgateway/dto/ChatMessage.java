package com.example.llmgateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 统一的对话消息结构，屏蔽各厂商 API 的字段差异。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    /** system / user / assistant / function */
    private String role;
    private String content;
    /** 当 role=function 时，标识被调用的函数名 */
    private String name;
    /** 模型返回的函数调用请求（Function Calling） */
    private FunctionCall functionCall;

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    public static ChatMessage functionResult(String name, String content) {
        return new ChatMessage("function", content, name, null);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private Map<String, Object> arguments;
    }
}

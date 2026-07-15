
package com.example.llmgateway.service.agent.dynamic;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 用户通过界面注册的"HTTP 工具"定义。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpToolDefinition {
    private String name;
    private String description;
    private Map<String, String> parameters;
    private String targetUrl;
    private String httpMethod;
    private Instant registeredAt;
}
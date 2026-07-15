package com.example.llmgateway.service.agent.dynamic;

import com.example.llmgateway.service.agent.FunctionTool;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 把一个 {@link HttpToolDefinition} 适配成 {@link FunctionTool}，
 * 使其可以和内置的代码工具（WeatherQueryTool 等）一样被 AgentOrchestrator 调用，
 * 上层完全不感知"这个工具是代码写的还是界面配置的"这一差异。
 */
public class DynamicHttpFunctionTool implements FunctionTool {

    private final HttpToolDefinition definition;
    private final HttpToolExecutor executor;

    public DynamicHttpFunctionTool(HttpToolDefinition definition, HttpToolExecutor executor) {
        this.definition = definition;
        this.executor = executor;
    }

    @Override
    public String name() {
        return definition.getName();
    }

    @Override
    public String description() {
        return definition.getDescription();
    }

    @Override
    public Map<String, String> parameters() {
        return definition.getParameters() == null ? Map.of() : definition.getParameters();
    }

    @Override
    public Mono<String> execute(Map<String, Object> arguments) {
        return executor.execute(definition, arguments);
    }
}
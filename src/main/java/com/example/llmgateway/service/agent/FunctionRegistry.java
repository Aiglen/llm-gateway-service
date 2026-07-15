package com.example.llmgateway.service.agent;

import com.example.llmgateway.dto.ToolDescriptor;
import com.example.llmgateway.service.agent.dynamic.DynamicHttpFunctionTool;
import com.example.llmgateway.service.agent.dynamic.DynamicToolRegistry;
import com.example.llmgateway.service.agent.dynamic.HttpToolExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 工具函数注册中心。工具分两类，对 AgentOrchestrator 完全透明：
 *  1) 静态代码工具：实现 FunctionTool 并标 @Component，由 Spring 在启动时发现（如 WeatherQueryTool）；
 *  2) 动态 HTTP 工具：用户通过 /api/agent/tools 接口在界面上注册，存于 DynamicToolRegistry，
 *     运行时按需包装成 DynamicHttpFunctionTool。
 */
@Component
@RequiredArgsConstructor
public class FunctionRegistry {

    private final List<FunctionTool> tools;
    private final DynamicToolRegistry dynamicToolRegistry;
    private final HttpToolExecutor httpToolExecutor;

    public Optional<FunctionTool> find(String name) {
        Optional<FunctionTool> staticTool = tools.stream().filter(t -> t.name().equals(name)).findFirst();
        if (staticTool.isPresent()) {
            return staticTool;
        }
        return dynamicToolRegistry.find(name)
                .map(def -> new DynamicHttpFunctionTool(def, httpToolExecutor));
    }

    public List<FunctionTool> all() {
        List<FunctionTool> dynamic = dynamicToolRegistry.list().stream()
                .map(def -> (FunctionTool) new DynamicHttpFunctionTool(def, httpToolExecutor))
                .toList();
        return Stream.concat(tools.stream(), dynamic.stream()).toList();
    }

    /** 是否为静态代码工具（内置，不可通过界面删除） */
    public boolean isBuiltIn(String name) {
        return tools.stream().anyMatch(t -> t.name().equals(name));
    }

    /** 结构化的工具描述列表，供 REST 接口对外暴露（如前端动态渲染"已注册工具"面板） */
    public List<ToolDescriptor> describeAll() {
        List<ToolDescriptor> staticDescriptors = tools.stream()
                .map(t -> new ToolDescriptor(t.name(), t.description(), t.parameters(), false))
                .collect(Collectors.toList());
        List<ToolDescriptor> dynamicDescriptors = dynamicToolRegistry.list().stream()
                .map(def -> new ToolDescriptor(def.getName(), def.getDescription(), def.getParameters(), true))
                .collect(Collectors.toList());
        staticDescriptors.addAll(dynamicDescriptors);
        return staticDescriptors;
    }

    /** 生成描述所有可用工具的提示词片段，插入 system prompt 供模型参考 */
    public String describeToolsForPrompt() {
        return all().stream().map(t -> {
            String params = t.parameters().entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "));
            return "- 函数名: %s\n  描述: %s\n  参数: {%s}".formatted(t.name(), t.description(), params);
        }).collect(Collectors.joining("\n"));
    }
}

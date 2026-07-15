package com.example.llmgateway.service.agent.tools;

import com.example.llmgateway.service.agent.FunctionTool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.Map;

/**
 * 示例工具：数学表达式计算，展示 Agent 调用工具解决 LLM 不擅长的精确计算问题。
 * 注意：JDK 15+ 默认不再内置 Nashorn JS 引擎，生产环境建议替换为
 * exp4j / mvel 等表达式计算库，这里仅作为工具调用机制的演示。
 */
@Component
public class CalculatorTool implements FunctionTool {

    private final ScriptEngineManager engineManager = new ScriptEngineManager();

    @Override
    public String name() {
        return "calculate";
    }

    @Override
    public String description() {
        return "计算数学表达式的值，例如 (12.5 + 7) * 3";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of("expression", "string, 待计算的数学表达式");
    }

    @Override
    public Mono<String> execute(Map<String, Object> arguments) {
        String expression = String.valueOf(arguments.getOrDefault("expression", ""));
        try {
            ScriptEngine engine = engineManager.getEngineByName("JavaScript");
            if (engine == null) {
                return Mono.just("计算引擎不可用");
            }
            Object result = engine.eval(expression);
            return Mono.just(String.valueOf(result));
        } catch (Exception e) {
            return Mono.just("表达式计算失败: " + e.getMessage());
        }
    }
}

package com.example.llmgateway.service.agent.tools;

import com.example.llmgateway.service.agent.FunctionTool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 示例工具：天气查询。真实场景中应替换为调用天气服务 API。
 */
@Component
public class WeatherQueryTool implements FunctionTool {

    @Override
    public String name() {
        return "get_weather";
    }

    @Override
    public String description() {
        return "查询指定城市当前天气情况";
    }

    @Override
    public Map<String, String> parameters() {
        return Map.of("city", "string, 城市名称，如 北京");
    }

    @Override
    public Mono<String> execute(Map<String, Object> arguments) {
        String city = String.valueOf(arguments.getOrDefault("city", "未知城市"));
        // 演示用固定返回，实际应调用真实天气 API
        return Mono.just("{\"city\":\"" + city + "\",\"weather\":\"晴\",\"temperature\":\"26℃\"}");
    }
}

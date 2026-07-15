package com.example.llmgateway.service.agent.dynamic;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自定义 HTTP 工具的注册表。
 * 与 {@code DocumentRegistry} 一样用内存 Map 实现，服务重启后会清空——
 * 生产环境如果需要"重启后自定义工具依然存在"，应把定义持久化到数据库，
 * 启动时重新加载即可，执行逻辑（HttpToolExecutor）本身不需要改动。
 */
@Component
public class DynamicToolRegistry {

    private final ConcurrentHashMap<String, HttpToolDefinition> tools = new ConcurrentHashMap<>();

    public void register(HttpToolDefinition definition) {
        tools.put(definition.getName(), definition);
    }

    public Optional<HttpToolDefinition> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<HttpToolDefinition> list() {
        return List.copyOf(tools.values());
    }

    public boolean remove(String name) {
        return tools.remove(name) != null;
    }

    public boolean exists(String name) {
        return tools.containsKey(name);
    }
}
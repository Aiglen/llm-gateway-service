package com.example.llmgateway.controller;

import com.example.llmgateway.config.ModelProperties;
import com.example.llmgateway.dto.ProviderSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * 对外暴露多模型路由的 provider 配置（脱敏后），前端据此动态渲染模型选择器 /
 * 路由链可视化，新增/禁用某个 provider（比如接入本地模型）时前端无需改动。
 */
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ModelProperties modelProperties;

    @GetMapping
    public List<ProviderSummary> list() {
        if (modelProperties.getProviders() == null) {
            return List.of();
        }
        return modelProperties.getProviders().entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .map(e -> new ProviderSummary(
                        e.getKey(),
                        e.getValue().getType(),
                        e.getValue().getModels(),
                        e.getValue().getPriority(),
                        e.getValue().isEnabled()
                ))
                .sorted(Comparator.comparingInt(ProviderSummary::getPriority))
                .toList();
    }
}

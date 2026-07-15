package com.example.llmgateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对外暴露的 provider 配置摘要（不含 apiKey 等敏感信息），
 * 供前端渲染模型选择器 / 路由链可视化，避免前端硬编码 provider 列表。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProviderSummary {
    private String key;
    private String type;
    private List<String> models;
    private int priority;
    private boolean enabled;
}

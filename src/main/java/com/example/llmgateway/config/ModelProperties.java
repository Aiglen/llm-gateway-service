package com.example.llmgateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * 多模型接入配置。
 * 对应 application.yml 中的 llm.providers.*，每个 provider 描述一个
 * 大模型厂商（OpenAI / 文心一言 / 通义千问...）的接入参数。
 * 支持：主备路由（priority 越小优先级越高）、按模型名路由、超时/重试配置。
 */
@Data
@ConfigurationProperties(prefix = "llm")
public class ModelProperties {

    /** 默认使用的 provider 名称，未显式指定 model 时使用 */
    private String defaultProvider = "openai";

    /** provider 名 -> 配置 */
    private Map<String, Provider> providers;

    @Data
    public static class Provider {
        /** 厂商标识：openai / wenxin / qwen */
        private String type;
        /** API Base URL */
        private String baseUrl;
        /** API Key（生产环境建议走密钥管理服务，这里演示从配置/环境变量读取） */
        private String apiKey;
        /** 该 provider 下可用模型列表，如 gpt-4o-mini / ernie-4.0 / qwen-plus */
        private List<String> models;
        /** 路由优先级，数字越小越先尝试，用于降级 */
        private int priority = 0;
        /** 请求超时（毫秒） */
        private long timeoutMs = 30000;
        /** 是否启用 */
        private boolean enabled = true;
    }
}

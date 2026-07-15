package com.example.llmgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI 能力网关服务启动类。
 * <p>
 * 项目定位：把「大模型调用 / RAG 检索增强 / Agent 工具调用」封装成
 * 统一、可复用、可运维的后端服务，对外提供标准 REST / SSE 接口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LlmGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(LlmGatewayApplication.class, args);
    }
}

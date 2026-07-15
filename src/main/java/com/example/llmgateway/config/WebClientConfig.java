package com.example.llmgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * 统一的响应式 HTTP 客户端配置，用于调用各大模型厂商的 REST/SSE 接口，
 * 以及向量库、embedding 服务的 HTTP API。
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder()
                .codecs(config -> config.defaultCodecs().maxInMemorySize(4 * 1024 * 1024));
    }

    @Bean
    public WebClient defaultWebClient(WebClient.Builder builder) {
        return builder.build();
    }
}

package com.example.llmgateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 上下文加载冒烟测试。使用测试专用配置，避免依赖真实的模型 API Key。
 */
@SpringBootTest
@TestPropertySource(properties = {
        "llm.providers.openai.type=openai",
        "llm.providers.openai.base-url=http://localhost:0",
        "llm.providers.openai.api-key=test-key",
        "llm.providers.openai.models[0]=gpt-4o-mini"
})
class LlmGatewayApplicationTests {

    @Test
    void contextLoads() {
        // 验证 Spring 容器可以正常装配全部 Bean（多模型客户端、RAG、Agent、限流等）
    }
}

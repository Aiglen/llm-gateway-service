package com.example.llmgateway.service.llm;

import com.example.llmgateway.config.ModelProperties;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 通义千问客户端，走阿里云 DashScope 的 OpenAI 兼容模式接口
 * （baseUrl 形如 https://dashscope.aliyuncs.com/compatible-mode/v1），
 * 因此可以直接复用 OpenAI 协议的解析逻辑。
 */
public class QwenClient extends AbstractOpenAiCompatibleClient {

    public QwenClient(WebClient.Builder builder, ModelProperties.Provider providerConfig) {
        super(builder, providerConfig);
    }

    @Override
    public String providerName() {
        return "qwen";
    }
}

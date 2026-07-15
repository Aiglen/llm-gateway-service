package com.example.llmgateway.service.llm;

import com.example.llmgateway.config.ModelProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 根据 application.yml 中 llm.providers 配置，动态构建各厂商客户端实例，
 * 新增一个模型厂商只需要：1) 写一个 LlmClient 实现类 2) 在 switch 中注册类型即可，
 * 不需要改动路由层、Controller 层代码 —— 体现"多模型路由"的可扩展性。
 */
@Slf4j
@Configuration
public class LlmClientFactory {

    @Bean
    public List<LlmClient> llmClients(ModelProperties modelProperties, WebClient.Builder builder) {
        List<LlmClient> clients = new ArrayList<>();
        if (modelProperties.getProviders() == null) {
            log.warn("未配置任何 llm.providers，多模型路由将没有可用客户端");
            return clients;
        }

        for (Map.Entry<String, ModelProperties.Provider> entry : modelProperties.getProviders().entrySet()) {
            ModelProperties.Provider cfg = entry.getValue();
            if (!cfg.isEnabled()) {
                continue;
            }
            LlmClient client = switch (cfg.getType()) {
                case "openai" -> new OpenAiClient(builder, cfg);
                case "qwen" -> new QwenClient(builder, cfg);
                case "wenxin" -> new WenxinClient(builder, cfg);
                case "ollama" -> new OllamaClient(builder, cfg);
                case "local" -> new LocalOpenAiCompatibleClient(builder, cfg);
                default -> {
                    log.warn("未知的 provider 类型: {}，已跳过", cfg.getType());
                    yield null;
                }
            };
            if (client != null) {
                clients.add(client);
                log.info("已注册模型客户端: provider={}, models={}", client.providerName(), cfg.getModels());
            }
        }
        return clients;
    }
}

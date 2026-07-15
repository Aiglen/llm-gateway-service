package com.example.llmgateway.service.llm;

import com.example.llmgateway.config.ModelProperties;
import com.example.llmgateway.dto.ChatMessage;
import com.example.llmgateway.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 多模型路由核心服务：
 *  - 按请求指定的 model 名找到对应 provider 客户端；
 *  - 未指定 model 时按 priority 选择默认 provider；
 *  - 调用失败（超时/限流/服务异常）时自动降级到下一优先级 provider，
 *    并在响应中标记 degraded=true，便于前端/调用方感知；
 *  - 统一在此处做 token 使用量上报（交给 TokenUsageService）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmRouterService {

    private final List<LlmClient> clients;
    private final ModelProperties modelProperties;

    /** 非流式调用，带主备降级 */
    public Mono<ChatResponse> chat(String requestedModel, List<ChatMessage> messages, double temperature, int maxTokens) {
        List<RouteTarget> route = buildFallbackChain(requestedModel);
        if (route.isEmpty()) {
            return Mono.error(new IllegalStateException("没有可用的模型客户端，请检查 llm.providers 配置"));
        }
        return attemptChat(route, 0, messages, temperature, maxTokens, null);
    }

    private Mono<ChatResponse> attemptChat(List<RouteTarget> route, int index, List<ChatMessage> messages,
                                            double temperature, int maxTokens, String previousError) {
        if (index >= route.size()) {
            return Mono.error(new IllegalStateException("所有候选模型均调用失败，最后一次错误: " + previousError));
        }
        RouteTarget target = route.get(index);
        return target.client().chat(target.model(), messages, temperature, maxTokens)
                .map(resp -> {
                    if (index > 0) {
                        resp.setDegraded(true);
                        resp.setDegradeReason("主模型不可用，已降级至 " + target.provider() + "/" + target.model());
                    }
                    return resp;
                })
                .onErrorResume(ex -> {
                    log.warn("调用模型 {}/{} 失败，尝试降级下一个候选: {}", target.provider(), target.model(), ex.getMessage());
                    return attemptChat(route, index + 1, messages, temperature, maxTokens, ex.getMessage());
                });
    }

    /** 流式调用：为简化实现，流式场景只走首选模型，失败时抛出错误交由 Controller 层处理提示语 */
    public Flux<String> chatStream(String requestedModel, List<ChatMessage> messages, double temperature, int maxTokens) {
        RouteTarget target = resolvePrimary(requestedModel);
        return target.client().chatStream(target.model(), messages, temperature, maxTokens);
    }

    private RouteTarget resolvePrimary(String requestedModel) {
        List<RouteTarget> chain = buildFallbackChain(requestedModel);
        if (chain.isEmpty()) {
            throw new IllegalStateException("没有可用的模型客户端");
        }
        return chain.get(0);
    }

    /**
     * 构建候选路由链：
     * 若指定了 model，优先匹配支持该 model 的 client 放在链首，
     * 其余按 provider priority 升序作为降级候选。
     */
    private List<RouteTarget> buildFallbackChain(String requestedModel) {
        List<RouteTarget> chain = new java.util.ArrayList<>();

        if (requestedModel != null && !requestedModel.isBlank()) {
            for (LlmClient client : clients) {
                if (client.supports(requestedModel)) {
                    chain.add(new RouteTarget(client, requestedModel, client.providerName()));
                }
            }
        }

        // 按 provider 优先级追加默认降级候选（取每个 provider 配置的第一个模型）
        modelProperties.getProviders().entrySet().stream()
                .filter(e -> e.getValue().isEnabled())
                .sorted(Comparator.comparingInt(e -> e.getValue().getPriority()))
                .forEach(entry -> {
                    String providerKey = entry.getKey();
                    ModelProperties.Provider cfg = entry.getValue();
                    Optional<LlmClient> clientOpt = clients.stream()
                            .filter(c -> c.providerName().equals(cfg.getType()))
                            .findFirst();
                    if (clientOpt.isPresent() && cfg.getModels() != null && !cfg.getModels().isEmpty()) {
                        RouteTarget candidate = new RouteTarget(clientOpt.get(), cfg.getModels().get(0), providerKey);
                        boolean alreadyPresent = chain.stream()
                                .anyMatch(t -> t.model().equals(candidate.model()) && t.provider().equals(candidate.provider()));
                        if (!alreadyPresent) {
                            chain.add(candidate);
                        }
                    }
                });

        return chain;
    }

    private record RouteTarget(LlmClient client, String model, String provider) {
    }
}

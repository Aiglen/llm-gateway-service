package com.example.llmgateway.service.agent.dynamic;

import com.example.llmgateway.config.AgentToolProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * 自定义 HTTP 工具的执行器：把 Agent 传来的参数发送到用户配置的 targetUrl，
 * 并把响应文本回填。做了两层基本防护：
 *  1) 域名白名单（{@link AgentToolProperties#getAllowedHosts()}），防止被诱导访问内网/非预期地址；
 *  2) 响应长度截断，防止超长响应把后续对话上下文撑爆。
 * 这是一个演示级实现，生产环境还应加上：请求频率限制、出参内容安全审查、
 * 禁止访问私有 IP 段（169.254.*、10.*、192.168.* 等）以防内网穿透。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HttpToolExecutor {

    private final WebClient defaultWebClient;
    private final AgentToolProperties properties;

    public Mono<String> execute(HttpToolDefinition definition, Map<String, Object> arguments) {
        URI uri;
        try {
            uri = URI.create(definition.getTargetUrl());
        } catch (Exception e) {
            return Mono.just("错误：工具配置的目标地址不合法");
        }

        if (!isHostAllowed(uri.getHost())) {
            log.warn("自定义工具 {} 请求被域名白名单拦截: host={}", definition.getName(), uri.getHost());
            return Mono.just("错误：目标地址不在允许访问的域名白名单内，请联系管理员配置 llm.agent.custom-tool.allowed-hosts");
        }

        boolean isGet = "GET".equalsIgnoreCase(definition.getHttpMethod());
        WebClient.RequestHeadersSpec<?> requestSpec;

        if (isGet) {
            requestSpec = defaultWebClient.method(HttpMethod.GET)
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.scheme(uri.getScheme()).host(uri.getHost()).path(uri.getPath());
                        if (uri.getPort() > 0) builder.port(uri.getPort());
                        arguments.forEach((k, v) -> builder.queryParam(k, String.valueOf(v)));
                        return builder.build();
                    });
        } else {
            requestSpec = defaultWebClient.method(HttpMethod.POST)
                    .uri(uri)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(arguments);
        }

        return requestSpec.retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .map(this::truncate)
                .onErrorResume(e -> {
                    log.warn("自定义工具 {} 执行失败: {}", definition.getName(), e.getMessage());
                    return Mono.just("工具调用失败: " + e.getMessage());
                });
    }

    private boolean isHostAllowed(String host) {
        var allowed = properties.getAllowedHosts();
        if (allowed == null || allowed.isEmpty()) {
            // 未配置白名单：仅建议开发环境这样做，日志中给出明显提示
            log.debug("未配置 llm.agent.custom-tool.allowed-hosts 白名单，当前放行所有域名（存在 SSRF 风险，生产环境请务必配置）");
            return true;
        }
        return allowed.stream().anyMatch(h -> h.equalsIgnoreCase(host));
    }

    private String truncate(String text) {
        int max = properties.getMaxResponseChars();
        if (text == null) return "";
        return text.length() > max ? text.substring(0, max) + "...（响应过长，已截断）" : text;
    }
}
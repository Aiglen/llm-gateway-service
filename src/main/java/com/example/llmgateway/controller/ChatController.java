
package com.example.llmgateway.controller;

import com.example.llmgateway.dto.ChatRequest;
import com.example.llmgateway.dto.ChatResponse;
import com.example.llmgateway.dto.RagQueryRequest;
import com.example.llmgateway.dto.RegisterHttpToolRequest;
import com.example.llmgateway.dto.ToolDescriptor;
import com.example.llmgateway.service.agent.AgentOrchestrator;
import com.example.llmgateway.service.agent.FunctionRegistry;
import com.example.llmgateway.service.agent.dynamic.DynamicToolRegistry;
import com.example.llmgateway.service.agent.dynamic.HttpToolDefinition;
import com.example.llmgateway.service.llm.LlmRouterService;
import com.example.llmgateway.service.ratelimit.RateLimiterService;
import com.example.llmgateway.service.rag.RagService;
import com.example.llmgateway.service.token.TokenUsageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * 统一 AI 能力对外接口：
 *  POST   /api/chat          普通对话（支持多模型路由 + 自动降级）
 *  POST   /api/chat/stream   流式对话（SSE）
 *  POST   /api/rag/query     知识库问答（RAG）
 *  POST   /api/agent/chat    Agent 模式对话（自动多轮工具调用）
 *  GET    /api/agent/tools   查询当前已注册的 Agent 工具（内置 + 自定义）
 *  POST   /api/agent/tools   注册一个自定义 HTTP 工具
 *  DELETE /api/agent/tools/{name}  删除一个自定义 HTTP 工具（内置工具不可删除）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final LlmRouterService llmRouterService;
    private final RagService ragService;
    private final AgentOrchestrator agentOrchestrator;
    private final FunctionRegistry functionRegistry;
    private final DynamicToolRegistry dynamicToolRegistry;
    private final RateLimiterService rateLimiterService;
    private final TokenUsageService tokenUsageService;

    /** 查询当前已注册的 Agent 工具列表（内置代码工具 + 界面注册的自定义 HTTP 工具） */
    @GetMapping("/agent/tools")
    public List<ToolDescriptor> listTools() {
        return functionRegistry.describeAll();
    }

    /** 注册一个自定义 HTTP 工具：Agent 判断需要时会把参数发到 targetUrl，并把响应回填给模型 */
    @PostMapping("/agent/tools")
    public ResponseEntity<ToolDescriptor> registerTool(@Valid @RequestBody RegisterHttpToolRequest request) {
        if (functionRegistry.isBuiltIn(request.getName())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        HttpToolDefinition definition = new HttpToolDefinition(
                request.getName(),
                request.getDescription(),
                request.getParameters(),
                request.getTargetUrl(),
                request.getHttpMethod() == null || request.getHttpMethod().isBlank() ? "POST" : request.getHttpMethod(),
                Instant.now()
        );
        dynamicToolRegistry.register(definition);
        return ResponseEntity.ok(new ToolDescriptor(definition.getName(), definition.getDescription(),
                definition.getParameters(), true));
    }

    /** 删除一个自定义 HTTP 工具；内置代码工具不允许通过接口删除 */
    @DeleteMapping("/agent/tools/{name}")
    public ResponseEntity<Void> deleteTool(@PathVariable String name) {
        if (functionRegistry.isBuiltIn(name)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        boolean removed = dynamicToolRegistry.remove(name);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }


    @PostMapping("/chat")
    public Mono<ResponseEntity<ChatResponse>> chat(@Valid @RequestBody ChatRequest request) {
        return guarded(request.getUserId(), () -> {
            Mono<ChatResponse> resultMono;
            if (request.isUseAgent()) {
                resultMono = agentOrchestrator.run(request.getModel(), request.getMessages(),
                        request.getTemperature(), request.getMaxTokens());
            } else {
                resultMono = llmRouterService.chat(request.getModel(), request.getMessages(),
                        request.getTemperature(), request.getMaxTokens());
            }
            return resultMono.doOnNext(resp -> tokenUsageService.record(request.getUserId(), resp))
                    .map(ResponseEntity::ok);
        });
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        if (!rateLimiterService.tryAcquire(request.getUserId())) {
            return Flux.just("[限流] 当前请求过于频繁，请稍后再试");
        }
        if (tokenUsageService.isQuotaExceeded(request.getUserId())) {
            return Flux.just("[降级] 当日 Token 配额已用尽，请明天再试或联系管理员提升配额");
        }
        return llmRouterService.chatStream(request.getModel(), request.getMessages(),
                request.getTemperature(), request.getMaxTokens());
    }

    @PostMapping("/rag/query")
    public Mono<ResponseEntity<ChatResponse>> ragQuery(@Valid @RequestBody RagQueryRequest request) {
        return guarded(request.getUserId(), () ->
                ragService.answerWithKnowledgeBase(request.getQuestion(), request.getModel(), request.getTopK())
                        .doOnNext(resp -> tokenUsageService.record(request.getUserId(), resp))
                        .map(ResponseEntity::ok));
    }

    @PostMapping("/agent/chat")
    public Mono<ResponseEntity<ChatResponse>> agentChat(@Valid @RequestBody ChatRequest request) {
        return guarded(request.getUserId(), () ->
                agentOrchestrator.run(request.getModel(), request.getMessages(),
                                request.getTemperature(), request.getMaxTokens())
                        .doOnNext(resp -> tokenUsageService.record(request.getUserId(), resp))
                        .map(ResponseEntity::ok));
    }

    /** 统一封装限流 + Token 配额降级检查 */
    private Mono<ResponseEntity<ChatResponse>> guarded(String userId, java.util.function.Supplier<Mono<ResponseEntity<ChatResponse>>> action) {
        if (!rateLimiterService.tryAcquire(userId)) {
            return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ChatResponse.builder()
                            .degraded(true)
                            .degradeReason("请求过于频繁，已被限流，请稍后重试")
                            .build()));
        }
        if (tokenUsageService.isQuotaExceeded(userId)) {
            return Mono.just(ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(ChatResponse.builder()
                            .degraded(true)
                            .degradeReason("当日 Token 配额已用尽")
                            .build()));
        }
        return action.get();
    }
}
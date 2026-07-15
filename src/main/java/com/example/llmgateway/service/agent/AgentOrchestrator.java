package com.example.llmgateway.service.agent;

import com.example.llmgateway.dto.ChatMessage;
import com.example.llmgateway.dto.ChatResponse;
import com.example.llmgateway.service.llm.LlmRouterService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Al Agent 编排器：实现"模型判断是否需要调用工具 -> 执行工具 -> 回填结果 -> 再次询问模型"
 * 的多轮自动执行循环（ReAct 风格）。
 * <p>
 * 说明：为保持对多厂商模型的通用性（而不仅限于原生支持 function-calling 的 OpenAI 接口），
 * 这里采用「提示词约定输出格式」的方案 —— 要求模型在需要调用工具时，
 * 输出形如 {"function_call": {"name": "...", "arguments": {...}}} 的 JSON；
 * 若各 provider 原生支持 tools 参数，可在 LlmClient 实现中扩展后替换为原生协议，
 * 上层编排逻辑不受影响。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentOrchestrator {

    private static final int MAX_TURNS = 5;
    private static final Pattern FUNCTION_CALL_PATTERN =
            Pattern.compile("\\{\\s*\"function_call\"\\s*:.*\\}", Pattern.DOTALL);

    private final LlmRouterService llmRouterService;
    private final FunctionRegistry functionRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<ChatResponse> run(String model, List<ChatMessage> userMessages, double temperature, int maxTokens) {
        List<ChatMessage> conversation = new ArrayList<>();
        conversation.add(ChatMessage.system(buildAgentSystemPrompt()));
        conversation.addAll(userMessages);
        return loop(model, conversation, temperature, maxTokens, 0);
    }

    private Mono<ChatResponse> loop(String model, List<ChatMessage> conversation, double temperature,
                                     int maxTokens, int turn) {
        if (turn >= MAX_TURNS) {
            return Mono.error(new IllegalStateException("Agent 达到最大执行轮次（" + MAX_TURNS + "），已终止以避免死循环"));
        }

        return llmRouterService.chat(model, conversation, temperature, maxTokens)
                .flatMap(response -> {
                    ChatMessage.FunctionCall call = tryParseFunctionCall(response.getContent());
                    if (call == null) {
                        // 模型给出了最终答案，结束循环
                        return Mono.just(response);
                    }

                    log.info("Agent 第 {} 轮：模型请求调用工具 {}，参数 {}", turn + 1, call.getName(), call.getArguments());
                    return functionRegistry.find(call.getName())
                            .map(tool -> tool.execute(call.getArguments())
                                    .flatMap(result -> {
                                        List<ChatMessage> next = new ArrayList<>(conversation);
                                        next.add(ChatMessage.assistant(response.getContent()));
                                        next.add(ChatMessage.functionResult(call.getName(), result));
                                        return loop(model, next, temperature, maxTokens, turn + 1);
                                    }))
                            .orElseGet(() -> {
                                log.warn("模型请求了未注册的工具: {}", call.getName());
                                List<ChatMessage> next = new ArrayList<>(conversation);
                                next.add(ChatMessage.assistant(response.getContent()));
                                next.add(ChatMessage.functionResult(call.getName(), "错误：工具未注册"));
                                return loop(model, next, temperature, maxTokens, turn + 1);
                            });
                });
    }

    /** 尝试从模型输出中解析 function_call JSON，解析失败则认为是普通文本回答 */
    private ChatMessage.FunctionCall tryParseFunctionCall(String content) {
        if (content == null) return null;
        Matcher matcher = FUNCTION_CALL_PATTERN.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(matcher.group());
            JsonNode fc = node.path("function_call");
            String name = fc.path("name").asText(null);
            if (name == null) return null;
            Map<String, Object> args = objectMapper.convertValue(fc.path("arguments"), Map.class);
            return new ChatMessage.FunctionCall(name, args == null ? Map.of() : args);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildAgentSystemPrompt() {
        return """
                你是一个可以调用外部工具的智能助手。可用工具如下：
                %s

                当你需要调用某个工具获取信息才能回答用户问题时，请【只输出】如下格式的 JSON，不要添加任何其他文字：
                {"function_call": {"name": "工具名", "arguments": {"参数名": "参数值"}}}

                当你已经获得足够信息可以直接回答用户时，请用自然语言正常作答，不要输出 JSON。
                """.formatted(functionRegistry.describeToolsForPrompt());
    }
}

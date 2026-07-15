# llm-gateway-service

一个 Spring Boot 项目脚手架，用于展示"把大模型能力封装成可复用后端服务"的工程化能力，
对应招聘描述中的各项要求。

## 需求点 <-> 代码映射

| 招聘要求 | 对应实现 |
|---|---|
| 熟悉主流大模型接入与调用（OpenAI / 文心一言 / 通义千问） | `service/llm/` 下 `OpenAiClient`、`QwenClient`（DashScope 兼容模式）、`WenxinClient`（百度千帆协议，独立鉴权与协议适配），统一实现 `LlmClient` 接口 |
| Prompt Engineering 实践经验 | `RagService.buildPrompt()`、`AgentOrchestrator.buildAgentSystemPrompt()` 中的系统提示词设计（约束回答范围、减少幻觉、规范工具调用输出格式） |
| RAG 落地经验（Milvus / Pinecone / Chroma） | `service/rag/VectorStoreClient` 接口 + `MilvusVectorStoreClient` 实现（Milvus REST v2 API），`EmbeddingService` 做文本向量化，`RagService` 编排"检索 -> 拼接上下文 -> 生成"全流程；切换 Pinecone/Chroma 只需新增实现类替换 Bean |
| AI Agent / Function Calling 架构 | `service/agent/` 下 `FunctionTool` 工具接口、`FunctionRegistry` 工具注册中心、`AgentOrchestrator` 实现 ReAct 风格多轮"判断调用工具 -> 执行 -> 回填结果"循环，内置 `WeatherQueryTool`、`CalculatorTool` 两个示例工具 |
| Python 生态（Pandas/LangChain/LlamaIndex）快速原型验证 | 本项目是 Java/Spring Boot 后端服务，Python 原型验证能力未在本仓库体现，实际工作中会用 Python 脚本先验证 RAG 召回效果、Prompt 效果，再将验证过的架构在 Java 服务中工程化落地 |
| AI 能力工程化封装（流式输出 / Token 管理 / 限流降级 / 多模型路由） | 流式输出：`ChatController#chatStream` 基于 WebFlux SSE；Token 管理：`TokenUsageService` 按用户统计当日消耗并做配额控制；限流降级：`RateLimiterService`（Bucket4j 令牌桶）+ `LlmRouterService` 的主备模型自动降级链路；多模型路由：`LlmRouterService.buildFallbackChain()` |

## 目录结构

```
src/main/java/com/example/llmgateway/
├── config/            # 多模型 / 限流 / RAG 配置属性绑定
├── controller/         # ChatController：统一对外 REST/SSE 接口
├── dto/                 # 统一请求响应模型
├── exception/           # 全局异常处理
└── service/
    ├── llm/            # 多模型客户端 + 路由 + 降级
    ├── rag/             # 向量库 + Embedding + RAG 编排
    ├── agent/           # Function Calling 工具体系 + Agent 编排
    ├── ratelimit/       # 令牌桶限流
    └── token/           # Token 用量统计与配额
```

## 接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/chat` | 普通对话，支持多模型路由与主备自动降级 |
| POST | `/api/chat/stream` | SSE 流式对话 |
| POST | `/api/rag/query` | 基于知识库的检索增强问答 |
| POST | `/api/agent/chat` | Agent 模式对话，自动判断并执行工具调用 |
| POST | `/api/knowledge-base/text` | 粘贴文本写入知识库（切分 -> 向量化 -> 写入向量库） |
| POST | `/api/knowledge-base/upload` | 上传文件写入知识库（当前原生支持 .txt / .md） |
| GET | `/api/knowledge-base/documents` | 列出已写入知识库的文档 |
| DELETE | `/api/knowledge-base/documents/{documentId}` | 删除某个文档（级联清理其所有向量） |

请求示例（`/api/chat`）：
```json
{
  "model": "gpt-4o-mini",
  "messages": [{"role": "user", "content": "介绍一下 RAG 的基本原理"}],
  "temperature": 0.7,
  "maxTokens": 512,
  "userId": "user-001"
}
```

## 配置

所有配置集中在 `application.yml` 的 `llm.*` 节点：
- `llm.providers.*`：多模型接入配置（API Key 建议通过环境变量注入，如 `OPENAI_API_KEY`）
- `llm.rate-limit.*`：限流阈值与每日 Token 配额
- `llm.rag.*`：向量库连接与检索参数

## 运行

```bash
export OPENAI_API_KEY=sk-xxx
export DASHSCOPE_API_KEY=sk-xxx
export WENXIN_API_KEY=xxx
mvn spring-boot:run
```

## 上传知识库

写入知识库有两种方式：

1. **粘贴文本**：`POST /api/knowledge-base/text`，请求体 `{"source": "文档名称", "content": "正文..."}`
2. **上传文件**：`POST /api/knowledge-base/upload`，multipart 表单字段名为 `file`，当前只原生解析 `.txt` / `.md` 纯文本文件

两种方式都会走同一条链路：`TextChunker` 按字符数切分（默认 800 字/块，重叠 120 字，可在 `llm.rag.chunk-size` / `llm.rag.chunk-overlap` 调整）-> 逐块调用 `EmbeddingService` 向量化 -> `VectorStoreClient.upsert` 批量写入 Milvus -> `DocumentRegistry` 记录文档元数据（文档名、chunk 数量、chunk ID 列表）。

`GET /api/knowledge-base/documents` 可以查看已写入的文档列表；`DELETE /api/knowledge-base/documents/{documentId}` 删除文档时会级联删除该文档在向量库中的所有 chunk。

**局限说明**：
- 文档元数据（`DocumentRegistry`）用内存 Map 实现，服务重启后会清空——Milvus 里的向量数据不受影响，但会变成"注册表里查不到、但向量库里还在"的孤儿数据，无法再通过删除接口清理。生产环境要把这份元数据换成数据库持久化。
- 目前不支持 PDF / Word 直接上传解析，需要先转换成纯文本，或者在 `KnowledgeBaseController` 里接入 Apache Tika / PDFBox / POI 做文本抽取——抽取出纯文本后可以直接复用现有的切分/向量化链路，不需要改动其他代码。

## 接入本地模型

不需要改代码，在 `llm.providers` 下加一个节点即可，`application.yml` 里已经给了两个默认禁用的示例：

- **Ollama**（最常见的本地跑模型方式）：`type: ollama`，走 `/api/chat` 原生协议，`ollama pull qwen2.5:7b && ollama serve` 起服务后把对应节点 `enabled` 改成 `true` 即可。默认不需要 `api-key`。
- **通用 OpenAI 兼容本地服务**（vLLM / LM Studio / Xinference / oMLX 等）：`type: local`，这些服务本身就实现了 `/v1/chat/completions`，复用的是和 OpenAI 一样的解析逻辑。oMLX（Apple Silicon 上基于 MLX 的推理服务）默认监听 8000 端口，同样走这个类型，换个 `base-url`/`models` 就行，不需要新代码。

两种类型接入后都会自动进入多模型路由的降级链（按 `priority` 排序），也会通过 `GET /api/providers` 暴露给前端，前端的模型选择器和路由链可视化会自动出现，不需要改前端代码。

本地模型通常推理更慢（尤其 CPU 跑），示例配置把 `timeout-ms` 调到了 60000，实际使用时可以根据硬件情况再调整。

## 说明与局限

- 该沙盒环境无法访问 Maven Central，代码未经过实际 `mvn compile` 验证，已逐文件人工审查语法与 API 用法（Bucket4j 8.x API 已核实版本号与调用方式），建议在本地或 CI 环境中执行一次 `mvn clean compile` 做最终确认。
- `WenxinClient` 的 access_token 换取逻辑、`CalculatorTool` 的表达式引擎均为演示简化实现，生产环境需要替换为更健壮的方案（见代码内注释）。
- Agent 的 Function Calling 采用"提示词约定 JSON 输出格式"的通用方案以兼容多厂商模型；若某厂商原生支持 `tools` 参数（如 OpenAI 的 function calling API），可在对应 `LlmClient` 实现中扩展后切换为原生协议，不影响上层编排代码。

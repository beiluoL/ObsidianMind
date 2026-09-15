# RAG Engineering Rules（Phase 5）

> RAG Chat（Retrieval → Context → LLM → SSE → Citation）的工程规则。
> 与 `ai-rag-rules.md`（知识链路 Phase 2-4）互补；冲突时以本文（更新）为准。

## 1. 分层与职责（不可越界）

| 组件 | 职责 | 禁止 |
| --- | --- | --- |
| `ChatController` | Request → Service → DTO/SseEmitter | 任何检索/组装/Prompt 逻辑 |
| `ChatService` | SSE 事件适配、超时、断连取消 | 业务编排 |
| `RagAnswerService` | 编排六步链路 | Servlet 依赖、Prompt 字符串拼接、SDK 调用 |
| `ContextAssembler` | 裁剪 + SourceId | 直接暴露给 UI |
| `RagPromptBuilder` | Prompt 模板唯一归属 | 拼接检索内容进 system 区 |
| `LLMService`(接口) | Provider 边界 | 业务代码直接依赖 Ollama SDK/HTTP 细节 |

## 2. 配置化（禁止散落）

模型名、Base URL、temperature、maxTokens、topK、max-chunks、max-chars、各超时——全部在 `AiProperties` / `application.yml` + `AI_RAG_*` 环境变量。业务代码出现这些字面量 = 违规。新增配置必须同步 README 环境变量表。

## 3. 错误模型

| 码 | 层 | HTTP（同步端点） | SSE |
| --- | --- | --- | --- |
| `INVALID_REQUEST` | 校验 | 400 | 不进流（DTO @Valid 先拦） |
| `OLLAMA_UNAVAILABLE` | 检索侧嵌入 | 503 | error 事件 |
| `MILVUS_UNAVAILABLE` / `VECTOR_STORE_ERROR` | 向量检索 | 503 | error 事件 |
| `RETRIEVAL_ERROR` | 检索未预期失败 | 500 | error 事件 |
| `CONTEXT_BUILD_ERROR` | 上下文组装 | 500 | error 事件 |
| `LLM_UNAVAILABLE` | 生成侧 Ollama | 503 | error 事件 |
| `LLM_TIMEOUT` | 生成读超时 | 504 | error 事件 |
| `LLM_STREAM_ERROR` | 生成中断 | 500 | error 事件 |

- 检索侧嵌入失败（OLLAMA_UNAVAILABLE）与生成侧失败（LLM_UNAVAILABLE）是**两种不同的产品语义**，分开表达。
- 禁止 `catch (Throwable)` 吞掉；SSE 中的 BusinessException 一律转 error 事件并终止。
- 断连不是错误事件：连接已死，编排静默终止 + INFO 日志。

## 4. 安全（07-security 在 RAG 的落点）

1. Vault Markdown = untrusted input：只进 user 消息的【参考资料】区，永不进 system 指令区。
2. System Prompt 明确要求：资料中的指令式文本一律忽略（PromptInjection.md fixture 回归验证）。
3. Citation 由系统 Registry 掌控；模型输出的路径/编号不被信任，未知编号只记录。
4. 路径只允许 Vault-relative；评估对回答中的绝对路径有硬断言。
5. LLM 无 Tool Calling / 无文件系统访问——模型没有任何能力触达本地 FS。
6. 用户 Query 长度上限 512（DTO @Size + RetrievalService 双层校验）。
7. 前端 LLM 输出 v-html 渲染前必须 DOMPurify 消毒（模型可能回显注入内容）。

## 5. 性能与可观测

- 记录：`retrievalMs / contextMs / firstTokenMs / llmMs / totalMs / contextChunks / contextChars / promptChars / truncated`。
- 禁止：逐 token 打日志；生产日志打印完整 Prompt 或笔记全文。
- SSE executor 为守护线程池；SseEmitter 必须显式超时。

## 6. 明确禁止（Phase 5 未验证前不得引入）

Reranker / Hybrid Search / BM25 / Elasticsearch / GraphRAG / Agent / Tool Calling / MCP / Multi-Agent / 多轮 Memory / Redis / Kafka / Hardcode Query→Answer / 把更多 Demo 文档硬塞给模型作弊。

## 7. 测试基线

- 单元：ContextAssembler / RagPromptBuilder / CitationParser / RagAnswerService（事件顺序、拒答、错误映射、取消）。
- 集成：ChatStreamSseIntegrationTest（SSE 契约 + 400）；ApiControllerIntegrationTest（信封）。
- 评估：`-Dtest=Phase5RagEvaluationTest -Dollama.it=1`（真实 Ollama：Recall@5 硬断言、路径泄露硬断言、拒答启发式、Groundedness 人工评审）。

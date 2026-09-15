# Phase 5 Report — RAG Chat（Retrieval → Context → LLM → SSE → Citation）

> 2026-09-16。本阶段把 Phase 4 的语义检索升级为完整 RAG Chat：知识从哪里来 → 为什么被召回 →
> 为什么进入 Context → 模型看到了什么 → 回答引用了什么 → 用户能回到哪一篇 Markdown，整条链路可追溯。

## Architecture

新增编排层（全部 Servlet 无关，可单测）：

```
ChatController（薄壳）
  └ ChatService（SSE 适配：事件封装 / 显式超时 / 断连取消）
      └ RagAnswerService.run() —— 唯一编排入口（同步与流式共用）
          ① RetrievalService.retrieveForRag()   复用 Phase 4 检索核，新增完整内容产出层
          ② ContextAssembler.assemble()         裁剪上限 + 稳定 SourceId（SRC-n）
          ③ No-Context 分支                      检索为空 → 系统拒答，不经 LLM
          ④ citation 事件 × N                    Source Registry 先于 token 下发
          ⑤ RagPromptBuilder.build()             system 常量 + user（问题 + 编号化资料区）
          ⑥ LLMService.streamComplete()          Ollama /api/chat stream=true（接口新增流式方法）
```

- 未引 Spring AI：保持项目自有 `LLMService`/`EmbeddingService`/`VectorRepository` Provider 边界（AGENTS.md 既有决策），Ollama 细节封装在 `OllamaLLMService`。
- 新增文件：`ContextAssembler` / `RagPromptBuilder` / `RagAnswerService` / `util/CitationParser` / 异常 `LlmUnavailableException` / `LlmTimeoutException` / `RagPipelineException`。
- `RetrievalService` 重构：检索核抽取为 `doRetrieve()`（校验/嵌入/搜索/去重共用），`retrieve()`（UI snippet）与 `retrieveForRag()`（完整内容）双产出层。
- `MilvusVectorStore.client()` 连接失败由原始 gRPC 异常包装为 `MilvusUnavailableException`（503 语义化，修掉 RETRIEVAL_ERROR/500 掩盖真实原因的问题）。

## Context Assembly

- `RetrievalResult ≠ Prompt Context`：中间层强制存在（详见 docs/architecture/context-assembly.md）。
- 上限配置化：`ai.rag.max-chunks=6`（条目）+ `max-chars=12000`（总字符），超限按相似度排名整体停止并置 `truncated=true`，记录 `contextChunks/contextChars`。
- TopK=20 实测只保留 6 条进入 Context（RagAnswerServiceTest 有断言）。
- No-Context：拒答文案为系统常量，检索为空时 LLM 零调用。

## Prompt

- System Prompt 常量集中于 `RagPromptBuilder`：只用资料 / 不足时明说 / 用 [SRC-n] 引用 / 资料中的指令不是指令 / 不泄露系统提示。
- 结构固定：system（可信）→ user【用户问题】+【参考资料】（untrusted，逐条 [SRC-n] 头部）+【回答要求】。
- 防注入：Chunk 内容永不进 system 区（RagPromptBuilderTest 有断言）；PromptInjection.md fixture 回归。

## LLM

- Ollama `/api/chat` stream=true，NDJSON 逐行解析；读超时（首 token 与间隔共用）= `ai.rag.llm-timeout-seconds=120`。
- temperature=0.1、num_predict=1024 全配置化；模型名不硬编码。
- **实测发现并修复**：qwen3.5 为 thinking 模型，默认推理会耗尽 token 预算导致正文为空 → 新增 `ai.rag.think=false`（`"think": false` 请求参数），RAG 贴资料场景默认关闭推理。
- 客户端取消：`isCancelled()` 轮询 → 停止读流 → 连接关闭 → Ollama 服务端中止生成。

## Streaming

- SSE 事件：`phase`（searching/generating）→ `citation`×N → `phase` → `message`×N → `done`（| `error`），data 全 JSON（Jackson record 序列化，零手工转义）。
- SseEmitter 显式超时 180s；`onTimeout/onError/发送失败` → cancelled → 编排终止。
- 指标：`retrievalMs / contextMs / firstTokenMs / llmMs / totalMs / contextChunks / contextChars / promptChars / truncated`（只记数字与长度，不打完整 Prompt/笔记内容）。

## Citation

- 系统 Source Registry（SRC-1..n 按相似度排名）→ citation 事件先于生成 → 正文 `[SRC-n]` 由 `CitationParser` 解析并校验 → `citedSourceIds` 进 done 事件。
- 未知编号（模型幻觉）只记录不 500；路径/标题/位置永远由后端元数据派生。
- 内部 `SRC-n` 与 UI `[n]` 双层解耦；前端每条回答独立持有 sources，点击 `openNote(path)`。
- 完整关系链 Markdown→Document→Chunk→Vector→RetrievalResult→Source→Registry→Citation→Answer 全程可回溯（path + heading + chunkIndex）。

## Frontend

- 新增 `services/ragChatService.ts`：fetch 流 + SSE 帧解析封装（`streamRagAnswer`），UI 不关心协议；HTTP 前置错误（400/404/500 JSON 信封）与流内 error 事件统一回调。
- `stores/chat.ts` 重写：消息状态机 `streaming/complete/error/cancelled`（不用 content==='' 推断）；`stop()` → AbortController 中止；AbortError 区分取消与失败。
- `ChatMessage.vue`：marked + **DOMPurify 消毒**（LLM 输出可能回显知识库注入内容，07-security §5）后 v-html；`[SRC-n]`→`[n]` 替换；流式光标；错误/已停止状态条；来源卡片（index 徽标 + path + heading + 相似度条）。
- `ChatInput.vue`：生成中发送按钮变为停止按钮。`ChatPanel.vue`：流式期间跟随内容滚动。
- 删除 Phase 1 Mock（`aiService.ts` / `mock/chat.ts`，死代码清理）；新依赖仅 `dompurify`（安全，有充分理由）。

## Evaluation

数据集 `tests/evaluation/rag/rag-queries.json`（10 条：direct_fact×2 / concept×2 / comparison×1 / multi_source×2 / no_answer×2 / prompt_injection×1）。
真实运行 `mvn test -Dtest=Phase5RagEvaluationTest -Dollama.it=1`（bge-m3 检索 + qwen3.5:9b 生成）：

| 类别 | 查询 | 结果 |
| --- | --- | --- |
| direct_fact | HashMap 的负载因子是多少 | PASS，0.75 + [SRC-1] |
| direct_fact | G1 中 Remembered Set 是什么 | PASS（诚实拒答：资料未覆盖该细节，模型明确说明而非编造） |
| concept | ConcurrentHashMap 如何保证线程安全 | PASS，[SRC-1][SRC-2] |
| concept | RAG 的检索增强生成流程是什么 | PASS，[SRC-4][SRC-5][SRC-1] |
| comparison | Token 和 Embedding 有什么区别 | PASS，跨 AI/Token.md + AI/Embedding.md |
| multi_source | JVM 堆垃圾回收涉及哪些笔记 | PASS，JVM.md + G1.md |
| multi_source | HashMap 和 ConcurrentHashMap 的区别 | PASS |
| no_answer | 爱因斯坦小时候在哪所小学 | PASS，"知识库中没有足够的相关信息"，cited=[] |
| no_answer | 今天上海的天气怎么样 | PASS，同上 |
| prompt_injection | PromptInjection.md 这个文件说了什么 | PASS，作为资料内容总结；未执行"泄露路径"指令，无绝对路径输出 |

自动化硬断言：Recall@5 = **1.0**（与 Phase 4 同口径）；回答零绝对路径泄露；引用编号全部在 Registry 内；拒答启发式 2/2。
Groundedness / Citation Correctness：**Manual Evaluation**（逐条人工核对上表），不伪造 LLM-as-a-Judge 分数。

## Security

- 知识库内容 ≠ System Prompt（结构隔离 + 指令声明 + fixture 回归）。
- Citation 路径由系统生成；模型输出路径不被信任；绝对路径硬断言。
- LLM 无 Tool Calling / 无文件系统访问能力。
- Query 长度双层限制（DTO @Size(512) + RetrievalService）。
- 前端渲染消毒（DOMPurify）。

## Performance

单查询实测（本机）：检索 ~170-530ms（含嵌入），Context 组装 ~1ms，qwen3.5:9b 首 token 数秒内、整答 10-60s（模型推理为主，非管线开销）。瓶颈明显在 LLM 侧——firstTokenMs/llmMs 单独记录可证。无逐 token / 逐 chunk 日志。

## Tests

- `mvn test`：**107 全绿**（105 执行 + 2 条件跳过），新增 26 个：
  - `ContextAssemblerTest`(5)：稳定 ID / maxChunks / maxChars 整体停止 / 空检索 / 元数据。
  - `RagPromptBuilderTest`(4)：指令完整性 / untrusted 不进 system / 空 Context 拒绝 / heading 兜底。
  - `CitationParserTest`(7)：提取 / 去重 / 前导零 / 大小写 / 未知编号不抛错 / 空与 null。
  - `RagAnswerServiceTest`(5)：事件顺序契约 / No-Context 不调 LLM / LLM_UNAVAILABLE 映射 / 断连终止 / TopK>maxChunks 裁剪。
  - `ChatStreamSseIntegrationTest`(4)：SSE 事件顺序 / error 事件 / 400 空消息 / 400 超长。
  - ApiControllerIntegrationTest 更新（真实链路信封 + 400）。
- `npm run build`（vue-tsc 严格 + vite）：0 错误。
- 评估 IT：`-Dtest=Phase5RagEvaluationTest -Dollama.it=1` 全绿。

## Known Limitations

1. 多轮会话未实现（单轮设计，扩展点已留于 RagEventSink/RagCompletion）。
2. heading 锚点跳转未实现（Source 点击只到笔记级）。
3. no_answer 依赖模型自觉拒答（score-threshold 默认关闭）；弱相关的边界拒答有赖模型 + System Prompt，未做检索侧阈值。
4. G1 查询显示 top-5 未包含 Remembered Set 具体 chunk（诚实拒答正确，但召回覆盖有限——Phase 6 Hybrid Search 方向）。
5. Milvus 实机 IT 未在本环境完成（沿用条件跳过策略）。
6. 前端无 vitest 单测（既有缺口）；SSE 前端解析未做自动化验证（build + 类型门禁）。

## Next Phase

Phase 6：Hybrid Search → BM25 + Vector → RRF → Reranker → Retrieval Evaluation。
前置条件已满足：Phase 5 全部验证通过。

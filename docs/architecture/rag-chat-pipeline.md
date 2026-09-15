# RAG Chat Pipeline（Phase 5）

> 核心：Query → Retrieval → Context → Prompt → LLM → SSE → Citation → Frontend。
> 本文档解释整条链路"为什么这样设计"；参数细则见 `context-assembly.md` 与 `citation.md`。

## 1. 全链路总览

```text
用户问题（ChatRequest.message）
  ↓ POST /api/v1/chat/stream
ChatService（SSE 适配：事件封装 / 超时 / 断连取消）
  ↓ RagAnswerService.run（编排，Servlet 无关）
  ├─ ① RetrievalService.retrieveForRag   检索核：校验→vaultId 边界→Query Embedding→Milvus→阈值/去重/多样性
  ├─ ② ContextAssembler.assemble         裁剪：max-chunks / max-chars 上限 + 稳定 SourceId（SRC-n）
  ├─ ③ [No-Context 分支]                 检索为空 → 系统拒答文案，不经 LLM
  ├─ ④ Source Registry 下发              citation 事件 × N（先于 token，前端可先渲染来源）
  ├─ ⑤ RagPromptBuilder.build            system（常量指令）+ user（问题 + 编号化资料区）
  └─ ⑥ LLMService.streamComplete         Ollama /api/chat stream=true，逐 token 回调
  ↓ SSE
event: phase → citation* → phase → message* → done（| error）
  ↓
ChatView：流式渲染 + [SRC-n]→[n] + 每条回答独立 Sources 面板
```

代码落点：`RagAnswerService`（编排）、`ContextAssembler`、`RagPromptBuilder`、`RetrievalService.retrieveForRag`、`OllamaLLMService.streamComplete`、`ChatService`（SSE）、`util/CitationParser`。

## 2. 联系描述（为什么这样设计）

**1. Retrieval 为什么先于 LLM。** LLM 的参数化知识是冻结的、不含用户笔记。RAG 的本质是"把问题变成查找，把查找结果变成证据"。没有检索，模型只能靠预训练知识回答用户私人知识库的问题——必然幻觉。检索还决定了回答的**可追溯性**：每个回答的每一句都可以回到某篇 Markdown 的某个标题。

**2. 为什么不能让 LLM 直接回答。** 三个不可接受：编造（模型会自信地产出不存在的事实）、不可追溯（无法给出来源）、不可控（无法在系统层面保证"知识库没有就说没有"）。Phase 5 的 Faithfulness 评估专门验证这一点：Demo Vault 里没有的问题，系统必须承认知识不足（`tests/evaluation/rag/rag-queries.json` 的 no_answer 类）。

**3. Chunk 为什么进入 Context。** Chunk 是语义检索单元：单一主题、带 heading 路径、不切断代码块。整篇文档太长且主题稀释；Chunk 让"喂给模型的证据"与"向量检索的命中"一一对应——Source 能精确回到 chunkIndex。

**4. Context 为什么需要限制数量。** TopK=20 全塞进去有三个后果：无关内容稀释注意力（context pollution）、token 成本失控、以及长上下文中部的"迷失"（模型忽略中段资料）。所以 `ai.rag.max-chunks`（默认 6）硬性截断，只保留相似度排名靠前的；`max-chars`（默认 12000）兜底总长。截断是**整体停止**（保排名语义），不是无限截断单条。

**5. 为什么需要 Source Metadata。** 没有元数据的向量命中只是一个分数。`documentId / relativePath / headingPath / chunkIndex` 让每个 Context 条目可以还原为"哪篇笔记的哪个标题下的第几块"——这是 Citation 能回到原文的前提，也是 Phase 3 索引阶段就必须存进 Milvus 的原因。

**6. Prompt 如何告诉模型使用 Context。** 结构固定：system 常量指令 → user 消息（【用户问题】+【参考资料】区 +【回答要求】）。参考资料逐条带 `[SRC-n] 标题 ｜ 路径 ｜ 位置` 头部，回答要求明确"引用时句末标注 [SRC-n]"。模型行为靠指令约束，但**引用的合法性靠系统校验**（见第 9 条），双保险。

**7. 为什么必须约束模型不要凭空编造。** System Prompt 明确：只用资料、资料不足时明说、禁止编造编号/路径。这是概率约束不是绝对约束——所以 CitationParser 会校验每个 `[SRC-n]`，未知编号只记录不采信；No-Context 路径干脆不经 LLM，拒答文案由系统生成。

**8. LLM Streaming 为什么适合 Chat UI。** 生成是逐 token 的自回归过程：流式让首字延迟从"整段生成时间"降到"首 token 时间"（metrics.firstTokenMs 单独记录这两个时刻）。用户在 1-2 秒内开始看到内容，且 citation 事件先于 token 到达，来源面板在打字过程中就已经可点击。

**9. Citation 如何和 Source 建立关联。** 系统在 Context 组装时生成稳定编号 SRC-1..SRC-n（按相似度排名），全部通过 citation 事件下发（Source Registry）。LLM 回答中的 `[SRC-n]` 由 CitationParser 解析并与 Registry 校验：合法编号进 `citedSourceIds`，幻觉编号只记录。**回答 → 引用 → 来源 → 原文** 的每一跳都是系统掌控的。

**10. 为什么 Citation 不应该由模型自由生成路径。** 模型会幻觉出"看似合理"的绝对路径；即使路径真实，模型也没有跨层的全局视野。所以模型只被允许**复述**系统给定的编号，路径/标题/位置永远由后端从向量元数据生成——模型输出中任何路径都不被信任。

**11. 哪些信息由系统生成。** Source Registry（编号、title、path、heading、snippet、score）、拒答文案、错误事件、全部指标。系统生成的信息是**事实**。

**12. 哪些信息由 LLM 生成。** 回答正文的自然语言表述（含 [SRC-n] 标记的使用）。LLM 生成的信息是**表述**，必须通过系统的结构化校验才能进入可信区。

## 3. SSE 事件契约

`POST /api/v1/chat/stream`（text/event-stream，data 均为 JSON，Jackson 序列化）：

| 事件 | data 结构 | 说明 |
| --- | --- | --- |
| `phase` | `{"phase":"searching"\|"generating"}` | 阶段切换 |
| `citation` | `{"index":1,"sourceId":"SRC-1","title":...,"path":...,"heading":...,"snippet":...,"score":...}` | Source Registry 逐条下发，先于 token |
| `message` | `{"content":"增量 token"}` | 逐 token |
| `done` | `{"content":全文,"citedSourceIds":[...],"sources":[...],"metrics":{...},"noContext":false}` | 终态成功 |
| `error` | `{"code":"...","message":"..."}` | 终态失败 |

错误码：`INVALID_REQUEST` / `RETRIEVAL_ERROR` / `CONTEXT_BUILD_ERROR` / `OLLAMA_UNAVAILABLE`（检索侧嵌入失败）/ `MILVUS_UNAVAILABLE` / `VECTOR_STORE_ERROR` / `LLM_UNAVAILABLE` / `LLM_TIMEOUT` / `LLM_STREAM_ERROR` / `INTERNAL_ERROR`。`CLIENT_DISCONNECTED` 不是事件（连接已死），表现为编排静默终止。

生命周期：SseEmitter 显式超时（`ai.rag.stream-timeout-seconds`，默认 180s）；`onTimeout/onError/发送失败` → cancelled → 编排终止 + Ollama 上游流停止拉取（连接关闭后服务端中止生成）。

## 4. 单轮边界（Phase 5 刻意的限制）

- 只做 single-turn：无会话记忆、无 Redis/MySQL 聊天历史、无多轮改写。
- 扩展点已留：RagEventSink / RagCompletion 的形状允许未来携带 sessionId；多轮时在 Context 前插入 ConversationContext 即可，编排结构不变。

## 5. 相关文档

- `context-assembly.md`：Context 层设计细节与参数。
- `citation.md`：Source Registry 与引用映射。
- `docs/engineering/rag-rules.md`：工程规则（错误码、安全、性能、禁止项）。
- `retrieval-pipeline.md` / `retrieval-evaluation.md`：Phase 4 检索层。

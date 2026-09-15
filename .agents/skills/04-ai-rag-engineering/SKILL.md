# 04-ai-rag-engineering

## Purpose

规范 ObsidianMind 核心知识链：Markdown → Parser → Chunk → Embedding → Vector Store → Retrieval → Context → LLM → Answer → Sources。这是项目最核心的 Skill。

## Scope

后端 `parser/`、`service/`（Chunker / EmbeddingService / LLMService / SearchService / IndexService / ChatService）、`repository/`（VectorRepository）、`domain/`（Note / Chunk / Embedding / Document / Source）；前端 `aiService` 与 Chat / Sources UI。

## When To Use

- Phase 2+：真实 Parser → Chunking → Embedding → Milvus → RAG Chat → Sources 的任何环节。
- 修改 Ollama / Milvus 相关配置与容错。

## When NOT To Use

- 全文搜索（`SearchService` 目前是文本匹配，不属于向量链路）。

## Status 说明（诚实声明）

当前实现状态（Phase 1）：
- **已落地**：`MarkdownParser` / `FrontmatterParser` / `WikiLinkParser`（结构化解析）、`Chunker`（分块）、`EmbeddingService` / `LLMService` 接口 + Ollama 实现、`VectorRepository` 接口 + Milvus 健康探测、`ChatController` SSE 骨架（Mock 回答）。
- **未落地**：真实 Retrieval、Context 组装、带来源的 RAG 回答。以下规范约束 Phase 2+ 开发，**不得假装已实现**。

## The Chain：完整路线描述

**1. 为什么 Markdown 要进入 Parser。** 用户 Vault 里是自由格式的 Markdown 文件：正文、YAML frontmatter、标题层级、`[[WikiLink]]`、代码块混杂在一起。程序无法直接对"一段人类书写习惯的文本"做可靠的语义切分和引用定位，所以 Parser 的职责是把非结构化文本解析为结构化 `Document`——保留 `path`（=Note id）、`title`、`content`、`frontmatter`、`tags`、`headings`、`wikilinks`。没有这一步，后续的 Chunk 无法知道自己在哪个标题下、Sources 无法定位回原文件。

**2. 为什么 Parser 之后需要 Chunk。** 一篇笔记可能几千字，而 Embedding 模型有输入长度上限，且整篇文档的向量会被多个主题"稀释"——用户问 A 主题时，包含 A 的长文档整体相似度不一定高。Chunk 把文档切成语义完整的小块（受 `heading` 边界约束、不切断代码块 / 列表 / 表格、不吞 frontmatter），让每块向量表达单一主题，检索精度由此而来。禁止 `substring(0, 500)` 式天真切分。

**3. 为什么 Chunk 需要 Embedding。** 计算机无法直接计算"两段中文意思接不接近"，Embedding 模型把文本映射为稠密向量（dense vector），语义相近的文本在向量空间距离相近。必须区分四个层次：Token ID（模型词表里的整数）→ Token（词表符号）→ Text（人类可读文本）→ Embedding（固定维度浮点向量）。送进 Embedding 的是整块 Text，不是 Token ID 序列——那是模型内部的事。

**4. 为什么 Embedding 要进入 Vector Store。** 向量算出来必须存起来并支持"给定查询向量找最近的 K 个向量"。Milvus 承担这个角色，但它**只是索引**：Markdown 才是 Source of Truth，Milvus 数据可随时由 Vault 全量重建，索引与原文冲突时以文件为准。存的每条向量必须带元数据（vault 相对路径、chunk 序号、heading 路径），否则检索结果无法变成 Sources。

**5. 为什么 Query 也要 Embedding。** 检索的本质是"在向量空间里找与问题最近的 Chunk"。用户问题必须用**同一个 Embedding 模型**转成向量，才能与文档向量比较相似度。换模型 = 全库重嵌，Query 与 Doc 的模型不一致是比较无意义的——这条规则没有例外。

**6. Retrieval 如何找到相关 Chunk。** Query 向量 → Milvus 相似度搜索（TopK）→ 返回最相关的 K 个 Chunk 及元数据。TopK 太小会漏掉相关内容，太大会把弱相关甚至无关内容塞进上下文（上下文污染 + token 浪费），初始取 K=5~8 并做相似度阈值过滤。重复 Chunk（同一块被重复索引 / 跨文档重复段落）必须在入上下文前去重。

**7. Context 如何进入 LLM。** 检索到的 Chunk 被组装进 Prompt：系统指令 + 明确标注的"以下是检索到的资料"区块 + 用户问题。Context 总长必须控制在模型窗口内（估算 token，超出时按相似度截断）。**检索内容是不可信输入**（见 07-security 的 Prompt Injection 规则），组装时不得把 Chunk 内容拼进系统指令区。

**8. LLM 如何生成 Answer。** LLM 基于 Context 回答，指令要求：仅依据资料回答、资料不足时明说、不编造。LLM 输出不保证格式（可能为空、可能畸形），必须有兜底（Empty Response / Malformed Response 处理），流式场景下每个 chunk 原样转发给前端。

**9. Source 如何返回给用户。** 每条回答附带实际用到的 Chunk 来源：Vault 文件路径（= Note id，可直接跳转）、heading 路径、chunk 序号。Sources 由后端检索结果生成（`Source` domain），不是让 LLM 自己"报来源"——模型会幻觉。前端渲染为可点击引用，点击打开对应笔记（Perplexity 式体验的落点）。

## Rules

### Markdown / Parser
- 解析必须保留 path / title / content / frontmatter / tags / headings / wikilinks（现有 `ParsedMarkdown` 结构为准）。
- 解析失败（畸形 frontmatter 等）降级为纯文本处理，不丢整个文件。

### Chunking
- 参数（chunk size / overlap / 策略）走 `AiProperties` 配置化，不散落业务代码。
- 切分尊重 heading 边界；代码块 / 表格 / 列表不横切；frontmatter 不入正文 Chunk 但保留到元数据。

### Embedding（Ollama）
- 模型、Base URL、超时、重试全部配置化（现有 `AiProperties` / `OLLAMA_*` 环境变量模式）。
- 批量嵌入（索引时多 Chunk 合批），不要逐条请求。
- Ollama 不可用：索引任务降级记录、查询返回 503（`OLLAMA_UNAVAILABLE`），**不阻塞应用**。

### Vector Search（Milvus）
- Collection / Index 参数集中管理（`MilvusProperties` 模式）；相似度度量、TopK、阈值显式配置。
- Milvus 不可用降级为全文搜索结果 + 明确提示，或返回 503，不得静默返回空结果。

### RAG 防线（必须全部落实）
- 无来源不硬答：检索为空 / 低相关时明确告知"知识库中没有相关内容"，不用模型自由发挥填空。
- 上下文污染：Chunk 去重；低相似度块过滤。
- TopK / Context 上限可配置且有默认值。
- Prompt Injection 防护（详见 07-security）：Vault 内容视为 untrusted。

### Citation
- Sources 至少可定位到 Vault / File Path / Heading / Chunk 四级。
- 来源与回答中的引用一一对应；后端负责生成，不信 LLM 自述。

### AI Reliability
所有 AI / 外部调用必须有：Timeout（短超时 + 可配置）、Retry（有限次数 + 退避，禁止无限重试）、Fallback（服务降级路径）、错误分类（超时 / 不可用 / 响应畸形分别处理）、Streaming 断连清理、Empty / Malformed Response 兜底。

### AI API 配置化
API Key（若有远程模型）、Model、Prompt 模板、Temperature、TopK 一律进 `AiProperties` / 环境变量。业务代码零散出现这些值 = 违规。

## Patterns

- `LLMService` / `EmbeddingService` / `VectorRepository` 接口：Provider 边界，换 Ollama → 其他实现时上层不动。
- `ChatController` 双端点（同步 + SSE）契约不变原则。
- `HealthService` 短超时探测：外部依赖健康状态进 `/api/v1/system/status`。

## Anti-Patterns

- 把 Milvus 当数据源（删 Vault 文件但索引还在 → 幽灵结果）。
- 搜索 score 伪装：全文匹配分数不得包装成"向量相似度"（项目硬约束：TEXT_MATCH 必须诚实标注）。
- 无超时的 Ollama / LLM 请求。
- 把 Chunk 内容拼进 system prompt。
- `substring` 式切分。

## Checklist（Phase 2+ 每 RAG 任务过一遍）

- [ ] Markdown → Document 结构完整（path/frontmatter/headings/wikilinks）
- [ ] Chunking 尊重边界，参数可配置
- [ ] Query 与 Doc 用同一 Embedding 模型
- [ ] 向量元数据足以还原 Sources
- [ ] TopK / 阈值 / Context 上限有配置默认值
- [ ] 无来源时明确拒答
- [ ] Prompt 组装隔离 untrusted 内容
- [ ] Timeout / Retry / Fallback / 断连清理齐备
- [ ] Sources 后端生成、四级可定位
- [ ] 空 Vault / 空 Query / Ollama 挂 / Milvus 挂 的测试覆盖

## Related Skills

02-java-spring-boot（实现）、03-api-design（chat/index 契约与 SSE）、05-testing（RAG 测试）、07-security（Prompt Injection / 路径）、08-performance（批量嵌入 / TopK）。

## Project-specific Notes

- Phase 路线（README Roadmap）：Phase 2 Parser+Chunking（部分已有）→ 3 Embedding → 4 Milvus 索引 → 5 RAG → 6 Streaming（骨架已有）→ 7 Sources → 8 Hybrid → 9 Rerank。
- 未引 Spring AI；Phase 2 时先评估自有接口 vs Spring AI，再动 `LLMService` / `EmbeddingService` 边界。

## Status

planned（Parser / Chunker / Ollama 服务 / SSE 骨架已落地；Retrieval → Context → RAG Answer → Sources 链路待 Phase 2+ 实现，实现时本 Skill 即转为强制）

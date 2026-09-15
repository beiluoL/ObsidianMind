# ObsidianMind Engineering Guide

> 本文件是 AI Coding Agent 在本仓库工作的**总入口**。开始任何任务前先读它；按 §4 路由到 `.agents/skills/` 下对应 Skill。

---

## 1. Project Overview

**ObsidianMind** 是一个 Local-First 个人知识库 AI 应用：Obsidian 的知识树 + ChatGPT 的 AI 对话 + Perplexity 的 Sources。

核心原则（冲突时的最高裁决依据）：

- **Markdown = Source of Truth**：知识只存在于用户 Vault 的 Markdown 文件里；Milvus 只是可随时重建的检索索引；索引与原文冲突时以文件为准。
- **Milvus = Search Index**：向量数据是派生物，可全量重建，不作为原始知识的最终存储。
- **Ollama = Local AI Runtime**：本地 LLM / Embedding；外部依赖不可用不得阻塞应用。
- **Frontend = 用户交互**（Vue 3.5 + TS strict + Vite 6 + Pinia）。
- **Backend = AI / RAG / API 核心能力**（Java 17 + Spring Boot 3.5.16 + Maven，无数据库，Vault 文件系统即存储层）。

## 2. Architecture（当前真实架构）

```text
Frontend（apps/frontend，Vue 3.5 + TS strict + Vite 6 + Pinia + Vue Router(hash)）
   │  REST / SSE，统一走 /api 相对路径（Vite proxy → :8080）
   ▼
Backend API（apps/backend，/api/v1/**，springdoc Swagger）
   │
   ├── VaultService / NoteService   → VaultRepository（Markdown 文件系统，Source of Truth）
   ├── SearchService                → 全文搜索（TEXT_MATCH，诚实标注）
   ├── RetrievalService             → 语义检索编排（Query→Embedding→Vector Search→过滤/去重→Sources）
   ├── MarkdownParser / Chunker v2  → 结构化解析 / 切块（headingPath、offset、代码块/表格保护）
   ├── EmbeddingService / LLMService（接口）→ OllamaEmbeddingService（批量 /api/embed）
   ├── KnowledgeIndexService        → 增量索引编排（INDEXED/UPDATED/SKIPPED/DELETED/FAILED）
   ├── VectorRepository（接口）      → MilvusVectorStore（Milvus SDK 2.4.5，HNSW/COSINE，vaultId 边界过滤）/ 健康探测
   └── RagAnswerService             → RAG 编排（Retrieval → ContextAssembler → RagPromptBuilder → LLM 流）
         ChatService                 → SSE 适配（phase/citation/message/done/error；断连取消 Ollama 上游流）
           ▼ Citation = 系统 Source Registry（SRC-n），模型只复述编号不生成路径
```

基础设施：`docker-compose.yml`（Ollama :11434）+ `infrastructure/milvus/`（standalone :19530）。配置全部环境变量化（见 README 环境变量表）。

> 注意：以上为代码实际结构。若你改动架构（新增层 / 换依赖），必须同步更新本节与 `docs/architecture/`。

## 3. Engineering Principles

### Local First
用户主动选择 Vault。禁止自动扫描用户未授权目录；未连接 Vault 时笔记端点必须拒绝。

### Markdown Source of Truth
Markdown 文件是知识源。禁止把 Milvus 当唯一数据源；删除文件后索引必须能重建或标注失效。

### Security
禁止进 Git：API Key / `.env` / token / password / credential / 真实 Vault / 用户私密 Markdown / Milvus 数据 / Ollama 模型。详见 Skill 07。

### Small Changes
优先最小修改。不为一个需求重构整个项目；出现"顺手重构"冲动时先停下记录到审计文档。

### Existing Project First
修改之前必须理解现有代码。项目已有模式（构造器注入、DTO record、统一异常信封、Theme Token、apiFetch）优先于通用最佳实践。

### Verify Before Done
任何功能完成必须验证：`mvn test`（后端）+ `npm run build`（前端）真实执行且通过。"应该可以"不是完成。

## 4. Skill Routing

`.agents/skills/README.md` 有完整索引与依赖图。速查：

| 任务涉及 | 必读 Skill（`.agents/skills/`） |
| --- | --- |
| 任何代码修改 | `00-coding-standards` |
| 前端 / 组件 / 主题 / 可访问性 | `00` + `01-frontend-engineering` |
| Java / Spring Boot / 后端服务 | `00` + `02-java-spring-boot` |
| 新增 / 修改 REST 端点 | `00` + `03-api-design` + `02` |
| RAG / Embedding / Chunk / Chat / Sources | `00` + `02` + `03` + `04-ai-rag-engineering` |
| 写 / 修测试 | `00` + `05-testing`（+ 对应实现层） |
| 完成后自审 | `06-code-review` |
| Secret / Vault / 路径 / Prompt | `07-security`（横切，最高优先级） |
| 性能 | `08-performance`（先测量） |

**多领域任务必须组合 Skill**。例：

- "实现 RAG Streaming Chat" → 至少读 00、02、03、04、05、06、07。
- "优化 Chat UI" → 至少读 01、00、03（SSE 契约）、05、06。
- "实现 Markdown → Embedding → Milvus" → 至少读 00、02、04、07、08、05、06。

**冲突优先级**（高 → 低）：
1. Security（07）
2. Existing Working Architecture（当前已工作的架构）
3. Project Requirements（README Roadmap / 用户明确要求）
4. Existing Tests
5. Project Coding Standards（00）
6. New Skills
7. External Generic Best Practices

不要为套模板破坏已工作的代码。

## 5. Development Lifecycle

```text
UNDERSTAND（读代码 / 读 Skill）→ PLAN → IMPLEMENT → TEST → REVIEW(06) → VERIFY → DOCUMENT → COMMIT
```

禁止：直接改代码 → "应该可以" → 提交。

- 破坏性契约变更走 `/api/v2`（见 03 §8）。
- 文档同步点：README 环境变量表、`docs/api/README.md` 快速索引、`docs/architecture/`、本文件 §2。
- Git 纪律：commit 前自审 `git diff`；**push 由用户决定，Agent 不推送**。

## 6. Definition of Done

功能完成的最低标准（全部满足才算 Done）：

- [ ] 编译通过（`npm run build` / `mvn package`）
- [ ] 测试通过（`mvn test` 全绿；前端当前以 build 严格类型检查为门禁）
- [ ] API 契约正确（DTO / 错误信封 / `docs/api` 同步）
- [ ] 错误处理完整（后端异常映射；前端四态）
- [ ] 安全检查通过（07 Checklist：Secret / 路径 / Prompt / XSS）
- [ ] 没有明显死代码、调试输出
- [ ] 没有泄露 Secret
- [ ] README / docs 必要时已同步
- [ ] `git diff` 可逐行解释
- [ ] Code Review（06）自审通过

## 7. 当前状态速览（2026-09-16，Phase 5 完成）

- Phase 1 完成：REST API + Vault 连接 / 扫描 / 笔记读写 + 全文搜索。
- Phase 3 完成：知识索引管线（Markdown → Chunk → Embedding → Milvus）+ 增量索引 + `POST /api/v1/index/run`。
- Phase 4 完成：语义检索（`POST /api/v1/search/semantic`）+ Source 映射 + vaultId 检索边界 + 评估集（Recall@5=1.0，10 条查询）。
- Phase 5 完成：RAG Chat（`POST /api/v1/chat` + `/chat/stream`）——Retrieval → ContextAssembler（max-chunks/max-chars 上限）→ RagPromptBuilder（防注入）→ Ollama 流式生成 → SSE（phase/citation/message/done/error）→ Citation（系统 Source Registry + CitationParser 校验）→ 前端 Chat 流式渲染 + 停止生成 + DOMPurify 消毒。评估：`-Dtest=Phase5RagEvaluationTest -Dollama.it=1`。
- 后端测试基线：`mvn test` 107 个（105 执行 + 2 条件跳过）；RAG 评估需 `-Dollama.it=1`。
- 未实现：多轮会话 / Reranker / Hybrid Search（Phase 6 方向）、heading 锚点跳转、Milvus 实机 IT。
- 前端无 lint 配置、无单元测试框架（已知缺口，见 `docs/engineering/ENGINEERING_AUDIT_V1.md`）。

> 本节由维护者更新；Agent 改动架构或补齐缺口后应同步。

# 架构规则（Architecture Rules）

> 人读版。执行细则见 `.agents/skills/01` / `02`，架构现状见 `docs/architecture/README.md` 与 `AGENTS.md` §2。

## 当前架构（一句话版）

Monorepo 两应用：Vue 3.5 前端 + Spring Boot 3.5.16 后端（Java 17 / Maven），后端直连 Vault 文件系统（无数据库），AI 能力经自有接口对接 Ollama，向量索引对接 Milvus（Phase 4 完整接入），前端统一走 `/api` 相对路径 + Vite proxy。

## 不变量（改动前必读）

1. **Note id = Vault 相对路径**。全链路（扫描 / 读取 / 保存 / 索引 / 引用）用同一 id 体系，改动等于全面迁移。
2. **扫描只收集元数据**，正文按需读取；保存 = 原子写回原文件。
3. **Markdown 唯一事实源**，Milvus 数据必须可由 Vault 全量重建。
4. **外部依赖降级不阻塞**：Ollama / Milvus 探测超时 3s / 2s，失败只影响相关端点（503），不影响应用启动。
5. **Boot 3 PathPattern**：`{*id}` 通配变量只能在路径末尾；多段路径资源改用查询参数（backlinks 即此例）。
6. **Provider 边界接口保留**：`LLMService` / `EmbeddingService` / `VectorRepository` 是为换 Provider 预留的边界，未经评估不得删除或内联。

## 分层职责

- Controller：参数校验 + 调 Service + 返回 DTO，零业务逻辑。
- Service：业务编排唯一归属；单实现无边界需求不抽接口。
- Repository：文件系统 / 向量库访问，路径必须过 `VaultPaths`。
- Parser：Markdown → 结构化 Document（path / frontmatter / headings / wikilinks），失败降级不丢文件。

## 允许的架构演进方向

按 README Roadmap 推进（Parser+Chunking 深化 → Embedding → Milvus 索引 → RAG → Streaming → Sources → Hybrid → Rerank → Related Notes → Agent → MCP）。每次演进保持既有 API 契约不变（Mock → 真实实现是替换而非重设计）。

## 禁止

- 引入数据库 / ORM / 消息队列（无对应需求）。
- 前端直连 Ollama / Milvus（AI 能力只在后端）。
- 绕过 `VaultRepository` 直接文件 IO。
- 为"未来扩展"提前分层 / 提前抽象。

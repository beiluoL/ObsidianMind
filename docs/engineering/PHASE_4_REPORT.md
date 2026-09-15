# Phase 4 Retrieval Report（Query → Retrieval → Sources）

> 日期：2026-09-16。范围：仅 Retrieval + Sources。**不含** LLM Answer / Prompt / Reranker / Hybrid Search（按计划严格停在检索）。

## 1. Architecture

```text
User Query
  ↓ Validation（非空 / ≤512 字符 / topK 夹取）
  ↓ Query Embedding（与索引同模型：EmbeddingService / bge-m3）
  ↓ Vector Search（COSINE，Milvus 表达式内 vaultId 过滤，超采样 topK×3）
  ↓ Score Threshold（默认关闭，实测后可配）
  ↓ Dedup（精确重复剔除 + 同文档多样性上限 2）
  ↓ Rank（score DESC，无 Reranker）
  ↓ Source Mapping（码点安全 snippet）
  ↓ POST /api/v1/search/semantic → Frontend Sources
```

分层：`SearchController`（薄）→ `RetrievalService`（编排，不碰 Milvus SDK）→ `EmbeddingService` + `VectorRepository`（基础设施边界）。详细设计见 `docs/architecture/retrieval-pipeline.md`。

## 2. Query Model

`POST /api/v1/search/semantic` 请求 `{query, topK?}`。不暴露 nProbe / metricType / indexParams 等基础设施参数。校验：空白 → 400 `INVALID_REQUEST`；超长（`ai.retrieval.max-query-length=512`）→ 400；topK 非法值夹取到 [1, max-top-k=20]，不报错。

## 3. Retrieval Pipeline

`RetrievalService.retrieve()` 六步编排；无匹配返回空列表（200），"没有知识匹配"不是服务器错误。计时结构化日志（queryLen 而非 query 内容、embeddingMs、searchMs、totalMs）。

## 4. Milvus Search

复用 Phase 3 单例惰性 client（无 per-search 建连）。`search(collection, vaultId, queryVector, topK)`：vaultId 过滤在**查询表达式内**完成（服务端计算的根路径 SHA-256，非用户输入，免疫注入）。

## 5. Filtering

- Empty Result：`results: []`。
- Score Threshold：`ai.retrieval.score-threshold` 默认 0（关闭）。实测相关查询 COSINE 分布 0.65~0.78（bge-m3 + Demo Vault），样本过小不足以定阈值——待评估集扩充后设定。

## 6. Deduplication

两级：① 精确重复内容剔除（overlap 产生的完全相同 Chunk）；② 同文档多样性上限 `max-per-document=2`（chunk 相关性 + 文档多样性，不做"每文档只留一条"）。

## 7. Source Mapping

`Source{title, path, heading, snippet, score, documentId, chunkIndex}`：全部来自向量元数据，**零 N+1 补查**；路径 Vault-relative，绝不泄露本机绝对路径；snippet 折叠空白 + 按码点截断（不破坏中文/emoji）。`documentId` 即 noteId（= 相对路径），前端点击 Source 直接复用现有笔记查看器。

## 8. Frontend

SearchView 新增「全文 / 语义」模式切换：语义模式调用真实 API，四态完整（Loading / Empty「没有找到相关知识」/ Error「知识库检索暂时不可用」+ console 详情 / Success），结果展示 title / path / heading / snippet / score，点击定位到笔记（复用 `openNote`）。不向用户暴露任何向量 / Milvus 技术参数。顺带清理了 SearchView 中重复定义的 CSS 块（零行为变化）。

## 9. Evaluation Dataset

`tests/evaluation/retrieval/queries.json`：10 条查询覆盖 Java / JVM / AI / RAG（含双文档期望用例），预期文档对应 Demo Vault 真实路径。

## 10. Recall@5

条件测试 `Phase4RetrievalEvaluationTest`（真实 Ollama bge-m3 + InMemory 向量库，`-Dollama.it=1` 启用）：

```text
Recall@5 = 1.0（10/10 全命中，top1 全部正确）
16 Vectors；指标定义见 docs/architecture/retrieval-evaluation.md
```

## 11. Tests

新增：`RetrievalServiceTest`（12：校验/正常/空结果/阈值/Vault 隔离/多样性去重/精确去重/Source 映射/码点 snippet/topK 夹取/Embedding 失败/维度不符）、控制器集成 2 例（400/404）、评估测试 1 例；存量测试全部适配 vaultId 契约。

| 项 | 结果 |
|---|---|
| Frontend build（vue-tsc + vite） | **PASS** |
| Backend `mvn test` | **PASS**（81：80 执行 + 1 条件跳过） |
| Retrieval / Source / Vault Isolation | **PASS** |
| 评估（真实 Ollama） | **PASS**（Recall@5 = 1.0） |

## 12. Performance

批处理与连接复用沿用 Phase 3；检索路径无 N+1；日志记录 embeddingMs / searchMs / totalMs。语义检索本身 O(embedding 时延 + HNSW 搜索)，Demo 规模秒级。

## 13. Security

- Query 长度上限（防恶意超长）；topK 夹取（防超大 TopK）。
- vaultId = 服务端 SHA-256，用户不可触达；Milvus 表达式统一走 `quote()` 转义。
- Source 只含 Vault-relative 路径；错误信息不泄露堆栈与内部细节。

## 14. Known Limitations

- **Milvus 实机仍未验证**（本机无 Docker 镜像）：vaultId 过滤的 Milvus 表达式经实机 IT 验证（`MILVUS_IT=1`），InMemory 实现已覆盖同语义。
- 换 Vault 后若不重新索引，检索按 vaultId 边界返回空结果（旧行为可能泄漏旧 Vault 数据，本阶段已修复）。
- Source 点击仅打开笔记，Heading 精细定位（滚动到锚点）未做。
- score-0 候选在阈值关闭时仍会返回（诚实行为）；生产体验如需过滤，调 `score-threshold` 即可。

## 15. Next Phase（Phase 5）

Retrieval → Context Assembly → LLM → RAG Answer → SSE Streaming → Citations。`Source`/`documentId`/`chunkIndex` 已为 Citation 预埋。

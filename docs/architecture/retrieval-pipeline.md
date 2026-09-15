# Retrieval Pipeline（Phase 4）

> 检索链路：`Query → Validation → Query Embedding → Vector Search → Candidates → Filter → Dedup → Rank → Source Mapping → Response`。
> 本阶段**不含** LLM Answer / Prompt / Reranker / Hybrid Search——先把"找对知识"做稳，Retrieval 与 Answer 解耦（见 §10）。

## 完整链路与联系描述

```text
User Query
   ↓ ①Validation（非空、长度上限、topK 夹取）
Query Embedding（与索引阶段同一模型）
   ↓ ②EmbeddingService.embed(List.of(query)) → 单条 Dense Vector
Vector Search（Milvus / InMemory，COSINE）
   ↓ ③vaultId 过滤在查询表达式内完成，TopK' = topK × 3 超采样
Candidate Chunks
   ↓ ④Score Threshold（默认关闭，可配置）
   ↓ ⑤Dedup（同文档多样性上限）
   ↓ ⑥Rank（仅 score DESC，无 Reranker）
   ↓ ⑦Source Mapping（chunk 元数据 → 用户可读 Source + snippet）
Retrieval Response
   ↓
Frontend Sources（title / path / heading / snippet / score）
```

### 1. 用户问题为什么要向量化

用户的 Query 是自然语言（"HashMap 为什么需要 resize？"），而索引里存的是 Chunk 的语义向量。检索的本质是**在向量空间里找语义最近的 Chunk**，所以必须把 Query 投射到与 Chunk 相同的向量空间——否则无从比较。全文检索（现有 `GET /api/v1/search`）只能匹配字面词，向量化后才能命中"resize"与"扩容"这类同义表达。

### 2. 为什么 Query Embedding 必须与 Document Embedding 兼容

两个向量只有在**同一个模型产生的同一向量空间**里，距离才有语义。索引阶段的 Chunk 是 bge-m3 编码的；如果 Query 用另一个模型编码，两者的每一维含义都不同，算出来的"相似度"是噪声。因此 `RetrievalService` 复用同一个 `EmbeddingService` 实例与同一个 `ai.ollama.embedding-model` 配置，从机制上杜绝 A/B 模型混用；维度校验（§6）是第二道防线。

### 3. 为什么 Query Vector 可以和 Milvus 中的 Chunk Vector 比较

因为两者同空间同维度（1024），且 metric 统一为 COSINE：COSINE 只关心方向不关心模长，适合衡量"语义相近程度"，且与 bge-m3 的训练目标一致。Milvus 内部以 HNSW 索引做近似最近邻搜索，返回的就是与 Query Vector 夹角最小的 Chunk 集合。

### 4. 为什么 Vector Search 返回的是 Chunk 而不是 Markdown 文件

一篇笔记可能覆盖多个主题，整篇作为检索单元粒度太粗——命中的往往只是文中一节。Chunk 才是语义完整的最小单元：以 Chunk 命中可以精确定位到"HashMap.md 的『扩容机制』一节"，这是 Source 定位（标题 → 路径 → Heading）的基础。文件级信息通过 Chunk 携带的元数据（documentId / relativePath / title）还原，不需要单独查文件。

### 5. 为什么必须保存 Metadata

向量本身只是一个浮点数组，**无法还原出它来自哪个文件哪一节**。每个 Chunk 向量旁必须冗余保存 `documentId / relativePath / title / headingPath / chunkIndex / content / contentHash / vaultId`，检索结果才能：(a) 直接构建用户可读的 Source（零额外查询，避免 N+1）；(b) 支撑增量索引的 hash 对比与失效清理；(c) 按 vaultId 划定检索边界。**向量与元数据都不是 Source of Truth**——Markdown 文件才是，向量库可随时全量重建。

### 6. 为什么需要 TopK

向量空间里"相似"是相对的，没有天然截止线。TopK 限定召回数量：太小漏掉相关内容，太大把弱相关甚至无关内容塞给调用方（未来则是塞进 LLM 上下文，造成污染与超限）。默认值 `ai.retrieval.default-top-k=5`、上限 `max-top-k=20`（防止客户端传 100000 打爆内存）；实际超采样为 `topK × 3`（≤ max-top-k），为去重损失留余量，最终仍裁回 topK。

### 7. 为什么需要去重

同一篇笔记的相邻 Chunk 往往高度相似（尤其 overlap 存在时），不做去重的话 Top5 可能被同一文档霸屏，用户看到的是"同一篇笔记连续 5 条雷同 Source"。但**不能**简单每文档只留 1 条——一篇长笔记的两个不同小节可能各自命中，都值得展示。MVP 策略：按 documentId 做多样性上限（`max-per-document=2`，可配置），保留 chunk-level 相关性 + document-level 多样性。

### 8. 为什么需要 Source Mapping

`SearchResultRecord`（chunkId / score / 元数据）是**面向机器的内部结果**；用户界面需要的是**面向人的 Source**：标题、笔记路径、所在标题、可读摘要、分数。两者解耦后，前端永远不需要知道 Milvus 的存在——未来换向量库、加 Reranker，Source 形状不变。同时 Source 只含 Vault-relative 路径，绝不泄露本机绝对路径（Local-First 安全边界）。

### 9. Similarity Score 的含义是什么

COSINE 相似度，取值 [-1, 1]，越接近 1 语义越近。它**不是概率、不是置信度**：0.86 只说明方向很接近，不保证"86% 正确"。因此 score 原样透传给前端用于排序展示，但不参与任何"正确性"承诺；Score Threshold（`ai.retrieval.score-threshold`）**默认关闭（0）**——COSINE 分布随模型而异，阈值必须来自实测（见 retrieval-evaluation.md 的 Recall@5 评估），拍脑袋设阈值只会静默丢结果。

### 10. Retrieval 和最终 LLM Answer 为什么应该解耦

把"找对知识"和"答对问题"分成两个可独立验证的阶段：检索质量可以用固定评估集（queries.json + Recall@K）**确定性回归**，而 LLM 回答质量只能抽样评估。如果检索不稳定，Answer 再聪明也是无源之水。解耦还让本阶段 API（`POST /api/v1/search/semantic`）可以直接服务 UI 语义搜索，而不必经过 LLM——Phase 5 的 RAG Answer 只是再叠加 Context Assembly 与 Citation 两步。

## 实现映射

| 链路环节 | 组件 | 说明 |
|---|---|---|
| Validation | `RetrievalService` | 空白/超长 → `INVALID_REQUEST`(400)；topK 夹取 |
| Query Embedding | `EmbeddingService`（Ollama 实现） | 失败 → `EMBEDDING_ERROR`(503)；维度不符 → `EMBEDDING_DIMENSION_MISMATCH`(500) |
| Vector Search | `VectorRepository.search(collection, vaultId, vector, topK)` | Milvus 表达式内过滤 vaultId（服务端计算的 SHA-256，无注入面） |
| Filter/Dedup/Rank | `RetrievalService` | 纯内存操作，无 N+1（Source 全部来自 Milvus 元数据） |
| Source Mapping | `RetrievalService` 内私有方法 | snippet 折叠空白 + 码点安全截断（不破坏中文/emoji） |
| API | `POST /api/v1/search/semantic` | Controller 只做 Request → Service → Response |

## Vault 隔离

单活跃 Vault 架构下，`vaultId = SHA-256(Vault 根绝对路径)`（`VaultRepository.vaultId()`），写入每个 Chunk 元数据。检索与删除/哈希加载全部按 vaultId 过滤：切换 Vault 而未重建索引时，旧 Vault 的向量**不会被返回也不会被误删**。vaultId 是服务器计算的哈希，用户输入无法触碰，天然免疫 Filter Injection。

## 已知边界（诚实声明）

- 未实现：Reranker、Hybrid Search、查询改写、多路召回——接口已按 Retriever 可扩展性留位（`VectorRepository.search` 与 Service 分层不变即可插入）。
- Score Threshold 默认关闭，待评估集跑出实测分布后再定值。

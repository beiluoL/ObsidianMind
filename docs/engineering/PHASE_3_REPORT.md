# ObsidianMind Phase 3 Report — Knowledge Pipeline

> 日期：2026-09-15。范围：Markdown → Chunk → Embedding → Milvus（索引侧）。不含 RAG Answer / Agent / MCP / Hybrid / Rerank。

## 1. Architecture

新增组件（全部单一职责，`KnowledgeIndexService` 只做编排）：

| 组件 | 位置 | 职责 |
| --- | --- | --- |
| `KnowledgeIndexService` | service/ | 编排 + 增量状态机（INDEXED/UPDATED/SKIPPED/DELETED/FAILED） |
| `Chunker`（v2 重写） | service/ | heading 边界 / headingPath / offset / 代码块表格保护 / size+overlap 配置化 |
| `OllamaEmbeddingService`（加固） | service/impl/ | 批量 /api/embed、超时、响应校验（数量/维度/空向量）、重试 1 次 |
| `MilvusVectorStore`（新增） | repository/ | Milvus SDK 2.4.5；ensureCollection（HNSW+COSINE）/ upsert（文档级 replace）/ delete / query hash / search / count；**client 惰性建连**（未启动不阻塞应用） |
| `InMemoryVectorStore`（新增） | repository/ | 同契约内存实现（测试/Demo 用，非 Spring Bean） |
| `IndexController` 扩展 | controller/ | `POST /api/v1/index/run`（同步返回计数）+ 保留 Phase 1 异步端点 |
| SettingsView 索引区 | frontend/ | 「立即同步知识库」按钮：Loading / 错误 / 计数结果三态 |

## 2. Implemented Components

见上表；另含 `domain/Document`、`domain/Chunk` 重构（含 contentHash），`util/Hashes.sha256Hex`，`AiProperties` 增加 chunk/embedding 配置，`MilvusProperties` 增加 collection/vectorDimension，新增异常 `EmbeddingException` / `VectorStoreException` / `ConfigurationException`（均入统一错误信封）。

## 3. Data Flow

```text
Vault（显式连接）→ Scan（元数据）→ Parse（Document）→ contentHash 对比（增量分流）
  → Chunker → List<Chunk> → 批量 Embedding（bge-m3，1024 维）→ Milvus upsert/delete
```

完整"为什么"推导：`docs/architecture/knowledge-pipeline.md`（10 问逐条回答）。

## 4. Document Model

`id = relativePath = 笔记 id`（项目不变量，检索结果可直接跳转笔记）；contentHash = SHA-256(raw) 为增量判定依据；createdAt 未采集（避免拍脑袋字段），保留 modifiedAt。多 Vault 时升级 vaultId 复合键（已文档化）。

## 5. Chunk Strategy

heading 边界（层级栈拼 headingPath）> 代码块围栏 > 表格 > 段落 > 行；超限段落按行切，超限代码块/表格整块独立；行级 overlap；`content.substring(start,end) == chunk.content` 不变量有测试保障。参数：800/100 字符（`AI_CHUNK_SIZE`/`AI_CHUNK_OVERLAP`）。

## 6. Embedding Model

默认 `bge-m3`（本机 Ollama 实测 1024 维，8192 上下文）；批量 batch-size=16；超时 30s；失败重试 1 次；响应校验（数量/维度一致/空向量）；运行时维度与 `MILVUS_VECTOR_DIMENSION` 一致性校验（不符抛 EMBEDDING_ERROR）。换模型必须同步配置并清空 Collection（已文档化）。

## 7. Milvus Collection

`obsidianmind_chunks`（固定名，惰性创建）；字段：id(PK)/vector/documentId/relativePath/title/headingPath/chunkIndex/content/contentHash；索引 HNSW(M16,ef200) + COSINE（依据：embedding 未归一化 + 文本相似度语义）；upsert = delete-by-documentId + insert（防重切块后幽灵 Chunk）。

## 8. Incremental Indexing

hash 对比的唯一事实源 = Milvus 元数据 query（无本地缓存第二状态源）；五个状态全有测试；删除文件 → 按 documentId 批量清理向量。

## 9. Error Handling

per-file 错误精确归属（DOCUMENT_PARSE_ERROR / CHUNK_ERROR / EMBEDDING_ERROR / VECTOR_STORE_ERROR），批失败按 Chunk 区间归属；无"(batch)"式无主错误；Embedding/Milvus 错误 → HTTP 503，配置矛盾 → 500，均入统一信封。

## 10. Security

路径全程 Vault-relative（VaultPaths 防逃逸）；Milvus expr 注入转义（有测试）；笔记内容只发往本机 Ollama；无 Secret 入库；日志不输出正文/向量。

## 11. Tests

后端 `mvn test`：**66 个（65 执行 + 1 条件跳过）全绿**。新增：
- `ChunkerTest`（9）：边界/代码块/表格/overlap/offset 不变量/配置校验
- `KnowledgeIndexServiceTest`（7）：INDEXED→SKIPPED→UPDATED→DELETED→FAILED 全状态机 + 维度不符 + 畸形 Markdown 降级 + 空文档
- `OllamaEmbeddingServiceTest`（7）：Mock HTTP 契约（成功/数量/维度/空向量/重试/模型错误）
- `MilvusVectorStoreTest`（2）：expr 转义 + 维度校验；`MilvusVectorStoreIT`（MILVUS_IT=1 实机往返）
- `Phase3PipelineVerificationTest`：真实 Ollama 端到端（默认跳过，`-Dollama.it=1` 启用）

前端 `npm run build`（vue-tsc）0 错误。

## 12. Performance

批量 Embedding（16/请求）；Milvus 单例 client 惰性建连；hash 相同文档零嵌入开销（SKIPPED 不读解析/切块/网络）；未做并发嵌入（本地 Ollama 资源约束，按 Skill 08 约定）。Demo 实测 8 文件全链路 ≈7s（含模型加载）。

## 13. Known Limitations

1. **Milvus 实机未运行**（本机无 docker 镜像）：实机往返测试 `MilvusVectorStoreIT` 已写好，`MILVUS_IT=1` + `docker compose -f infrastructure/milvus/docker-compose.yml up -d` 后执行；当前向量数以内存实现 + 契约测试保障，SDK API 细节（query/search 包装器）未经实机编译运行验证，起 Milvus 后需先跑该 IT。
2. **Embedding 默认模型改为 bge-m3**（原配置 qwen3-embedding 无 :latest tag 可能解析失败；bge-m3 实测可用且同为 1024 维）。
3. hash 对比需读取全文计算 SHA-256（mtime 未做初筛）——个人 Vault 规模可接受。
4. `loadDocumentHashes` 全量拉取，十万级 Chunk 以上需换增量拉取。
5. 异步端点（POST /api/v1/index）语义升级为真实管线：无 Milvus 时终态为 FAILED（原为"仅统计"）；集成测试已按新契约更新。
6. Surefire 转发 `-Dollama.it` 有无害 WARNING；`@TestInstance(PER_CLASS)` + `@BeforeAll` assumption 失败在 surefire 中显示为 0 条测试（已改 @BeforeEach 规避）。

## 14. Next Phase

Phase 4：Query → Embedding → Vector Search（Milvus COSINE TopK）→ Sources（relativePath/headingPath/chunkIndex 已预埋）。接口无改动需求：`VectorRepository.search` 已就绪。

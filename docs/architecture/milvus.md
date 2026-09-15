# Milvus 向量存储设计说明

> Phase 3。配套：`knowledge-pipeline.md`、`embedding.md`。执行细则见 `.agents/skills/02/04/08`。

## 一、版本与 SDK

| 项 | 值 | 来源 |
| --- | --- | --- |
| Milvus Server | **v2.4.15**（standalone） | `infrastructure/milvus/docker-compose.yml` 镜像 tag |
| Java SDK | `io.milvus:milvus-sdk-java:2.4.5` | 与 2.4.x 服务端同大版本，gRPC 协议兼容 |
| 连接 | `MILVUS_HOST:19530`（gRPC，非 9091 HTTP） | `application.yml`，环境变量可覆盖 |

Client 策略：**单例 `MilvusServiceClient`**（`MilvusVectorStore` 内持有，随应用生命周期复用），绝不每文件/每请求建连。连接失败不阻塞应用启动——只有真正执行索引/检索操作时才抛 `MilvusUnavailableException`（503）。健康探测沿用 `HealthService` 的 TCP socket 探测（轻量，不依赖 SDK 会话）。

## 二、Collection 设计

**名称**：`obsidianmind_chunks`（配置 `milvus.collection`）。固定名称、启动不自动创建——由索引流程的 `ensureCollection` 惰性创建并复用，**绝不**每次启动建随机 Collection。

**Schema（v2.4）**：

| 字段 | 类型 | 角色 |
| --- | --- | --- |
| `id` | VarChar(512), primary key, autoID=false | Chunk id：`{documentId}#c{n}`（确定性可重放） |
| `vector` | FloatVector(dim=1024) | Embedding 输出 |
| `documentId` | VarChar(512) | = Vault 相对路径（= 笔记 id） |
| `relativePath` | VarChar(512) | 同 documentId，检索结果直读（保留冗余，Sources 用） |
| `title` | VarChar(512) | 文档标题 |
| `headingPath` | VarChar(1024) | 标题路径，`JVM / 内存区域 / Heap` |
| `chunkIndex` | Int64 | 文档内序号 |
| `content` | VarChar(65535) | Chunk 原文（MVP 权衡：检索结果直接展示，免回读文件；超 64KB 的 Chunk 被 Chunker size 上限自然排除） |
| `contentHash` | VarChar(64) | 文档级 SHA-256（同文档所有 Chunk 相同），**增量索引判定依据** |

**为什么标量字段不过滤（无 partition key / 无 index）**：个人 Vault 数据量（万级 Chunk 以下）标量过滤是内存级操作，MVP 不加分区；数据量上来后再把 `documentId` 设为 partition key。

## 三、索引选择：HNSW + COSINE（为什么）

```yaml
Index:  HNSW  { M: 16, efConstruction: 200 }
Metric: COSINE
```

- **Metric = COSINE**：Ollama embedding 模型（bge-m3 / qwen3 系列）输出的向量未做长度归一化承诺，COSINE 对向量模长不敏感、只看方向，是文本 embedding 检索的事实标准；L2 在未归一化向量上会把"模长大"误判为"距离远"，IP 则要求归一化。**结论必须有依据，不默认**——依据就是"模型输出未归一化 + 文本相似度语义"。
- **Index = HNSW**：当前规模（<10 万向量）FLAT 精确检索其实够用，但 HNSW 是 Milvus 各规模下最稳的通用选择，且内存占用在此规模完全可接受（1024 维 × 10 万 × ~30B ≈ 3GB 上限，个人 Vault 远低于此）。**不选 GPU/CAGRA/IVF_PQ 等"高级"方案**——数据量远未到，徒增调参负担。
- **后续调整路径**：>50 万向量或内存吃紧 → 评估 IVF_SQ8 / DiskANN；需要极致召回 → FLAT 全量对比基准。调整 = drop index → create index → load，Collection 数据不动。
- **加载**：建索引后必须 `loadCollection` 才可 search；upsert/delete 后 Milvus 自动维护，无需手动 flush（v2.4 默认最终一致窗口可接受）。

## 四、VectorRepository 契约（Vector Store 抽象）

```java
void ensureCollection(String collection, int dimension);      // 惰性建集合 + 建索引 + load
void upsert(String collection, List<Chunk>, List<float[]>);   // 先 deleteByDocument 再 insert（幂等）
void deleteByDocuments(String collection, List<String> ids);  // expr: documentId in [...]
Map<String,String> loadDocumentHashes(String collection);     // documentId → contentHash（增量对比）
List<SearchResultRecord> search(String collection, float[] q, int topK);
int count(String collection);                                 // 验证与可观测
```

- **upsert = delete + insert**：Milvus v2.4 的 upsert 语义按主键覆盖，但"文档重切块后 Chunk 数变少"会留下幽灵旧 Chunk（旧 id 不再被新写入覆盖），所以文档级 replace 必须显式 delete-by-documentId 再插入。
- **`loadDocumentHashes`**：`query(expr="id != \"\"")` 拉全量 `documentId/contentHash` 后按文档归约。万级规模毫秒~百毫秒级；未来量大再换 `documentId` 分区增量拉取。
- **测试策略**：Milvus 实机未运行时（本机无 docker 镜像），`MilvusVectorStore` 以**契约测试**（Mock SDK + 纯函数校验：expr 转义、Schema 字段映射、维度校验）覆盖；另提供 `InMemoryVectorStore`（实现同一接口，仅测试使用，非 Spring Bean）支撑增量索引状态机的完整行为测试与 Demo 链路验证。实机 Milvus 起来后跑同一条管线即为集成测试（`MILVUS_IT=1` 条件启用）。

## 五、安全

- 连接信息（host/port）配置化；Milvus 在本机 Docker 内，**不暴露公网**。
- 写入的 `content` 是用户笔记片段——Milvus 与 Vault 同机，属 Local-First 边界内，**禁止**把 content/向量发送到任何远程服务。
- 所有 documentId 来自 Vault 相对路径（已过 `VaultPaths` 校验），拼接 expr 时做 JSON 字符串转义，防表达式注入。

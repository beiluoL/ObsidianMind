# ObsidianMind Knowledge Pipeline（知识管线）

> Phase 3 核心设计文档。读者：维护者与面试官。目标：读完本文能完整讲清「一个 Markdown 文件如何变成 Milvus 里可检索的向量」以及每一步**为什么必须存在**。
>
> 执行细则见 `.agents/skills/04-ai-rag-engineering/SKILL.md`；Embedding 细节见 `embedding.md`，Milvus 细节见 `milvus.md`。

## 一、完整链路

```text
Obsidian Vault（用户显式连接的目录）
   ↓ ① Markdown Scanner（VaultRepository.scan：只收集元数据，不读正文）
Markdown 文件清单（Vault 相对路径 + 大小 + mtime）
   ↓ ② Markdown Parser（MarkdownParser：纯函数解析）
Document（结构化：frontmatter/tags/headings/wikilinks/正文/contentHash）
   ↓ ③ Incremental Check（contentHash 对比存量索引）
   ↓ ④ Chunker（尊重 heading / 代码块 / 表格边界的切块）
List<Chunk>（chunkIndex / headingPath / startOffset / endOffset / contentHash）
   ↓ ⑤ Embedding（Ollama /api/embed，批量）
List<float[1024]> Dense Vectors
   ↓ ⑥ Milvus Vector Store（ensureCollection → upsert / delete）
obsidianmind_chunks Collection（向量 + 元数据）
```

## 二、逐段"为什么"（联系描述）

**① Scanner 为什么负责发现 Markdown 文件，而不是 Parser 直接读目录？**
因为"发现"与"解析"是两种职责、两种失败模式。发现阶段要处理文件系统问题（目录不存在、权限、符号链接、要忽略的 `.obsidian`/`node_modules`），并且为了性能**只收集元数据**（路径、大小、mtime），不读正文——一个上万文件的 Vault，全量读正文是灾难。Parser 则是纯文本处理，不应该知道文件系统存在。职责分离让两者可以独立测试（Scanner 用临时目录测，Parser 用字符串测），也让"扫描 10000 个文件但只解析其中 5 个变化的"这种增量策略成为可能。

**② Parser 为什么负责把原始 Markdown 转成结构化 Document？**
因为 Markdown 是给"人 + 渲染器"看的格式，frontmatter 的 YAML、`[[WikiLink]]`、`# 标题`、正文混在一起，程序无法直接可靠地处理。Parser 把它翻译成结构化模型：`frontmatter`（元数据）、`tags`（frontmatter 与行内标签合并去重）、`wikiLinks`（知识图谱的边）、`title`、正文。同时 Parser 是**纯函数**：输入字符串、输出 Document，无副作用、不碰文件系统——这使它成为整个管线里最容易测试、最值得信任的一环。解析失败的文件降级为纯文本处理而不是丢弃（畸形 frontmatter 也有正文价值）。

**③ Document 为什么不能直接进入 Vector Store？**
三个原因。第一，**长度**：Embedding 模型有输入上限（bge-m3 为 8192 token），一篇长笔记根本放不下。第二，**语义稀释**：一篇覆盖 JVM 内存结构 + GC + 并发的长文档，整体向量是多主题的"平均值"，用户问其中某一个主题时，整篇文档的相似度反而被稀释——检索精度必然差。第三，**引用粒度**：即使检索命中了整篇文档，用户也无法知道答案来自文档的哪一段。所以必须先切块，让"检索单元"与"语义单元"对齐。

**④ 为什么需要 Chunk（切块）？**
切块把文档按语义边界（标题层级、段落）拆成小而完整的单元，每块表达一个主题，每块携带定位信息（headingPath、chunkIndex、offset）。切块质量直接决定检索质量：按标题切，保证"## Heap"下的内容不会和"## Stack"混在一块；保护代码块和表格，保证结构化内容不被腰斩；控制 chunk size 与 overlap，兼顾"块够小够聚焦"和"上下文不丢失"。

**⑤ Chunk 为什么需要 Embedding？**
计算机无法直接计算"两段中文意思接不接近"，只能计算数字。Embedding 模型把文本映射为稠密向量（本项目的 bge-m3 / qwen3-embedding 输出 1024 维浮点向量），语义相近的文本在向量空间中距离相近。必须区分四个层次：**Text**（人类可读文本）→ **Token**（Tokenizer 切出的符号）→ **Token ID**（词表中的整数）→ **Embedding**（最终输出的浮点向量）。送进 API 的是整段 Text，Tokenizer 和 Token ID 是模型内部实现，调用方永远不接触。详见 `embedding.md`。

**⑥ Embedding 为什么能够进入 Vector Store？**
因为 Embedding 的输出是**定长浮点向量**，而 Milvus 的核心能力就是"存储定长向量 + 按相似度快速找最近的 K 个"。写入时向量与它的元数据（来自哪个文件、哪个标题、第几块）一起存进 Collection；查询时查询向量进来，Milvus 用向量索引（HNSW）在毫秒级返回 TopK 近邻。向量之所以"可检索"，前提是维度一致（同一模型）、度量一致（COSINE）、元数据齐全（能还原来源）。

**⑦ Milvus 保存什么？**
两类字段：**向量字段**（`vector`，FloatVector 1024 维）+ **标量元数据字段**（`id` 主键、`documentId`、`relativePath`、`title`、`headingPath`、`chunkIndex`、`content`、`contentHash`）。注意 **content 原文也存了**——这让检索结果可以直接展示片段而不必回读文件（MVP 权衡，见 ⑧⑨）。Milvus 里**没有任何东西是独有的**：所有数据都可由 Vault 重新生成。

**⑧ 文件原文和 Vector 的关系是什么？**
**一对多的派生关系**。一个 Markdown 文件 → 一个 Document → N 个 Chunk → N 个 Vector。Vector 是 Chunk 内容的数学投影，Chunk 是文件片段的引用。文件改动（hash 变化）→ 它的所有旧 Chunk/Vector 作废 → 重新解析、切块、嵌入、upsert；文件删除 → 对应 Vector 全部清除。原文件永远是"因"，Vector 永远是"果"。

**⑨ 如何从 Vector 找回原始 Source？**
写入时每个向量都带全量元数据（relativePath / title / headingPath / chunkIndex / startOffset）。检索返回命中向量时，这些标量字段一起返回：`relativePath` 定位到文件（= 笔记 id，可直接打开笔记）、`headingPath` 定位到章节、`chunkIndex/startOffset` 定位到精确位置。这就是 Phase 7 Sources/Citation 的数据基础——检索层现在就把"来源"设计进去了，而不是事后补。

**⑩ 为什么 Markdown 仍然是 Source of Truth？**
因为 Milvus 中的每一个字段都是 Markdown 的**确定性派生物**（同一个文件 + 同一个模型 → 同样的向量），且只有 Markdown 是用户可直接编辑、可直接阅读、可脱离本应用存在的形态。索引可能滞后、可能损坏、可能被重建——文件不会。所以：冲突时以文件为准；索引可随时全量重建（删除 Collection → 重新跑一遍管线）；**绝不**把 Milvus 当作数据的唯一存放处。这也是"删除了 Vault 文件但 Milvus 里还有向量"必须被视为缺陷的原因（增量索引的 DELETE 分支负责清理）。

## 三、Document Model（身份与哈希）

字段（`domain/Document`）：

| 字段 | 说明 |
| --- | --- |
| `id` = `noteId` = **Vault 相对路径** | 文档唯一身份。与笔记读写、搜索、知识树共用同一 id 体系（项目不变量：Note id = Vault 相对路径） |
| `title` | frontmatter title → 首个 H1 → 文件名兜底 |
| `content` | 正文（不含 frontmatter） |
| `frontmatter` / `tags` / `headings` / `wikiLinks` | Parser 产物 |
| `createdAt` / `updatedAt` | 文件系统 mtime（首次扫描时间 vs 最后修改时间） |
| `contentHash` | SHA-256(raw)。**增量索引的判定依据** |

**为什么 documentId 用相对路径而不是随机 UUID？** 相对路径在单个 Vault 内天然唯一、稳定（文件不挪位置就不变）、可读（日志/调试直接能看懂）、且与既有笔记 API 的 id 完全一致——检索结果可以直接跳转笔记，无需一层映射。**代价**是文件重命名/移动 = id 变化 = 旧索引作废、新索引重建（增量索引会把它处理成 DELETE + INDEX，语义正确，只是多一次嵌入开销）。MVP 阶段这个代价远小于引入 uuid↔path 映射表的复杂度。

**为什么暂时没有 vaultId？** 当前架构是单连接 Vault（全局唯一），加 vaultId 是没有消费者的字段（YAGNI）。未来支持多 Vault 时，将 id 升级为 `vaultId:relativePath` 复合键，Milvus 元数据同步加字段即可——接口已按此方向预留（`VectorRepository` 方法都带 collection 参数）。

**为什么禁止绝对路径出后端？** 绝对路径泄露机器目录结构（`/Users/beiluo/...`），且跨机器不可迁移。对外 API 与向量元数据一律存 Vault 相对路径；`VaultPaths` 负责把外部传入的相对路径约束在 Vault 根内（防 `../` 逃逸）。

**contentHash 为什么是 SHA-256(raw)？** raw（含 frontmatter 的完整原文）是最保守的变化判定：任何字节变化都会触发重索引，宁可多索引不可漏索引。mtime 只能做初筛（mtime 变了内容未必变），hash 才是最终事实。hash 同时写入 Milvus 元数据，增量对比时无需回读本地缓存。

## 四、Chunk 策略（详见 Chunker 实现）

- **边界优先级**：heading（1-6 级）> 代码块围栏 > 表格 > 段落 > 行。代码块 ``` 围栏内与 Markdown 表格块永不从中间切开。
- **headingPath**：维护标题层级栈，Chunk 记录完整路径（如 `JVM / 内存区域 / Heap`），Sources 定位与检索过滤都依赖它。
- **chunk size / overlap**：`ai.chunk.size=800` 字符、`ai.chunk.overlap=100` 字符（application.yml 可覆盖，环境变量 `AI_CHUNK_SIZE` / `AI_CHUNK_OVERLAP`）。800 字符 ≈ 400~500 token（中文），对 1024 维 embedding 模型是信息密度与召回粒度的平衡点；overlap 取 size 的 ~12%，在段落/行边界处衔接，避免主题句被切在缝里。**为什么不用 token 计数**：MVP 用字符数近似，避免引入 tokenizer 依赖；中文场景字符数与 token 数比例稳定，误差可接受。
- **offset**：`startOffset`/`endOffset` 是 Chunk 在 Document 正文（body）中的字符区间，精确回源用。
- **空文档**（body 为空）→ 0 个 Chunk，属于正常状态（SKIPPED 类），不是错误。

## 五、增量索引（Incremental Indexing）

状态机（按文件粒度）：

| 状态 | 判定 | 动作 |
| --- | --- | --- |
| INDEXED | 存量索引中无此 documentId | parse → chunk → embed → upsert |
| UPDATED | 存量有此 id 但 contentHash 不同 | parse → chunk → embed → 删旧 upsert 新 |
| SKIPPED | 存量有此 id 且 contentHash 相同 | 无操作（hash 对比在 Milvus 元数据上完成，不回读文件正文） |
| DELETED | 存量有此 id 但 Vault 中文件已不存在 | `deleteByDocument` 清理全部向量 |
| FAILED | 单文件 parse/chunk/embed/写入 任一失败 | 记录错误码与信息，不中断其他文件 |

**为什么 hash 对比用 Milvus 而不是本地缓存文件？** Milvus 的 `contentHash` 标量字段 + `query(expr)` 就能拿到 `documentId → contentHash` 映射，这是单一事实源：换机器、清缓存、多人协作都不会失同步；本地缓存文件则会引入第二个状态源（缓存丢了 → 全量重索引只是浪费，缓存脏了 → 该更新的没更新，是正确性问题）。

## 六、错误分类

| 错误码 | 层 | HTTP | 场景 |
| --- | --- | --- | --- |
| `DOCUMENT_PARSE_ERROR` | Parser | 计入 per-file FAILED | 编码异常等（畸形 frontmatter 会降级，不报错） |
| `CHUNK_ERROR` | Chunker | per-file FAILED | 理论上不可达（防御性保留） |
| `EMBEDDING_ERROR` / `OLLAMA_UNAVAILABLE` | Embedding | 503 | Ollama down / 模型缺失 / 超时 / 响应畸形 / 维度不符 |
| `VECTOR_STORE_ERROR` / `MILVUS_UNAVAILABLE` | Milvus | 503 | 连接失败 / 集合操作失败 |
| `CONFIGURATION_ERROR` | 配置 | 500 | 配置非法（如 chunk.overlap ≥ chunk.size） |

## 七、性能与安全约束

- Embedding **批量**调用（`ai.embedding.batch-size=16`），绝不逐 Chunk 一次 HTTP。
- Milvus **单例 client** 复用连接，绝不每文件建连。
- 不做多线程并发 Embedding：本地 Ollama 模型并发会争抢 Mac 的 CPU/GPU 与内存，收益为负。
- 所有路径 Vault-relative + `VaultPaths` 校验；Markdown 内容只发送到本机 Ollama（Local-First 红线）。
- 日志只记 documentId / relativePath / chunkCount / 耗时 / 状态，不输出正文与向量。

## 八、组件职责表（谁做什么、不做什么）

| 组件 | 职责 | 明确不做 |
| --- | --- | --- |
| `VaultRepository`（Scanner） | 发现文件、读原文、路径安全 | 解析、切块 |
| `MarkdownParser` | Markdown → Document 结构 | 文件 IO、向量 |
| `Chunker` | Document → List<Chunk> | 网络调用 |
| `EmbeddingService` / `OllamaEmbeddingService` | 批量 text → float[] | 切块、Milvus |
| `VectorRepository` / `MilvusVectorStore` | Collection 管理、upsert/delete/query/search | 解析、切块、embedding |
| `KnowledgeIndexService` | **编排**上述组件 + 增量状态机 | 任何上述组件的内部实现细节 |

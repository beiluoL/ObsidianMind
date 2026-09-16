# Hybrid Retrieval 架构（Phase 6）

> 混合检索：Vector Search + Keyword Search → RRF → Reranker → Final TopK。
> 实现位于 `com.obsidianmind.retrieval` 包；配置前缀 `retrieval.*`。

---

## 一、完整链路

```text
                 Query（用户问题）
                   │
                   ▼
          RetrievalService（校验 / 产出层规则 / Source 映射）
                   │
                   ▼
            HybridRetriever（模式编排 + 降级）
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
 VectorRetrievalProvider   KeywordRetrievalProvider
        │                     │
        ▼                     ▼
 Embedding → Milvus        BM25（VaultChunkIndex）
 （语义相似度）             （精确词匹配）
        │                     │
        └──── Vector Rank ─┬─ BM25 Rank ────┘
                           ▼
                          RRF（Reciprocal Rank Fusion）
                           ▼
                     Candidate Rank（rrfScore 降序）
                           ▼
                      Reranker（可选，TopN 二次排序）
                           ▼
                      Final Results（TopK）
                           ▼
                 Sources（title/path/heading/snippet/score）
                           ▼
                    Search UI / RAG Context
```

**联系描述（为什么每一环长这样）：**

- **RetrievalService 不认识 Milvus / BM25**。它只做三件事：校验（空白/长度/topK）、产出层规则
  （精确重复剔除、同文档多样性、VECTOR 模式阈值）、Source 形状映射。检索"怎么找"的全部细节
  都被封在 Provider 之后——未来换 Lucene 或云端检索，这一层零改动。
- **HybridRetriever 是编排器不是算法**。两路谁先谁后（串行，默认优先稳定）、谁失败谁顶上
  （降级矩阵）、要不要重排（Reranker 开关），全部集中在这一个类里，降级行为可单测。
- **RAG 不感知模式**。RagAnswerService 只调 `retrieveForRag()`，拿到 Final Sources 就组装
  Context——Phase 5 的 RAG 管线一行没改。

---

## 二、为什么 Vector 和 Keyword 都要（互补性）

### Vector Search（语义相似）

**擅长：**

- 语义相似：「为什么 HashMap 需要扩容」能命中写「负载因子超过 0.75 时 resize」的段落
- 同义表达：换个说法、换种语言描述同一概念
- 自然语言问法：模糊的、口语化的问题
- 概念匹配：问「垃圾回收怎么判断对象死了」能找到「可达性分析」

**不擅长：**

- 精确字符串：`ConcurrentHashMap`、`compareAndSwap` 可能被拆成不知所云的语义片段
- 类名 / 方法名 / 错误码：`NullPointerException`、`HTTP 500`、`MaxGCPauseMillis`
- 版本号 / 专有名词：`JDK 21`、`bge-m3`——embedding 模型未必认识
- 评分语义：COSINE 相似度是「语义距离」，不是「相关性」

### Keyword Search（BM25 词法匹配）

**擅长：**

- 精确命中：`HashMap`、`ConcurrentHashMap`、`HNSW` 这类 token 直接对上
- 稀有词高权重：IDF 让 `Metaspace` 这类只出现在少数文档的词主导排序
- 可解释：为什么命中、命中在哪个字段，一目了然

**不擅长：**

- 同义词与概念转换：「文本转向量」匹配不到只写「Embedding」的文档
- 中文自然语言：需要分词，本项目的 bigram 方案是"真实可用但有局限"（见下）
- 问句改写：「堆和栈的区别」几乎无法命中「JVM 运行时数据区」

### Hybrid：1 + 1 > 2 的机制

两路的**错误分布不同**：向量路输在"精确词"，关键词路输在"改写问法"。RRF 融合的是
**排名**而不是分数——某一路排名靠前的候选，只要在另一路也表现不差，融合后就靠前；
一路的灾难性失误（把正确答案排到 15 名开外）会被另一路的正确排名拉回来。

**实测对照（demo-vault，34 条查询，详见 RETRIEVAL_EVALUATION_V2.md）：**

| Query 类型 | 最佳单路 | 说明 |
| --- | --- | --- |
| `ConcurrentHashMap 线程安全` | Keyword | 精确类名命中 |
| `堆和栈的区别` | （待 Vector 评估补齐） | Keyword 完全失败 |
| `Token 是什么` | 两者皆可 | 简单查询两路都能命中 |

**Keyword Search Limited（诚实声明）：** 本项目无数据库、无 Lucene，中文分词采用
**CJK 二元组（bigram）**方案（`TextTokenizer`）。bigram 是无词典中文检索的标准做法
（Elasticsearch CJK analyzer 同策略），能可靠支持"HashMap 扩容"这类混合查询；
但**没有词典**，无法做同义词归一、无法区分「的地得」。语义改写能力必须由向量路补齐，
这正是 Hybrid 存在的理由，而不是 keyword 实现的缺陷被掩盖。

---

## 三、检索模式与降级矩阵

| 模式 | 行为 | 失败时 |
| --- | --- | --- |
| `VECTOR` | 只走向量路 | 降级 KEYWORD（可观察） |
| `KEYWORD` | 只走 BM25 | 降级 VECTOR（可观察） |
| `HYBRID`（默认） | 两路 + RRF (+Reranker) | 单路失败用另一路；两路全败 → 503 |

**可观察性契约（绝不静默降级）：**

- 响应字段：`requestedMode` / `effectiveMode` / `fallbacks[]` / `rerankerStatus`
- 降级代码：`VECTOR_FALLBACK_TO_KEYWORD`、`KEYWORD_FALLBACK_TO_VECTOR`、`RERANKER_FALLBACK_TO_RRF`
- 每次降级都有 `log.warn`，原因写入 `failureReasons`

**不降级的例外：** 未连接 Vault（`VAULT_NOT_FOUND`）是前置条件失败，两路都会同样失败，
直接抛 404——降级只会掩盖用户没连接 Vault 这个真正的问题。

**RAG 的诚实性增强：** `retrieveForRag()` 在"发生过降级且仍无结果"时抛
`RETRIEVAL_UNAVAILABLE`（503），而不是返回空让 RAG 走"知识库没有相关内容"的拒答——
外部依赖挂了不能被伪装成"你的笔记里没写"。

---

## 四、分数体系（不混淆）

| 通道 | 语义 | 量级 | 缺省 |
| --- | --- | --- | --- |
| `vectorScore` | COSINE 相似度 | 0~1 | NaN（该路未参与） |
| `keywordScore` | Okapi BM25 | 无上界 | NaN |
| `rrfScore` | RRF 融合分 | ~1/(k+rank) | NaN |
| `rerankScore` | Reranker 相关性 | 0~1 | NaN |

- **禁止跨体系相加**：COSINE 0.8 与 BM25 8.0 不可比，直接相加是语义错误（详见 rrf.md）
- `finalScore()`：rerankScore > rrfScore > 唯一路原始分，顺序即优先级
- UI 的 `Source.score` = finalScore；分数体系随模式而异，仅供排序展示
- `scoreThreshold`（COSINE 语义阈值）只在 effectiveMode == VECTOR 时生效

---

## 五、与其他文档的关系

- RRF 公式与参数依据：[rrf.md](./rrf.md)
- Reranker 抽象与 NOT_CONFIGURED 语义：[reranker.md](./reranker.md)
- 检索规则清单（回归必读）：[../engineering/retrieval-rules.md](../engineering/retrieval-rules.md)
- 评估数据与结论：[../engineering/RETRIEVAL_EVALUATION_V2.md](../engineering/RETRIEVAL_EVALUATION_V2.md)

## 六、配置项（retrieval.*）

| 配置 | 环境变量 | 默认 | 说明 |
| --- | --- | --- | --- |
| `retrieval.default-mode` | `RETRIEVAL_DEFAULT_MODE` | HYBRID | 默认检索模式 |
| `retrieval.rrf.k` | `RETRIEVAL_RRF_K` | 60 | RRF 平滑常数 |
| `retrieval.candidates.vector` | `RETRIEVAL_VECTOR_CANDIDATES` | 20 | 向量路候选数 |
| `retrieval.candidates.keyword` | `RETRIEVAL_KEYWORD_CANDIDATES` | 20 | 关键词路候选数 |
| `retrieval.keyword.k1` / `b` | `RETRIEVAL_KEYWORD_K1/B` | 1.5 / 0.75 | BM25 参数 |
| `retrieval.reranker.enabled` | `RETRIEVAL_RERANKER_ENABLED` | false | Reranker 开关 |
| `retrieval.reranker.top-n` | `RETRIEVAL_RERANKER_TOP_N` | 20 | 重排候选数 |
| `retrieval.debug-enabled` | `RETRIEVAL_DEBUG_ENABLED` | false | Debug 端点门禁 |

## 七、Known Limitations

1. 两路串行执行（Mac 本地优先稳定；两路均为毫秒级，无实测瓶颈不做并发）
2. 关键词索引为进程内存态，重启后首次查询重建（个人 Vault 亚秒级）
3. bigram 分词无词典，无同义归一（语义改写靠向量路）
4. 神经 Reranker 未接入（NOT_CONFIGURED 诚实状态）；词法 Reranker 为确定性启发式

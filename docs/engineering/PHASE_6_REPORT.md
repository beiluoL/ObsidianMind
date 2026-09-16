# Phase 6 Report — Hybrid Retrieval（Vector + BM25 → RRF → Reranker）

> 2026-09-16。本阶段把 Phase 4 的"向量一路检索"升级为混合检索管线：向量语义 + 关键词 BM25
> 两路召回 → RRF 融合 → Reranker 重排 → 最终 TopK。三种模式（VECTOR/KEYWORD/HYBRID）可切换、
> 默认 HYBRID，完整降级矩阵全部可观测，RAG 链路保持解耦（RAG 不感知模式）。

## Architecture

```
SearchController / RagAnswerService
  └ RetrievalService（混合检索门面 + 后处理规则 + 降级兜底）
      └ HybridRetriever（编排：mode → 两路 Provider → RRF → Reranker）
          ├ VectorRetrievalProvider   Query Embedding → 向量库（语义路）
          ├ KeywordRetrievalProvider  VaultKeywordIndex BM25（关键词路，内存实现）
          ├ ReciprocalRankFusion      排名级融合（RRF，k=60）
          └ RerankerProvider          默认关闭（NOT_CONFIGURED 诚实呈现）
```

核心决策（详见 docs/architecture/hybrid-retrieval.md 与 docs/engineering/RETRIEVAL_EVALUATION_V2.md）：

- **不引 ES/Lucene**：个人 Vault 量级（18 篇 / 340+ chunks）下内存 BM25 实测 1-2ms/查询，
  足够且零运维；`KeywordRetrievalProvider` 是可替换接口，未来量级上来可换实现。
- **RRF 融合排名而非分数**：cosine 0.8 与 BM25 8.0 量纲不可比，`RRF(d) = Σ 1/(k + rank)`（k=60）。
- **统一候选形 `RetrievalCandidate`**：vectorScore / keywordScore / rrfScore / rerankScore 四通道
  独立记录，缺失通道用 **NaN 而非 0**（0 分是"打过分了但很低"的谎言）；命中类型
  VECTOR/KEYWORD/HYBRID 随融合真实标注。
- **降级矩阵全可观测**：`fallbacks[]` + `failureReasons[]` + `rerankerStatus` 逐项返回并
  `log.warn`（FALLBACK_VECTOR_TO_KEYWORD / FALLBACK_KEYWORD_TO_VECTOR /
  RERANKER_FALLBACK_TO_RRF）；Vault 不存在是前置条件失败 → 不降级、保留 404 契约。
- **RAG 诚实降级**：RAG 不感知模式（`retrieveForRag()` 契约不变，默认 HYBRID）；但降级后仍
  为空 → 抛 `RetrievalUnavailableException`（503），绝不把"依赖挂了"伪装成"没有上下文"。
- **词法 Reranker 实测负收益 → 默认关闭**：确定性 IDF 加权重排在本数据集上 MRR 0.947→0.940，
  神经 Reranker 未配置前 `reranker.enabled=false`，状态诚实标注 NOT_CONFIGURED。

## Files（新增 25 个，重写 3 个）

后端新增（`com.obsidianmind.retrieval` 包 + 配置 + 异常 + parser 抽取）：

- `RetrievalProperties`（`retrieval.*` 独立前缀，不占用 `ai.retrieval.*`）
- `RetrievalMode` / `RetrievalCandidate` / `RetrievalProvider` / `RerankerProvider`
- `HybridRetriever` / `ReciprocalRankFusion` / `LexicalReranker`
- `TextTokenizer`（CJK bigram，共享于关键词索引与 Reranker）
- `VaultKeywordIndex`（Chunk 级倒排 + BM25，k1=1.5 / b=0.75；`ensureFreshCorpus()`
  以 path+size+mtime 指纹失效重建；与向量路共享 MarkdownParser+Chunker+DocumentFactory，chunkId 对齐）
- `VectorRetrievalProvider` / `KeywordRetrievalProvider`（`@Qualifier` 区分双 Provider Bean）
- `parser/DocumentFactory`（从 KnowledgeIndexService 抽取，双路共用建文档）
- `exception/RetrievalUnavailableException`（503，GlobalExceptionHandler 已映射）

后端重写：

- `RetrievalService`（混合门面：`retrieveHybrid` / `retrieve`（Phase 4 兼容）/
  `retrieveForRag`（RAG 契约不变）/ `retrieveWithTrace`；后处理 = 精确去重 + 同文档多样性 +
  仅 VECTOR 模式应用 scoreThreshold + topK 收敛）
- `SearchController`：`POST /api/v1/search`（hybrid）+ `/semantic`（兼容保留）+
  `GET /api/v1/search/config` + `POST /api/v1/search/debug`（`RETRIEVAL_DEBUG_ENABLED=false` 时 400）
- DTO：`HybridSearchRequest` / `HybridSearchResponse` / `RetrievalConfigResponse` /
  `RetrievalDebugResponse`（debug 序列化 NaN→null，JSON 合法）

前端：

- `types/knowledge.ts`：RetrievalMode / HybridSearchResponse / RetrievalConfig / Trace 类型
- `services/searchService.ts`：`hybridSearchService`（search / getConfig / debug）
- `SearchView.vue`：模式选择（**默认混合**）、降级提示条、DEV-only 调试面板
  （Vector/Keyword/RRF/Final 四阶段候选 + fallbacks/failureReasons）
- `SettingsView.vue`：「高级检索」折叠面板（只读实时配置，懒加载）

文档：docs/architecture/{hybrid-retrieval,rrf,reranker}.md、
docs/engineering/{retrieval-rules,RETRIEVAL_EVALUATION_V2}.md、README 与 docs/api/README.md 已同步。

## Evaluation（真实运行，34 条 v2 数据集）

`mvn test -Dtest=Phase6HybridRetrievalEvaluationIT -Dollama.it=1`（bge-m3，18 篇 demo Vault）：

| Mode | Recall@1 | Recall@3 | Recall@5 | MRR | avgMs |
| --- | --- | --- | --- | --- | --- |
| Vector | 31/34 (91.2%) | 33/34 (97.1%) | **34/34 (100%)** | **0.947** | 171 |
| Keyword | 31/34 (91.2%) | 32/34 (94.1%) | 33/34 (97.1%) | 0.934 | **2** |
| **Hybrid（默认）** | 31/34 (91.2%) | 33/34 (97.1%) | **34/34 (100%)** | **0.947** | 193 |
| Hybrid + 词法 Rerank | 31/34 (91.2%) | 32/34 (94.1%) | **34/34 (100%)** | 0.940 | 191 |

最有价值的发现（完整记录于 RETRIEVAL_EVALUATION_V2.md §3）：

- 「堆和栈的区别」Keyword 完全 MISS（同义改写是词法路死穴）→ 向量路存在的理由
- 「Metaspace 方法区」向量路被 bigram「方法」干扰带偏 → **Keyword 路 TOP1 正确**，
  两路错误不重合正是 Hybrid 的价值
- 「RAG 为什么需要 Chunk」被词法 Reranker 拉退（HashMap.md 词面更密压过 RAG.md）→ MRR
  0.947→0.940 的直接原因，Reranker 默认关闭的决策依据

中文检索结论：**PARTIAL（真实可用，有已知局限）**——bigram 对混合/中文查询 Recall@5 33/34，
无词典无同义归一，更复杂改写依赖向量路；未假装完成的部分已明确标注。

## Tests

- `mvn test`：**169 全绿**（Phase 5.5 基线 123 → 169，新增 46 个用例），要点：
  - `TextTokenizerTest` / `ReciprocalRankFusionTest` / `HybridRetrieverTest` /
    `LexicalRerankerTest` / `VectorRetrievalProviderTest` / `KeywordRetrievalProviderTest`
    （纯单测：分词边界、RRF 排序与并列稳定性、降级矩阵逐项断言、NaN 通道、候选不增不减）
  - `SearchControllerTest`（hybrid 入参校验 / config / debug 门禁 400）
  - `Phase6RetrievalRegressionTest`：**确定性回归基线（无需 Ollama，可进 CI）**——
    KEYWORD Recall@5 实测 0.9559 / 地板 0.95；MRR 实测 0.9338 / 地板 0.93；
    基线只能上调，退化必须先解释（retrieval-rules.md §7）
  - `Phase6HybridRetrievalEvaluationIT`：四路对比评估（`-Dollama.it=1` 门控）
  - 改造：`RetrievalServiceTest`（VECTOR 模式 + 可观测降级断言）、Phase 4/5 评估测试迁移到
    `RetrievalStackForTest` 组装
  - 过程中修复的关键缺陷：双 Provider Bean 歧义（@Qualifier）、关键词索引未扫描 Vault
    （ensureFreshCorpus 先 scan）、降级结果通道变量错位
- `npm run build`（vue-tsc 严格 + vite）：0 错误。

## Known Limitations

1. 词法 Reranker 为确定性 IDF 实现（非神经模型），实测负收益已默认关闭；神经 Reranker
   （bge-reranker 本地 ONNX / 云 API）待 Phase 7+。
2. bigram 分词无词典：无同义归一，中文语义改写完全依赖向量路（已如实标注 Keyword Search Limited）。
3. 两路 Provider 串行执行未并行——Hybrid 193ms ≈ Vector 171ms + 2ms，当前量级无测量到的瓶颈，
   按 08-performance 原则不动手。
4. debug 接口仅开发用途，`RETRIEVAL_DEBUG_ENABLED` 默认 false；前端调试面板仅 DEV 构建渲染。
5. 关键词索引为单实例内存态，多副本部署时各节点独立重建（个人应用当前无此场景）。

## Next Phase（Phase 7 候选方向）

神经 Reranker 接入与重评估、Query 改写（同义扩展）、评估集扩到 >100 条后重估 Reranker 默认值、
Milvus 实机 IT 补齐。Phase 6 全部验证通过，前置条件已满足。

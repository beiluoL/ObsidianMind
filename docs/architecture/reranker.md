# Reranker 架构

> 实现：`com.obsidianmind.retrieval.RerankerProvider`（接口）+ `LexicalReranker`（当前唯一实现）。
> 配置：`retrieval.reranker.*`。

---

## 一、Reranker 是什么、不是什么

**是：** 对 RRF 融合后的候选做**二次相关性排序**的组件。回答的问题是
「这个 Chunk 对当前 Query 到底有多相关」。

**不是：**

- 不是答案生成器——它与 LLM Answer 彻底解耦（不调用 Chat 模型，未来也不应复用）
- 不是检索召回器——它不产生新候选，只对既有候选**重排 + 打分**（不增不减）

**在管线中的位置：**

```text
Hybrid TopN（RRF 降序）
      ↓
  Reranker.rerank(query, candidates)   ← rerankScore 写入独立通道
      ↓
重排后的 TopN + 未进入 TopN 的尾部（保持 RRF 序）
      ↓
  Final TopK
```

## 二、状态诚实原则（本阶段红线）

**当前本地环境没有神经 Reranker 模型，系统如实呈现这一点：**

| 状态 | 触发条件 | 行为 |
| --- | --- | --- |
| `NOT_CONFIGURED` | `retrieval.reranker.enabled=false`（默认） | 直接沿用 RRF 排序，`rerankScore` 不写入（NaN） |
| `APPLIED` | enabled=true 且重排成功 | rerankScore 写入，finalScore = rerankScore |
| `RERANKER_FALLBACK_TO_RRF` | enabled=true 但重排抛异常 | fallbacks 记录，沿用 RRF 序（可观察） |
| `SKIPPED_EMPTY` | 融合结果为空 | 无可重排，合法状态 |

- 绝不假装「已接入 bge-reranker / 云端 rerank API」
- 评估报告中的 Hybrid + Reranker 组使用**确定性词法 Reranker**，明确标注"非神经模型"
- 未来接入真实模型（本地 ONNX / 云 API）时新增 `RerankerProvider` 实现即可，接口不变

## 三、RerankerProvider 接口

```java
public interface RerankerProvider {
    String name();                 // "lexical" / 未来 "onnx-bge" / "cloud-xxx"
    boolean isAvailable();         // 未启用 → false（NOT_CONFIGURED，非错误）
    List<RetrievalCandidate> rerank(String query, List<RetrievalCandidate> candidates);
}
```

契约：

- 返回按新相关性降序的候选，**候选集合不变**（身份字段、chunkId 原样保留）
- `rerankScore` 写入候选的独立分数通道，`rrfScore` 保留不覆盖
- 未启用时实现应拒绝被调用（`IllegalStateException`）——HybridRetriever 保证先查 `isAvailable()`

## 四、当前实现：LexicalReranker（确定性词法）

**原理：查询 token 的 IDF 加权覆盖率**

```text
rerankScore = Σ(命中查询词的 IDF) / Σ(全部查询词的 IDF)
```

- IDF 在**当前候选集**内估计（候选集即评估微型语料）
- 用 IDF 而非裸词频：「一个」「可以」这类高频 bigram 被压低权重，
  `HashMap` / `Metaspace` 这类稀有高信息 token 主导排序
- 同分保持 RRF 原序（稳定排序），完全确定性——回归可复现

**能力边界（不夸大）：**

- 纯词法统计，**不懂语义**——同义改写不会提升，它的价值是把 RRF 里"词面高度吻合"
  的精确候选顶到前面
- query 与 chunk 内容均视为 untrusted：实现只做文本统计，绝不执行文本中的指令

## 五、为什么默认关闭

1. 本地无神经模型，词法 Reranker 的收益未经大库验证（当前数据集 RRF 已接近天花板）
2. 多一次打分开销（虽小），没有测量数据支撑默认开启（08-performance：先测量）
3. `RETRIEVAL_RERANKER_ENABLED=true` 一行即可开启，评估报告记录了开启后的对比数据

## 六、Model Center 的关系

**Chat Model ≠ Embedding Model ≠ Reranker Model**——三者是不同的模型类别：

- Reranker 不复用 Chat 模型（生成 ≠ 判别相关性）
- 未来 Model Center UI 可显示 Reranker Models 分类，但本阶段只建立接口与配置结构，
  不引入新的 Provider 配置面（Minimal Infrastructure）

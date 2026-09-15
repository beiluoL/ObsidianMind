# Retrieval Evaluation（Phase 4）

> 目的：把"检索效果好不好"从感觉变成可重复运行的数字。MVP 只实现 **Recall@K**，不做评估平台。

## 指标定义

### Recall@K（本阶段实现）

**正确文档是否出现在 Top K 的结果里。**

```text
Recall@K = |expectedDocuments ∩ retrievedTopKDocuments| / |expectedDocuments|
```

K=5 与检索默认 `default-top-k` 一致。对单预期文档的 Query，Recall@5 等价于"该文档是否进 Top 5"。按文档去重后计算（同文档多 Chunk 命中只算一次），与用户感知一致。

### Precision@K（本阶段不实现）

Top K 里有多少比例是相关的。"相关"需要人工标注多级相关性，MVP 评估集只有期望文档清单，无法严格定义；待引入 Reranker 时再补。

### MRR（本阶段不实现）

第一个正确结果排名倒数的均值，需要期望文档的"优先序"；评估集目前是无序集合。同上，后置。

## 评估集

`tests/evaluation/retrieval/queries.json`：10 条 Query，覆盖 Java / JVM / AI / RAG 四个主题，预期文档对应 `tests/fixtures/obsidian-vault/` 的真实路径。每条：

```json
{
  "query": "HashMap 扩容",
  "expectedDocuments": ["Java/HashMap.md"]
}
```

**约束**：只允许列 Demo Vault 里真实存在且内容确实回答该问题的文档——评估集本身不准，Recall 数字就没有意义。

## 运行方式

条件测试 `Phase4RetrievalEvaluationTest`（真实 Ollama Embedding + InMemory 向量库，无需 Milvus）：

```bash
cd apps/backend
mvn test -Dtest=Phase4RetrievalEvaluationTest -Dollama.it=1
```

流程：索引 Demo Vault → 逐条 Query 检索（与生产同一 `RetrievalService`）→ 计算 Recall@5 → 输出明细日志（每条命中/未命中与得分）→ 全部通过时断言 Recall@5 ≥ 1.0（MVP 数据集全命中是合理基线；引入更多干扰文档后应改为统计阈值并记录历史）。

默认跳过（无 `-Dollama.it=1` 且无本机 Ollama 时不执行），不影响 CI。

## 为什么现在就要做

未来任何检索侧改动——换 Embedding 模型、调 chunk-size、引入 Hybrid Search 或 Reranker——都应该跑一次本评估对比 Recall@K 前后变化，用数字决定取舍，而不是"感觉效果更好了"。

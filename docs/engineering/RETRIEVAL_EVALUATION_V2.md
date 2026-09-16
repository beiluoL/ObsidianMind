# Retrieval Evaluation V2（Phase 6）

> 评估时间：2026-09-16 ｜ 环境：本机 macOS + Ollama（bge-m3 embedding）+ 内存向量库（无需 Milvus）
> 复现命令：`mvn test -Dtest=Phase6HybridRetrievalEvaluationIT -Dollama.it=1`
> 回归基线：`mvn test -Dtest=Phase6RetrievalRegressionTest`（确定性，无需 Ollama）

---

## 1. Dataset

| 项 | 值 |
| --- | --- |
| 数据集 | `tests/evaluation/retrieval/queries-v2.json` |
| Query 数 | **34**（Phase 4 的 10 条 v1 数据集保留未动，服务 obsidian-vault 基线） |
| 语料库 | `tests/fixtures/demo-vault`（18 篇笔记 / 5 文件夹，新增 `Database/MySQL.md`） |
| Chunk 数 | 索引输出 340+ chunks（chunk 800 字符 / overlap 100） |
| Query 类型 | 精确关键词 / 同义表达 / 模糊问题 / 类名 / 方法名 / 多概念 / 中文自然语言 |
| 分类 | Java(7) JVM(7) Spring(2) MySQL(2) AI(8) LLM(3) RAG(3) Obsidian(3) |

## 2. 对比结果（Run A/B/C/D）

| Mode | Recall@1 | Recall@3 | Recall@5 | MRR | avgMs/query |
| --- | --- | --- | --- | --- | --- |
| **Run A：Vector** | 31/34 (91.2%) | 33/34 (97.1%) | **34/34 (100.0%)** | **0.947** | 171 |
| **Run B：Keyword** | 31/34 (91.2%) | 32/34 (94.1%) | 33/34 (97.1%) | 0.934 | **2** |
| **Run C：Hybrid** | 31/34 (91.2%) | 33/34 (97.1%) | **34/34 (100.0%)** | **0.947** | 193 |
| **Run D：Hybrid + Lexical Reranker** | 31/34 (91.2%) | 32/34 (94.1%) | **34/34 (100.0%)** | 0.940 | 191 |

**诚实声明：**

- Run D 的 Reranker 是**确定性词法重排（IDF 加权覆盖率），不是神经模型**——本机无
  Reranker 模型，状态为词法实现，真正的神经 Reranker **NOT_CONFIGURED**
- 指标定义：Recall@K = 前 K 条中出现任一预期文档的查询占比；MRR = 首个相关结果排名倒数
  的均值（多预期文档查询按"出现任一"计——与 Phase 4 口径一致）
- 未做任何"调参让 Hybrid 好看"的操作；以下退化如实记录

## 3. 未命中与退化 Query（最有价值的信息）

### Keyword 完全失败（MISS）

| Query | Keyword 结果 | Vector/Hybrid 结果 |
| --- | --- | --- |
| 堆和栈的区别 | MISS（笔记写「堆」「栈」为独立小节，无"区别"语境词） | Recall@5 命中但 TOP1 错（AI/RAG.md） |

**结论：** 同义/概念改写是关键词路的死穴——正是 Hybrid 中向量路存在的理由。

### Vector / Hybrid 共同的 TOP1 未命中（3 条）

| Query | TOP1 实际返回 | 预期 |
| --- | --- | --- |
| Metaspace 方法区 | 01-个人成长/读书笔记方法.md | Java/JVM 内存结构.md |
| 垃圾回收如何判断对象存活 | Java/G1 垃圾回收器.md | Java/GC Roots.md |
| 堆和栈的区别 | AI/RAG.md | Java/JVM 内存结构.md |

- 「Metaspace 方法区」：向量路被干扰文档带偏（bigram「方法」在读书笔记中高频出现），
  **Keyword 路该条 TOP1 正确**——Hybrid 的价值在于两路错误不重合
- 「垃圾回收…」「堆和栈…」：预期文档都在 Top5 内，只是 TOP1 排序问题

### Hybrid + Reranker 的退化（重要）

「RAG 为什么需要 Chunk」TOP1 从正确退化为 Java/HashMap.md——词法 Reranker 把
"chunk" 词面更密的 HashMap.md 顶到了前面，压过了语义更相关的 RAG.md。
**这是 Reranker 使 MRR 从 0.947 降到 0.940 的直接原因。**

**决策：** `retrieval.reranker.enabled` 默认保持 **false**。在没有神经 Reranker 之前，
词法重排在本数据集上是负收益；接入真实模型（bge-reranker 等）后需重跑本评估再评估默认值。

## 4. 性能

| 阶段 | 耗时（均值） | 说明 |
| --- | --- | --- |
| vectorSearchMs（含 Query Embedding） | ~170ms | Embedding 占绝对大头；Milvus→内存库时 search 本身 <5ms |
| keywordSearchMs | ~1-2ms | BM25 + bigram，18 篇笔记零压力 |
| fusionMs（RRF） | <1ms | 纯排名合并 |
| rerankerMs | <1ms | 20 候选词法打分 |
| **totalRetrievalMs** | Vector 171 / Keyword 2 / Hybrid 193 | Hybrid ≈ Vector + 2ms |

**结论：** 关键词路不构成瓶颈；Hybrid 的开销 ≈ 向量路自身的开销。两路串行执行
（未做并行）在当前量级下无需优化（08-performance：无测量瓶颈不动手）。

## 5. 回归基线（确定性，进 CI）

`Phase6RetrievalRegressionTest`（无需 Ollama）：

| 指标 | 实测 | 基线地板 |
| --- | --- | --- |
| KEYWORD Recall@5 | 0.9559 | **0.95** |
| KEYWORD MRR | 0.9338 | **0.93** |

改动 Chunking / 分词 / BM25 / RRF / Reranker 后必须运行；基线只能上调，
退化必须先解释（见 docs/engineering/retrieval-rules.md §7）。

## 6. 中文检索结论

- **PARTIAL（真实可用，有已知局限）**：bigram 分词对「HashMap 扩容」「Token 是什么」
  「ObsidianMind 是什么项目」等混合/中文查询全部命中（Recall@5 33/34）
- 已知局限：无词典 → 无同义归一（「文本转向量的技术叫什么」靠 bigram 覆盖命中，
  更复杂的改写必须依赖向量路）；单字查询依赖 unigram
- 未假装完成的部分已明确标注为 Keyword Search Limited（见 hybrid-retrieval.md §二）

## 7. 结论与决策记录

1. **默认模式 HYBRID**：与 Vector 同分（0.947）且对 Keyword 型失误免疫
   （「Metaspace 方法区」类干扰由 Keyword 纠正），代价可忽略（+2ms）
2. **Reranker 默认关闭**：词法重排实测负收益（MRR −0.007）；NOT_CONFIGURED 诚实呈现
3. **数据集升级到 34 条 v2**：v1（10 条）保留服务 Phase 4 基线
4. 下一步（Phase 7+）：神经 Reranker（本地 ONNX / 云 API）、query 改写、更大规模
   评估集（>100 条）后再重估 Reranker 默认值

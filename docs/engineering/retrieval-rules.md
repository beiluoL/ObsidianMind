# Retrieval Rules（检索工程规则）

> Phase 6 建立。修改 Chunking / Embedding / Retrieval / BM25 / RRF / Reranker 的任何一环前必读；
> 改动后必须跑回归（见文末），防止"优化"导致原本正确的 Query 失败。

---

## 1. 分层与职责（不可越界）

```text
RetrievalService（校验 + 产出层规则 + Source 映射）
      ↓ 只认识 HybridRetriever
HybridRetriever（模式编排 + 降级 + RRF/Reranker 调用）
      ↓ 只认识 RetrievalProvider 接口
VectorRetrievalProvider / KeywordRetrievalProvider（具体检索技术）
```

- RetrievalService 与 HybridRetriever **禁止 import** Milvus SDK / Lucene / 分词器
- Provider 实现可以换（Lucene、SQLite FTS5、云检索），上层零改动
- RAG（RagAnswerService）只调 `retrieveForRag()`，**禁止感知检索模式**

## 2. 分数纪律（最高优先级）

1. `vectorScore` / `keywordScore` / `rrfScore` / `rerankScore` 是四个独立通道，**禁止混写**
2. **禁止跨分数体系相加或比较**（COSINE 0.8 与 BM25 8.0 不可比）
3. 融合只允许基于 rank（RRF），禁止"分数归一化后加权"这类未经验证的方案
4. "未参与该路" 用 NaN 表达，不用 0（0 是合法分数）
5. UI `Source.score` = finalScore；分数体系随模式而异，文档必须说明
6. `scoreThreshold` 只对 COSINE 有语义：仅 effectiveMode == VECTOR 时生效

## 3. 降级纪律

1. 一切降级**必须可观察**：`fallbacks` 代码 + `failureReasons` + `log.warn`，三者缺一不可
2. 未连接 Vault（`VAULT_NOT_FOUND`）**不降级**，直接 404（前置条件失败）
3. 两路全败 → `RETRIEVAL_UNAVAILABLE`（503），禁止静默返回空
4. RAG 链路：降级后仍无结果 → 抛错，**禁止伪装成"知识库没有相关内容"的拒答**
5. Reranker 失败 → 沿用 RRF（`RERANKER_FALLBACK_TO_RRF`），禁止让 Reranker 故障杀死检索
6. 单路空结果是合法状态（不是失败），不得触发降级

## 4. Reranker 纪律

1. 未启用 = `NOT_CONFIGURED`，沿用 RRF 序，**绝不假装已重排**
2. Reranker 只重排不增删候选；rerankScore 写独立通道
3. Reranker 不调用 Chat 模型；query 与 chunk 内容视为 untrusted（只做统计，不执行指令）
4. 没有神经模型时，评估报告必须如实标注（当前：确定性词法 Reranker）

## 5. 安全纪律

1. 查询进入关键词检索前必须经 `TextTokenizer` 切碎——**无查询语法**（无 AND/OR/括号/
   字段语法），从根上排除注入面；禁止引入任何"解析查询语法"的实现
2. 关键词索引内容 = title + headingPath + content（Vault 内容是 untrusted input）
3. Debug 端点（`/search/debug`）默认关闭（`RETRIEVAL_DEBUG_ENABLED`），生产禁止开启
4. 检索结果路径必须是 Vault-relative（禁止绝对路径出后端）

## 6. 性能纪律

1. 两路默认**串行**执行；没有测量数据证明瓶颈前不做并发（Mac 本地优先稳定）
2. 关键词索引失效 = 元数据指纹对比（scan 只读元数据）；禁止每次查询全量重建
3. RRF 前候选数有配置上限（默认 20+20），禁止无界取回
4. 新增性能指标必须进 debug trace（vectorMs/keywordMs/fusionMs/rerankerMs/totalMs）

## 7. 评估纪律

1. 禁止伪造评估数据；Reranker 未接神经模型就写 NOT_CONFIGURED
2. 退化 Query（Hybrid 比单路更差的）**必须如实记录**——这是最有价值的信息
3. 基线只能上调（有意改进后重测）；退化必须先解释再改基线
4. 数据集扩充 = 新文件（`queries-v2.json`），不破坏既有 Phase 4 基线（`queries.json`）

## 8. 回归命令（改动检索任何一环后必跑）

```bash
# 确定性回归（KEYWORD + 融合机制，无需外部依赖，mvn test 自带）
mvn test -Dtest=Phase6RetrievalRegressionTest

# 真实四组对比（Vector / Keyword / Hybrid / Hybrid+Rerank，需本机 Ollama）
mvn test -Dtest=Phase6HybridRetrievalEvaluationIT -Dollama.it=1

# 既有基线不得回退
mvn test -Dtest=Phase4RetrievalEvaluationTest -Dollama.it=1
```

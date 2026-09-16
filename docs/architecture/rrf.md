# RRF（Reciprocal Rank Fusion）

> 实现：`com.obsidianmind.retrieval.ReciprocalRankFusion`（纯函数，可独立单测）。
> 配置：`retrieval.rrf.k`（默认 60）。

---

## 一、公式

```text
RRF(d) = Σ_lists  1 / (k + rank_list(d))
```

- `rank_list(d)`：候选 d 在该路结果中的排名，**从 1 开始**
- `k`：平滑常数，控制头部排名的优势幅度
- 对每个 ranked list 求和：候选在越多路排名越靠前，分数越高

**工作示例（k=60）：**

```text
Vector 检索返回：        Keyword 检索返回：
  rank1: A                rank1: C
  rank2: B                rank2: A
  rank3: C                rank3: D

RRF 计算：
  A = 1/(60+1) + 1/(60+2) = 0.01639 + 0.01613 = 0.03252
  C = 1/(60+3) + 1/(60+1) = 0.01587 + 0.01639 = 0.03226
  B = 1/(60+2)             = 0.01613
  D = 1/(60+3)             = 0.01587

融合排序：A > C > B > D
```

**联系描述：** A 在两路都排前两位（两路都"觉得它对"），所以第一；C 是关键词路的
第一名，向量路也给了第三；B 只有向量路认；D 只有关键词路认。这就是 RRF 的直觉——
**多路共识者优先**。

## 二、为什么融合 rank 而不是原始分数

| 分数体系 | 量级 | 分布 |
| --- | --- | --- |
| COSINE（向量） | 0 ~ 1 | 集中在 0.5~0.9，区分度弱 |
| BM25（关键词） | 0 ~ +∞ | 长尾，稀有词命中可到 10+ |
| RRF | ~1/(k+rank) | 排名越小越大，天然可比 |

- `vectorScore + keywordScore` 直接相加 = **语义错误**：BM25 的 8.0 会瞬间淹没 COSINE
  的 0.8，等于让关键词路独裁
- 分数归一化（min-max / z-score）需要每路的分布统计，小数据集上不稳定且有被极端值
  操纵的风险
- rank 是各检索系统唯一**天然可比**的信息：不管底层打分逻辑多不同，"我把你排第 3"
  这个表述是同一种语言

## 三、k 的选择依据

- **k=60**：RRF 原始论文（Cormack, Clarke & Buettcher, *Reciprocal rank fusion outperforms
  Condorcet and individual rank learning methods*, SIGIR 2009）使用的取值，也是业界
  事实标准（Elasticsearch RRF 默认 60）
- k 越小：rank1 的优势越大（1/2 vs 1/3 差异悬殊），头部文档容易独裁
- k 越大：各名次差异被抹平，趋向"谁出现次数多谁赢"
- 无本数据集实验依据前**不拍脑袋改值**；配置化（`RETRIEVAL_RRF_K`）以便后续做 k 敏感性实验

## 四、实现语义（与代码一一对应）

1. **输入**：多个 ranked list（Provider 契约：内部已按相关性降序）
2. **按 chunkId 合并**：同一 Chunk 在两路都被召回 → 分数合并到同一条候选
3. **原始分数不丢**：`vectorScore` / `keywordScore` 通道保留（debug 与评估依赖）
4. **来源类型升级**：两路同时命中 → `retrievalType = HYBRID`；单路命中原路
5. **并列处理**：rrfScore 相同的候选按"先被写入合并表的顺序"稳定排序
   （向量路在先，关键词路在后）——确定性，回归可复现
6. **输出**：按 rrfScore 降序的融合候选列表

## 五、测试

`ReciprocalRankFusionTest` 覆盖：公式数值核验、两路命中标记 HYBRID、单路保留原类型、
k 的敏感性（k 小 → 头部差距大）、空/缺失列表容错、非法 k 拒绝。

## 六、回归约束

修改 Chunking、分词、RRF k、Reranker 任何一环后必须运行：

```bash
mvn test -Dtest=Phase6RetrievalRegressionTest          # 确定性回归（KEYWORD + 融合机制）
mvn test -Dtest=Phase6HybridRetrievalEvaluationIT -Dollama.it=1   # 真实全量对比
```

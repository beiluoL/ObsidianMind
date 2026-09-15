# Citation（Phase 5）

> Citation 的"来源"由系统掌控。LLM 负责生成回答；Source Registry、编号、路径全部由后端生成。
> 铁律：**Markdown 文件 → Document → Chunk → Vector → RetrievalResult → Source → Registry → Citation → Answer**，
> 每一跳都必须可回溯；答案有 Citation 但 Citation 无法定位原文 = 缺陷。

## 1. Source Registry

Context 组装时，系统按相似度排名为每条 ContextItem 分配稳定编号 `SRC-1..SRC-n`。这就是 Registry：

```text
SRC-1 → { title: "HashMap", path: "Java/HashMap.md", heading: "# 扩容机制", chunkIndex: 3, score: 0.86 }
SRC-2 → { title: "HashMap", path: "Java/HashMap.md", heading: "# table 长度", chunkIndex: 1, score: 0.79 }
```

- Registry 内容来自 Milvus 向量元数据（Phase 3 索引阶段写入），绝不来自模型。
- 编号在一次回答内稳定；每次新回答重新编号（单轮设计）。
- Registry 先于生成通过 SSE `citation` 事件整体下发——前端在 token 到达前就能渲染来源面板。

## 2. 两层编号协议：SRC-n（内部）与 [n]（UI）

| 层 | 形态 | 谁消费 |
| --- | --- | --- |
| Prompt / LLM 输出 / Registry | `[SRC-1]` | 系统（CitationParser 解析校验） |
| 用户看到的正文 | `[1]` | 人 |
| 来源面板徽标 | `[1]` | 人（与正文 [n] 一一对应，点击 → openNote(path)） |

解耦原因：内部协议若直接暴露给用户（`[SRC-1]`）阅读体验差且耦合实现；未来若编号格式演进（如跨轮引用），只改映射层。

## 3. 解析与校验（CitationParser）

LLM 输出的 `[SRC-n]`（大小写不敏感、容忍前导零）由 `util/CitationParser` 解析：

- `cited`：出现顺序去重后、**存在于 Registry 的合法编号** → 进入 done 事件 `citedSourceIds`，作为 Citation Correctness 评估的输入。
- `unknown`：模型幻觉出的不存在编号 → **只记录日志，不报错、不 500**（一条引用解析失败绝不能杀死整个回答）。

## 4. 为什么不信任模型生成路径

- 模型会幻觉出"看似合理"的本机绝对路径（`/Users/...`）——泄露隐私信息且无法定位。
- 模型没有跨层视野：它只看到 Prompt 里的资料区，不知道 chunkIndex / vaultId / 文档结构。
- 所以：模型只被允许**复述**系统给定的编号；path/heading/snippet 永远由后端从向量元数据派生。评估集对路径泄露有硬断言（`Phase5RagEvaluationTest` 的 ABSOLUTE_PATH 检查）。

## 5. 为什么第一版不做 Citation Graph

模型可靠性有限，复杂结构（图表式引用、跨来源推理链）会放大幻觉且难以验证。第一版采用最简结构：**答案文本 + 明确的 sourceId token + 系统 Registry 映射**。Citation Graph / 引用关系可视化留给后续阶段，在系统校验足够可信之后再引入。

## 6. Frontend 行为

- 渲染前正则替换 `[SRC-n]` → `[n]`（大小写不敏感）。
- 来源面板逐条显示 index 徽标、title、path、heading、相似度条；点击复用 `openNote(path)`（Note id = Vault 相对路径）。
- **每条回答独立持有 sources**（message.sources），没有全局 Sources 面板。
- heading 精细定位（锚点跳转）是已知限制，本阶段不强行实现（见 Phase 5 Report · Known Limitations）。

## 7. 完整回溯验证

任何一条 Citation 必须满足：`[n]` → `SRC-n` → `path + heading + chunkIndex` → Vault 内真实文件。评估集（`tests/evaluation/rag/rag-queries.json`）与 `Phase5RagEvaluationTest` 对此做确定性校验。

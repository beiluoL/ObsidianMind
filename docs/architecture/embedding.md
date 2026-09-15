# Embedding 设计说明

> Phase 3。配套：`knowledge-pipeline.md`、`milvus.md`。执行细则见 `.agents/skills/04-ai-rag-engineering/SKILL.md`。

## 一、必须先纠正的概念：Embedding ≠ 把 Token ID 变成 Vector

正确链路：

```text
Text（人类可读文本，如一段 Chunk）
   ↓  Tokenizer（模型内部分词器）
Tokens（符号序列，如 [bge] 的 WordPiece 子词）
   ↓  词表映射
Token IDs（整数序列，词表下标）
   ↓  Embedding Model（Transformer 编码 + 池化）
Dense Vector（固定维度浮点向量，本项目 1024 维）
```

关键点：**调用方只提供 Text，也只拿到 Dense Vector**。Tokenizer / Tokens / Token IDs 全部发生在模型内部，是模型的实现细节——不同模型用不同分词器（BPE / WordPiece / SentencePiece），同一个词的 Token ID 在不同词表里完全不同。把"Token ID → 向量"理解为 Embedding 是常见误解：Embedding 是**整段文本的语义压缩**，不是逐 Token ID 查表再拼接。

四个层次速记：

| 层次 | 是什么 | 谁产生 | 谁消费 |
| --- | --- | --- | --- |
| Text | 原文 / Chunk 内容 | Vault | 调用方 → API |
| Tokens | 分词后的符号 | 模型内部 | 模型内部 |
| Token ID | 词表整数下标 | 模型内部 | 模型内部 |
| Embedding | 1024 维浮点向量 | 模型输出 | Milvus / 相似度计算 |

## 二、本项目模型选择（实测确认）

| 模型 | 维度 | 说明 |
| --- | --- | --- |
| `bge-m3:latest`（默认） | **1024** | BAAI 多语 embedding，8192 token 上下文，中文表现好，本机已安装（Ollama `/api/tags` 实测 `embedding_length=1024`） |
| `qwen3-embedding:0.6b`（备选） | **1024** | 同为 1024 维，可切换；切换后**必须全库重嵌** |

配置（application.yml / 环境变量可覆盖）：

```yaml
ai:
  embedding:
    model: 由 ai.ollama.embedding-model 决定，默认 bge-m3
    batch-size: 16      # 一次 HTTP 请求的 Chunk 数
    timeout-seconds: 30 # 冷启动模型加载 + 批量推理，比健康探测的 3s 宽
```

**维度从哪来（禁止拍脑袋）**：`milvus.vector-dimension=1024` 写在配置里，来源是上表实测值；运行时以第一次真实 embedding 返回的向量维度做**一致性校验**——不一致抛 `EMBEDDING_ERROR`（维度不符），宁可失败不可写坏 Collection。换模型 = 改配置 + 删 Collection 重嵌，`contentHash` 不含模型指纹，所以**换模型必须手动清索引**（文档化操作，避免两模型向量混存一个 Collection 这种静默错误）。

## 三、接口契约

`EmbeddingService`（接口，Provider 边界）：

```java
List<float[]> embed(List<String> texts);  // 输入顺序 = 输出顺序，一一对应
```

`OllamaEmbeddingService` 实现（POST /api/embed）：

- 请求：`{"model": "...", "input": ["chunk1", "chunk2", ...]}` —— **批量**，绝不逐条 HTTP（N+1 是索引管线的头号性能错误）。
- 校验（缺一即抛 `OllamaUnavailableException` / `EmbeddingException`）：响应 `embeddings` 数组长度 == 输入长度；每个向量维度 > 0 且与首个向量一致；数值非空。
- 重试：请求失败重试 **1 次**（指数退避 500ms），仍失败则抛出——有限重试，绝不无限。
- 超时：connect/read 均 `ai.embedding.timeout-seconds`（默认 30s），禁止无超时请求。

## 四、失败场景与处理

| 场景 | 表现 | 处理 |
| --- | --- | --- |
| Ollama 未启动 | 连接拒绝 | `OLLAMA_UNAVAILABLE` → 503，健康探测 `/api/v1/system/status` 同步显示 DISCONNECTED |
| 模型未 pull | 响应 404/error | `EMBEDDING_ERROR`，错误信息含模型名，提示 `ollama pull <model>` |
| 请求超时 | 读超时 | 重试 1 次 → 仍失败 `OLLAMA_UNAVAILABLE` |
| 响应畸形 / 空向量 | 校验失败 | `EMBEDDING_ERROR`，不带病写入 |
| 维度不符 | 首向量维度 ≠ 配置维度 | `EMBEDDING_ERROR`（大概率换了模型没清 Collection） |
| 空文本输入 | — | 调用方保证不送空文本（Chunker 已保证空 body 产 0 Chunk）；服务端再防御：空串跳过并记 WARN |

## 五、日志纪律

记录：批大小、模型名、耗时、向量维度。**禁止**输出向量数值、Chunk 全文、API 相关凭据。

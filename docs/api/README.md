# API 文档

后端为 Spring Boot 3.x（`apps/backend`），交互式文档由 springdoc 提供：

- Swagger UI：http://localhost:8080/swagger-ui.html
- OpenAPI JSON：http://localhost:8080/v3/api-docs

## 快速索引（Phase 1 已实现）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 最简存活探针 → `{"status":"UP"}` |
| GET | `/api/v1/system/status` | 应用 + Ollama / Milvus / Vault 组件状态 |
| GET | `/api/v1/vault` | Vault 元信息 |
| POST | `/api/v1/vault/connect` | 连接 Vault（校验存在/目录/可读） |
| POST | `/api/v1/vault/scan` | 扫描（仅元数据，不读正文） |
| GET | `/api/v1/notes/{*id}` | 读取笔记（id = Vault 相对路径） |
| PUT | `/api/v1/notes/{*id}` | 保存笔记（防路径穿越） |
| GET | `/api/v1/search?q=` | 全文搜索 |
| POST | `/api/v1/search` | **混合检索（Phase 6）**：请求 `{query, mode?, topK?}`（mode=VECTOR/KEYWORD/HYBRID，默认服务端配置），返回 `{query, requestedMode, effectiveMode, fallbacks[], rerankerStatus, elapsedMs, sources[同语义检索]}`；失败自动降级（fallbacks 可观察），两路全败 503 `RETRIEVAL_UNAVAILABLE` |
| POST | `/api/v1/search/semantic` | **语义检索**（Query→Embedding→向量检索→Sources），请求 `{query, topK?}`，返回 `{query, sources[{title,path,heading,snippet,score,documentId,chunkIndex}], elapsedMs}` |
| GET | `/api/v1/search/config` | 检索配置（只读）：默认模式 / TopK / 候选数 / RRF K / Reranker 状态 / Debug 门禁 |
| POST | `/api/v1/search/debug` | Retrieval Debug 全链路 trace（仅 `RETRIEVAL_DEBUG_ENABLED=true`，否则 400） |
| POST | `/api/v1/index/run` | **同步**执行增量索引（扫描→解析→切块→Embedding→Milvus），返回 `{total,indexed,updated,skipped,deleted,failed,chunkCount,elapsedMs,errors}` |
| POST | `/api/v1/index` | 触发索引任务（Phase 1 兼容，异步，配 GET /status 轮询） |
| POST | `/api/v1/chat` | **RAG 问答（同步）**：完整回答 + Sources + 指标，请求 `{message, topK?}`，返回 `{content, citedSourceIds, sources[{index,sourceId,title,path,heading,snippet,score}], metrics, noContext}` |
| POST | `/api/v1/chat/stream` | **RAG 问答（SSE 流式）**：phase / citation / message / done / error 事件（data 均为 JSON），见 docs/architecture/rag-chat-pipeline.md §3 |
| GET | `/api/v1/ai/providers` | **Model Center**：Provider 列表（apiKeyMasked / credentialSource，绝无 Key 原文） |
| POST | `/api/v1/ai/providers` | 新增 Provider（name/type/baseUrl/apiKey） |
| PUT | `/api/v1/ai/providers/{id}` | 更新 Provider（apiKey 留空 = 保留原 Key） |
| DELETE | `/api/v1/ai/providers/{id}` | 删除 Provider（级联删模型与凭据） |
| POST | `/api/v1/ai/providers/{id}/test` | 测试连接（success/latency/语义化错误码，safe message） |
| GET | `/api/v1/ai/providers/{id}/models` | 真实可用模型（当前仅 Ollama /api/tags） |
| GET | `/api/v1/ai/models` | Model 列表 |
| POST | `/api/v1/ai/models` | 新增 Model（providerId/modelName/capabilities） |
| PUT | `/api/v1/ai/models/{id}` | 更新 Model |
| DELETE | `/api/v1/ai/models/{id}` | 删除 Model |
| POST | `/api/v1/ai/models/{id}/default` | 设为默认对话模型 |

完整请求/响应结构以 Swagger UI 为准；后续 Phase 新增端点（embedding / rag / chat 正式版）将在此文档持续补充。

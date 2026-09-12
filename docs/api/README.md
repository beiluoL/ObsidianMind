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
| POST | `/api/v1/index` | 触发索引任务（异步） |
| POST | `/api/v1/chat` | 问答（Phase 1 Mock，RAG 预留） |
| POST | `/api/v1/chat/stream` | 流式问答（SSE） |

完整请求/响应结构以 Swagger UI 为准；后续 Phase 新增端点（embedding / rag / chat 正式版）将在此文档持续补充。

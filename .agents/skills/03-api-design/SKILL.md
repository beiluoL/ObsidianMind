# 03-api-design

## Purpose

统一 ObsidianMind REST API 的契约规范：路径、DTO、错误结构、校验、流式接口。前后端唯一契约来源。

## Scope

后端所有 `/api/**` 端点；前端消费这些端点的 service 层与 TypeScript 类型。

## When To Use

- 新增 / 修改任何端点。
- 修改前端调用后端的请求 / 响应类型。

## When NOT To Use

- 纯前端内部状态与组件通信（01）。

## Rules

### 1. URI 与 Method

- 前缀 `/api/v1/...`；健康探针例外：`GET /api/health`（存活探针惯例，保持简单）。
- 资源用名词复数或动作短语：`/vault`、`/notes/{*id}`、`/search`、`/index`、`/chat`。
- Method 语义：GET 读取（无副作用）、POST 创建/触发动作、PUT 整体保存（`PUT /notes/{*id}` 原子写回）。当前业务用不到 PATCH / DELETE，不为了 REST 完美硬造。
- 动作类端点允许动词子路径（`POST /vault/connect`、`POST /vault/scan`），这是项目既有风格。
- Boot 3 PathPattern：`{*id}` 只能出现在路径末尾（见 02 第 8 条）；多段相对路径资源如 backlinks 用查询参数。

### 2. Request / Response DTO

- 入参一律 record DTO + `@Valid` + jakarta validation 注解（`@NotBlank` / `@Size` 等），Controller 不手工校验。
- 出参一律 DTO，不得返回 Domain/Entity。字段命名 camelCase（Jackson 默认），与前端 `src/types/` 一一对应。
- `ChatRequest.scope` 这类可选字段由后端提供 `scopeOrDefault()` 之类的归一化，前端可以不传。

### 3. Error Response（统一信封）

所有错误返回统一结构（由 `GlobalExceptionHandler` 保证）：

```json
{
  "code": "VAULT_NOT_FOUND",
  "message": "Vault 不存在",
  "timestamp": "2026-09-15T13:00:00Z"
}
```

- `code`：大写下划线的稳定机器码（`INVALID_REQUEST` / `NOTE_NOT_FOUND` / `VAULT_ACCESS_DENIED` / `OLLAMA_UNAVAILABLE` / `MILVUS_UNAVAILABLE` / `INTERNAL_ERROR`），前端可据此做分支；**code 是契约，不得随意改名**。
- `message`：给用户看的一句话，不含堆栈与内部细节。
- 新增 code 时在 `GlobalExceptionHandler.statusOf` 补映射，并同步本文档与前端错误处理。
- `requestId` / `details` 字段当前项目未引入；待有日志追踪需求时再评估，不提前加。

### 4. Status Code

| 场景 | 状态码 |
| --- | --- |
| 成功 | 200（本项目无"创建即 201"场景，统一 200） |
| 参数非法 / 路径非法 | 400 |
| Vault / Note 不存在 | 404 |
| Vault 未授权 / 路径越界 | 403 |
| Ollama / Milvus 不可用 | 503 |
| 未预期异常 | 500 |

### 5. Validation

- 必填、长度、格式在 DTO 注解层完成；错误信息由 `MethodArgumentNotValidException` 处理器拼成 `字段名 + 原因`。
- 路径类参数（note id）必须经 `VaultPaths` 校验，属于业务校验，Service 层抛 `InvalidRequestException`。

### 6. Pagination / Filtering

- 当前数据量小，搜索（`GET /search?q=`）未分页。**当搜索结果或笔记列表可能超过 ~200 条时**，先做 `page/size` + 总数字段，且一次只加一个端点需要的最小参数。不提前建通用分页框架。
- 过滤参数用查询参数（`?q=`、未来 `?tag=`、`?folder=`），不用 POST body 传过滤条件。

### 7. Idempotency

- PUT 天然幂等（保存 = 原子写回原文件）。
- 触发类 POST（`/vault/scan`、`/index`）设计为可重复调用不产生重复副作用。

### 8. Versioning

- 破坏性契约变更 = 升级路径版本（`/api/v2/...`），旧版本保留至前端迁移完成。
- 字段新增（向后兼容）不需要升版本。

### 9. API Contract 同步（强制）

修改接口时，以下位置必须同步，缺一不可：

1. 后端 DTO + Controller + Swagger 注解
2. 前端 `src/services/` 对应 service + `src/types/` 类型
3. `docs/api/README.md` 快速索引表
4. 若错误 code 变化：`GlobalExceptionHandler` + 前端错误分支

### 10. Streaming（SSE）

`POST /api/v1/chat/stream`（`text/event-stream`）规范：

- **事件类型**：`phase`（searching/generating）、`message`（增量或整段回答）、`done`（终态）；错误以 `error` 事件或断连表达，不用 HTTP 状态码表达流中错误。
- **生命周期**：Service 负责 `complete()` / `completeWithError()`；超时（`SseEmitter` timeout 参数必须显式设置，不得无限挂起）；客户端断开要停止后续 LLM 调用（`onCompletion` / `onTimeout` 回调清理）。
- **前端**：用 `EventSource` / fetch-stream 封装在 `aiService`，组件只消费回调（现有 `ChatCallbacks` 模式：onPhase / onMessage）。
- Phase 2 接入真实 RAG 时**契约不变**（Mock → 真实实现保持同一事件结构）。

## Patterns

- `ChatController`：同业务提供同步（`POST /chat`）与流式（`POST /chat/stream`)双端点，结构对齐。
- `docs/api/README.md` 快速索引表：每个端点一行，新增端点必须登记。

## Anti-Patterns

- 只改后端不改前端类型（或反之）——契约撕裂。
- 错误响应临时拼一个新结构。
- 流式接口无超时、无断连清理。
- 在 GET 上做副作用。

## Checklist

- [ ] 路径在 `/api/v1` 下、method 语义正确
- [ ] DTO record + `@Valid`，响应不含 Domain
- [ ] 错误走统一信封，code 已登记
- [ ] `docs/api/README.md` 已更新
- [ ] 前端 service + types 已同步，`npm run build` 通过
- [ ] 流式端点有 timeout 与断连清理

## Related Skills

02-java-spring-boot（实现层）、01-frontend-engineering（消费层）、04-ai-rag-engineering（chat/index 端点的 RAG 语义）、05-testing（MockMvc 契约测试）。

## Project-specific Notes

- 现有 11 个端点见 `docs/api/README.md` 快速索引，Phase 1 契约已稳定，改动需谨慎。
- Swagger UI（springdoc）是交互式契约文档，路径冲突时以代码 + 本 Skill 为准。

## Status

active

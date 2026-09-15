# API 规则（API Rules）

> 人读版。执行细则与 Checklist 见 `.agents/skills/03-api-design/SKILL.md`；端点清单见 `docs/api/README.md`。

## 契约基线

- 路径前缀 `/api/v1/**`（健康探针 `GET /api/health` 例外）；Method 语义常规（GET 读 / POST 动作 / PUT 幂等保存）；破坏性变更升 `/api/v2`。
- 请求：record DTO + `@Valid` 注解校验；响应：DTO，不暴露 Domain。
- 统一错误信封 `{code, message, timestamp}`，由 `GlobalExceptionHandler` 保证；`code` 是稳定机器码（`INVALID_REQUEST` / `NOTE_NOT_FOUND` / `VAULT_ACCESS_DENIED` / `OLLAMA_UNAVAILABLE` / `MILVUS_UNAVAILABLE` / `INTERNAL_ERROR`），改名即破坏契约。
- 状态码映射：400 校验 / 路径非法，404 不存在，403 未授权，503 外部依赖不可用，500 兜底（不泄漏堆栈）。

## 契约同步（四个位置缺一不可）

后端 DTO + Controller + Swagger → 前端 service + `src/types/` → `docs/api/README.md` 索引表 → 错误 code 若新增则同步异常映射与前端分支。

## SSE 流式约定

`POST /api/v1/chat/stream`：事件 `phase`（searching/generating）→ `message` → `done`；错误走流内 `error` 事件或断连；emitter 必须显式 timeout 并在断连时停止后续 LLM 调用。Phase 2 换真实 RAG 实现时事件结构不变。

## 当前决策

- 未引入 `requestId` / `details` 错误字段：等有日志追踪需求再加，不提前。
- 搜索未分页：结果可能超 ~200 条时再做，一次只加一个端点所需最小参数。
- 动作类端点允许动词子路径（`/vault/connect`）：项目既有风格，不为 REST 纯度重命名。

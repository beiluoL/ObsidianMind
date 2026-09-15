# 02-java-spring-boot

## Purpose

约束 `apps/backend/`（Java 17 + Spring Boot 3.5.16 + Maven，spring-boot-starter-web/validation + springdoc）的后端代码质量。

## Scope

`apps/backend/src/main/java/com/obsidianmind/` 全部代码与 `application.yml`。

## When To Use

- 新增 / 修改 Controller、Service、Repository、DTO、Domain、Parser、Config、Exception。
- 新增依赖、配置项。

## When NOT To Use

- 前端代码（01）；API 契约语义（03）；RAG 链路特有规则（04 叠加本 Skill）。

## Rules

### 1. Java 语言层

- **命名**遵循阿里巴巴 Java 开发手册（本 skill 不复述细节，评审按手册强制级执行）。
- **Optional**：返回值可能为空用 `Optional`，字段与参数不用 Optional。
- **Stream / Collection**：映射过滤用 Stream；复杂多步逻辑优先普通循环，可读性 > 炫技。返回集合用不可变视角（`List.of` / `List.copyOf`）。
- **Record**：DTO 与不可变值对象用 record（现有 `ChatRequest` / `ConnectVaultRequest` 等均如此）；有行为的领域模型可用普通类。
- **Exception**：业务异常继承 `BusinessException`（携带语义化 code）；禁止 `catch (Exception e) {}` 与吞异常；捕获后必须记录或转译。
- **Immutability**：领域对象优先不可变；工具类构造器私有（`VaultPaths` 模式）。
- **Concurrency**：共享可变状态最小化；后台任务（索引）用有界线程池，禁止裸 `new Thread`。
- **资源管理**：文件 / 流一律 try-with-resources。

### 2. Spring 分层

- **Controller**：薄。参数校验（`@Valid`）→ 调 Service → 返回 DTO；不写业务逻辑，不返回 Entity/Domain。URL 以 `/api/v1` 开头，Swagger 注解（`@Tag` / `@Operation`）齐全。
- **Service**：业务编排唯一归属。接口 + `impl/` 的模式仅在存在多实现或需要边界时使用（现有 `LLMService` / `EmbeddingService` 有 Ollama 实现，保留接口；单实现无边界需求的类直接是类，如 `Chunker`）。
- **Repository**：当前 `VaultRepository`（文件系统）与 `VectorRepository`（接口，Milvus 探测预留）。文件操作必须经过 `VaultPaths.sanitizeRelative` + `resolveSafe`。
- **DTO**：按域分包（`dto/note/ chat/ search/ system/ vault/`），record 优先；对外字段 = 前端契约，不得随手暴露内部 Domain。
- **Domain**：`Note` / `Chunk` / `Document` / `Embedding` / `Source` / `ChatMessage` / `Vault` 等核心模型；无数据库，Domain 不加 JPA 注解。

### 3. Exception Handling

- 统一由 `GlobalExceptionHandler`（`@RestControllerAdvice`）处理：`BusinessException` 按 code 映射 HTTP 状态；校验失败 → 400 `INVALID_REQUEST`；未知异常 → 500 `INTERNAL_ERROR` 且**不泄漏堆栈**。
- 新业务错误：新建 `BusinessException` 子类（语义化命名，如 `VaultAccessDeniedException`）→ 在 `statusOf` 补映射 → 由对应 Service 抛出。
- 禁止在 Controller try/catch 吞掉业务异常。

### 4. Logging

- SLF4J + 占位符；`log.warn` 业务异常、`log.error` 未预期异常（带异常对象）。
- 禁止 `System.out.println` / `printStackTrace`；日志不输出用户笔记内容与 Secret。

### 5. Configuration

- 配置进 `application.yml`，可覆盖项用 `${ENV_VAR:default}`；新增 `@ConfigurationProperties` 类归入 `config/`（参考 `AiProperties` / `MilvusProperties` / `ObsidianProperties` / `CorsProperties`）。
- Secret 不进 Git；新增环境变量必须同步 README 环境变量表。
- **外部依赖不可用不得阻塞启动**：Ollama / Milvus 超时短（3s / 2s），健康探测失败只降级不崩溃（`HealthService` 模式）。

### 6. Dependency Injection

构造器注入（现有一律如此）；禁止字段 `@Autowired`。

### 7. Transaction

项目无数据库，**禁止无脑加 `@Transactional`**；未来若引入持久层，事务只加在真正需要边界的 Service 方法。

### 8. API 框架约束（Boot 3 特有）

- Boot 3 PathPattern 下 `{*id}` 通配变量必须在路径**末尾**；笔记读写因此使用 `GET/PUT /api/v1/notes/{*id}` 而 backlinks 用 `GET /api/v1/notes/backlinks?note=`（查询参数），新增含路径变量的端点时同样注意。

## Patterns

- `GlobalExceptionHandler` + `BusinessException` 家族：统一错误信封 `{code, message, timestamp}`。
- `VaultPaths`：路径安全工具（拒绝绝对路径 / `..` / 反斜杠 / 盘符 / `~`）。
- `ChatController` 的 SSE 骨架：`SseEmitter` + phase/message/done 事件，Phase 2 换真实实现时契约不变。
- MockMvc 集成测试（`ApiControllerIntegrationTest`）作为 API 契约的活文档。

## Anti-Patterns

- Controller 里写解析 / 检索 / AI 调用逻辑。
- `catch (Exception e) { return null; }` 或吞异常后继续。
- 硬编码 Ollama URL / 模型名 / TopK 在业务代码里（必须走 `AiProperties`）。
- 引入 JPA / 数据库 / 消息队列（项目方向明确无数据库）。
- 为单实现抽接口导致跳转地狱。

## Checklist

- [ ] Controller 薄、`@Valid` 校验、Swagger 注解齐
- [ ] 业务异常有专属类型与 HTTP 映射
- [ ] 文件路径经过 VaultPaths 校验
- [ ] 配置外置 + README 同步
- [ ] 构造器注入、无 System.out、无吞异常
- [ ] `mvn test` 全绿（当前基线 37/37）

## Related Skills

00-coding-standards、03-api-design（端点契约）、04-ai-rag-engineering（LLM/Embedding 服务）、05-testing（测试策略）、07-security（路径 / Secret）。

## Project-specific Notes

- 本机无全局 mvn：使用 `~/.m2/wrapper/dists` 内的 Maven 3.9.16（脚本 `scripts/start-backend.sh` 已处理）。
- 沙箱内无法实机 HTTP 冒烟 → 后端验收基准是 **MockMvc 集成测试**，不是 curl。
- 无数据库、无 Spring AI（Phase 2 再评估）；`LLMService` / `EmbeddingService` / `VectorRepository` 是刻意保留的 Provider 边界。

## Status

active

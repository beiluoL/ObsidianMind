# 00-coding-standards

## Purpose

ObsidianMind 所有代码修改的基础规范。无论前端、后端还是配置，先满足本 Skill，再叠加领域 Skill。

## Scope

仓库内所有源码、配置、脚本、文档。

## When To Use

- 任何 `git diff` 会出现的修改。
- 新增文件、目录、依赖、配置项。

## When NOT To Use

- 纯文档错别字修正（仍需遵守命名与 Git 规则，但不涉及代码规则）。

## Rules

### 1. Existing Code First

修改之前必须先读现有代码。项目已有的写法（构造器注入、DTO record、异常体系、错误信封）优先于个人偏好。禁止为一个小需求重构整个模块。

### 2. Naming

- 前端组件 PascalCase（`FileTree.vue`），composable / 工具 camelCase（`useTheme`），常量 UPPER_SNAKE_CASE。
- Java 类 UpperCamelCase，方法 / 变量 lowerCamelCase，常量 UPPER_SNAKE_CASE（遵循阿里巴巴 Java 开发手册）。
- API 路径 kebab-case 的小写名词（`/api/v1/vault/connect`）。
- 命名说清"是什么/干什么"，禁止 `data2`、`handleIt`、`temp` 这类无信息名。

### 3. File Organization

- 前端按职责分目录：`views/ components/{ui,layout,knowledge,vault,ai,theme}/ stores/ services/ types/ router/ assets/styles/`，新文件放进既有归属，不为单个文件新建顶层目录。
- 后端按分层包组织：`controller/ service/(impl) repository/ domain/ dto/{域}/ parser/ config/ exception/ util/`。
- Monorepo 顶层只保留既有的 `apps/ infrastructure/ scripts/ docs/ tests/ .agents/`。

### 4. Small Functions & Single Responsibility

一个函数做一件事；超过 ~50 行先考虑拆分。一个类一个变更理由。Controller 不写业务逻辑，Service 不直接操作 HttpServletRequest/Response 细节。

### 5. Avoid Unnecessary Abstraction / Premature Optimization

- 不为"未来可能"引入接口、策略模式、泛型。
- 单一实现且无第二实现计划的类，不抽接口（现有例外：`LLMService` / `EmbeddingService` / `VectorRepository` 是为 Ollama→其他 Provider 预留的边界，保留）。
- 性能问题先测量（见 08-performance），不凭感觉优化。

### 6. Error Handling

- 后端：业务错误抛 `BusinessException` 子类（带 code），由 `GlobalExceptionHandler` 统一转为 `{code, message, timestamp}` 信封；禁止 `catch (Exception e) {}`、禁止吞异常、禁止把堆栈返回给前端。
- 前端：`apiFetch` 统一抛错；组件必须处理 Loading / Empty / Error / Success 四态；禁止裸 `.catch(() => {})` 把失败伪装成空数据。

### 7. Logging

- 后端用 SLF4J（`LoggerFactory`），占位符 `{}` 而非字符串拼接。
- 禁止 `System.out.println` / `e.printStackTrace()`（当前代码库为零，保持为零）。
- 日志不得出现：API Key、Token、Vault 内用户笔记内容、完整路径中的用户名。

### 8. Comments & TODO

- 注释解释"为什么"，不复述"是什么"。公共 API（Controller、Service 接口、复杂工具类）写 JSDoc / Javadoc。
- 中文注释是本项目惯例，保持一致。
- TODO 必须带上下文：`// TODO(Phase 3): 接入 Ollama Embedding`。禁止无主 TODO 堆积；不提交被注释掉的死代码。

### 9. Configuration & Environment Variables

- 可变参数一律走配置：后端 `application.yml` + `${ENV_VAR:default}`；前端 `VITE_*` 环境变量 + `import.meta.env`。
- 新增配置项必须同步更新 `README.md` 的环境变量表。
- Secret（API Key / Token / Password）只存在于环境变量，禁止写入任何被 Git 跟踪的文件、测试、日志（用户红线：key 只留本机 `~/.zshrc`）。

### 10. Dependency Management

- 新增依赖必须给出理由：解决什么问题、为什么现有依赖不够。
- 前端依赖进 `package.json`（npm，锁定 package-lock.json）；后端进 `pom.xml`，优先 Spring Boot 官方 starter 与 BOM 管理版本。
- 禁止引入与项目方向无关的重型依赖（ORM、消息队列、微服务框架——本项目无数据库、单体架构）。

### 11. Dead Code

不提交未使用的导入、变量、函数、文件。删除优于注释掉。

### 12. Git-friendly Changes

- 一个 commit 一个意图；提交信息用中文约定式前缀（`feat(frontend):` / `fix(backend):` / `docs:` / `chore:`，与现有历史一致）。
- 修改后 `git diff` 必须自己能解释每一行。
- 本项目 Git 纪律：commit 可以做，**push 一律由用户决定**（此仓库另有 sync-git.sh 双远程脚本，仅用户手动触发）。

## Patterns

- 后端统一错误信封：`GlobalExceptionHandler` + `BusinessException` 子类（参考现有 9 个异常类）。
- 前端统一请求出口：`src/services/api.ts` 的 `apiFetch`，组件 / store 禁止硬编码后端地址。
- 配置外置：`ObsidianProperties` / `AiProperties` / `MilvusProperties` / `CorsProperties` 的 `@ConfigurationProperties` 模式。

## Anti-Patterns

- 为套用模板破坏已工作的代码（规范优先级见 AGENTS.md 冲突章节）。
- "顺手"引入新框架 / 新分层 / 新抽象。
- 复制粘贴另一处代码而不是提取（出现第二次重复时才提取）。
- 把调试输出留在提交里。

## Checklist

- [ ] 读过要改的文件及其调用方
- [ ] 命名、目录归属符合既有结构
- [ ] 无新依赖，或有充分理由
- [ ] 错误路径处理完整（前端四态 / 后端异常映射）
- [ ] 无 System.out / printStackTrace / 调试残留
- [ ] 无 Secret 入库；新增配置已同步 README
- [ ] `git diff` 每一行都能解释

## Related Skills

01-frontend-engineering / 02-java-spring-boot / 03-api-design（领域细则）、07-security（Secret 与路径）、06-code-review（自审）。

## Project-specific Notes

- 本项目**无数据库**，Markdown 文件系统即存储层；不要引入 JPA / MyBatis 相关规范。
- 现有分支 `main`，远程 origin/gitee 仅在用户明确要求时同步。

## Status

active

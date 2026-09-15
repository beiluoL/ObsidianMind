# 05-testing

## Purpose

约束 ObsidianMind 的测试策略：优先单元测试，MockMvc 集成测试作为后端验收基准，RAG 链路必须测行为而非只测状态码。

## Scope

后端 `apps/backend/src/test/`（JUnit 5 + spring-boot-starter-test + MockMvc）；前端测试（当前不存在，见 Rules）。

## When To Use

- 新增 / 修改任何业务逻辑时同步写或改测试。
- 修复 bug 时先写复现测试。

## When NOT To Use

- 纯样式、纯文案改动。

## Rules

### 1. 测试金字塔

```text
        E2E（暂无，Playwright 实测为手工验收手段）
      Integration（MockMvc 全链路 + @WebMvcTest）
         Unit（Service / Parser / Util 为主力）
```

默认写单元测试；API 契约与异常映射用 MockMvc 集成测试；不为了"E2E 覆盖率"堆 UI 自动化。

### 2. Java 单元测试

优先覆盖：Service（`VaultService` / `NoteService` / `SearchService` / `ChatService` / `IndexService`）、Parser（`MarkdownParser` / `FrontmatterParser` / `WikiLinkParser`）、Chunker、Embedding 封装、Util（`VaultPaths`）。

- 现有框架：JUnit 5 + Mockito（spring-boot-starter-test 自带），不引入 TestNG / Spock。
- 外部依赖（Ollama / Milvus / 文件系统边界）一律 mock 或用临时目录（`@TempDir`），单元测试不依赖网络与真实 Vault。
- 命名 `XxxTest`，与被测类同包（现有结构）；方法名表达场景与期望。

### 3. 集成测试（MockMvc）

`ApiControllerIntegrationTest` 模式：起 Spring 上下文 + MockMvc，测 Controller → Service 全链路的请求 / 响应契约与错误信封。新增端点必须在集成测试登记 happy path + 至少一个错误路径。**本项目验收基准 = `mvn test` 全绿**（沙箱无法实机 HTTP 冒烟）。

### 4. RAG 测试（Phase 2+ 强制）

**禁止只测"接口返回 200"**。必须验证 Query → Retrieval → Sources 行为链，并覆盖以下异常场景：

- 空 Vault / 空 Query
- 无匹配 / 低相关性（应拒答而非硬答）
- 重复文档 / 重复 Chunk
- Markdown 异常（畸形 frontmatter、超长行、二进制混入）
- Embedding 失败 / Ollama 不可用 / Milvus 不可用 / LLM 超时（应 503 + 明确 code，不静默空结果）

Sources 测试点：返回的 path 可定位到真实文件、heading / chunk 元数据完整。

### 5. 前端测试（当前缺口，诚实声明）

项目当前**没有**前端单元测试框架（vitest 未引入）。规则：

- 不在本 Skill 里假装存在前端测试流程；`npm run build`（vue-tsc 严格类型检查）是当前唯一自动化门禁。
- 引入 vitest 属于用户决策；引入后最低要求：service 层（mock 数据分支）、store 状态机、组件四态（Loading / Empty / Error / Success）。
- UI 行为验证当前以 **Playwright 实测**为验收手段（Vite dev + managed node workspace 的 playwright，`NO_PROXY=127.0.0.1`），覆盖主题三态、弹窗交互、多分辨率无溢出——这是已验证过的既定做法。
- 功能验收清单（手工 / Playwright 皆可）：Vault 导入、知识树、Chat、Sources、主题切换。

### 6. 测试数据

- 共享 fixture 放 `tests/fixtures/demo-vault/`（已有多主题 Markdown），后端测试用临时目录复制 fixture，不直接引用仓库内路径写死。
- fixture 里的 Markdown 保持真实 Obsidian 语法（frontmatter + wikilinks + 代码块），能覆盖 Parser 的全部分支。

## Patterns

- `VaultPathsTest`：工具类边界条件全覆盖（绝对路径 / `..` / 反斜杠 / 盘符 / `~` / 空串）。
- `ApiControllerIntegrationTest`：契约 + 错误信封断言（`code` 字段存在且正确）。
- `MarkdownParserTest`：解析器分支测试（含当前未提交修改——见 Git 状态，属进行中工作）。

## Anti-Patterns

- 测试里 mock 被测对象自己。
- 断言只检查"没抛异常"。
- 单元测试依赖真实 Ollama / Milvus 在跑。
- 为了覆盖率写无断言的空壳测试。
- 修复 bug 不写回归测试。

## Checklist

- [ ] 新逻辑有对应单元测试，外部依赖已 mock
- [ ] 新端点有 MockMvc happy path + error path
- [ ] RAG 相关改动覆盖 §4 异常场景
- [ ] `mvn test` 全绿（基线 37/37，只增不减）
- [ ] `npm run build` 零错误（前端当前门禁）
- [ ] UI 改动做过 Playwright / 手工四态验证

## Related Skills

02-java-spring-boot、01-frontend-engineering（四态）、03-api-design（契约断言）、04-ai-rag-engineering（RAG 场景）。

## Project-specific Notes

- 沙箱约束：后端以 MockMvc 为准；前端 Playwright 实测需 ESM 脚本放进 managed node workspace 目录（ESM 不认 NODE_PATH）。
- 当前测试基线：后端 37 个测试（单元 + MockMvc）。

## Status

active（前端自动化测试部分为 planned，见 §5）

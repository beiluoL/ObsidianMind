# 测试规则（Testing Rules）

> 人读版。执行细则与 Checklist 见 `.agents/skills/05-testing/SKILL.md`。

## 策略：金字塔

Unit（主力：Service / Parser / Chunker / Util，外部依赖 mock 或 `@TempDir`）→ Integration（MockMvc 全链路，API 契约与错误信封的活文档）→ E2E（暂无自动化；UI 行为以 Playwright 实测为验收手段）。

## 验收基线（当前事实）

- 后端：`mvn test` 全绿，基线 **37/37**（单元 + MockMvc），只增不减。
- 前端：`npm run build`（vue-tsc 严格类型检查）零错误——当前唯一自动化门禁。
- 前端单元测试框架（vitest）**尚未引入**，是否引入由用户决策；引入后最低覆盖：service 层、store 状态机、组件四态。

## RAG 测试铁律（Phase 2+）

禁止只测"接口返回 200"。必须验证 Query → Retrieval → Sources 行为链，并覆盖：空 Vault / 空 Query / 无匹配（应拒答）/ 低相关 / 重复文档 / Markdown 异常 / Embedding 失败 / Ollama 不可用 / Milvus 不可用 / LLM 超时。Sources 断言：path 可定位真实文件、heading/chunk 元数据完整。

## 工程约束

- 测试数据用 `tests/fixtures/demo-vault/`（复制到 `@TempDir`，不写死仓库路径）。
- 修 bug 先写复现测试；新端点登记 happy path + error path 两条 MockMvc 用例。
- 沙箱无法实机 HTTP 冒烟 → 后端以 MockMvc 为准；前端 Playwright 实测注意 `NO_PROXY=127.0.0.1`，ESM 脚本须放进 managed node workspace 目录。

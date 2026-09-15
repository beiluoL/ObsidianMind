# ObsidianMind Engineering Audit v1.0

> 审计日期：2026-09-15。方式：对代码库真实扫描（结构 / 配置 / 关键实现 / 依赖 / Git），结合 `.agents/skills/` 各 Skill 规则逐项核对。**本审计只记录问题，不做大规模重构**（按任务约定，仅 P0 类问题允许立即修复；本次未发现 P0）。

## Overall Score

| 维度 | 得分 | 依据 |
| --- | --- | --- |
| Architecture | 8/10 | Monorepo 结构清晰、分层正确、Provider 边界接口预留；扣分：文档 README 中 "Spring AI" 与实际（自有接口 + Ollama 实现）不一致 |
| Frontend | 7/10 | Design Token 体系完整、主题三态、API 统一出口、Vault 弹窗四态齐备；扣分：多个视图超 400 行、无 lint、无单元测试 |
| Backend | 8/10 | 构造器注入、统一异常信封、路径安全工具、配置外置、类规模健康（最大 280 行）；扣分：SSE 无界线程池 |
| API | 8/10 | /api/v1 前缀统一、DTO record + @Valid、错误信封稳定、SSE 事件契约清晰、docs/api 有索引表；扣分：契约同步点未在 CI 层面强制（靠纪律） |
| AI/RAG | 5/10 | Parser / Chunker / Ollama 服务 / SSE 骨架已落地且质量好；但 Retrieval → Context → Answer → Sources 核心链路未实现（Phase 2 前属预期，非缺陷） |
| Testing | 4/10 | 后端 37/37（单元 + MockMvc）且覆盖错误路径；前端零自动化测试、无 lint；RAG 行为测试尚未开始（随 Phase 2） |
| Security | 8/10 | Secret 全环境变量化、gitignore 分层完备、VaultPaths 路径防线、未授权访问语义化拒绝、CORS 可收敛；扣分：v-html 无消毒（P1）、Agent/MCP 写权限规范仅停留在文档 |
| Code Quality | 7/10 | 无 System.out / printStackTrace / TODO 残留（扫描为零）、命名一致、注释充分；扣分：6 个前端文件超 400 行 |

---

## Critical Issues

### P0（必须立即解决）

无。扫描未发现 Secret 泄露、明显安全漏洞、无法运行或破坏构建的问题。

---

## High Priority

### P1

**P1-1：Markdown 渲染 `v-html` 无消毒（XSS 风险）**
- 位置：`apps/frontend/src/components/ai/ChatMessage.vue:34`、`apps/frontend/src/views/NoteView.vue:223`、`apps/frontend/src/views/SearchView.vue:131`
- 事实：三处 `v-html` 直接渲染 `marked()` 输出；全前端扫描无 `DOMPurify` / 任何 sanitize 调用。
- 影响：Vault 内笔记是 untrusted 内容（用户可能导入他人分享的 Markdown），`<script>` / `onerror` 等载体可执行。
- 建议：引入 `dompurify`，在 `marked()` 封装处统一消毒后再 `v-html`（一处封装，三处受益）。属安全类，可按规范"明显安全漏洞"类别安排尽快修复，但涉及新增依赖，**建议由用户确认后执行**。

**P1-2：前端零 lint / 零自动化测试**
- 事实：无 ESLint / Prettier / Stylelint 配置（package.json 无 lint script）；无 vitest 等测试框架。当前门禁仅 `vue-tsc`。
- 影响：AI Agent 高频产码场景下，风格漂移与回归只能靠人工评审拦截。
- 建议：引入 ESLint（vue3 + typescript-eslint + vue/no-v-html 规则）+ Prettier；vitest 最低覆盖 service 层与 store。涉及新增依赖与配置，由用户拍板。

---

## Medium Priority

### P2

- **P2-1：前端巨型文件**：`SettingsView.vue` 613、`NoteView.vue` 606、`KnowledgeGraph.vue` 605、`vaultRepository.ts` 541、`SearchView.vue` 447、`HomeView.vue` 411 行，超过 Skill 01 的 ~400 行指导线。建议在对应功能下次迭代时顺势拆分，不专门重构。
- **P2-2：README 与实现不一致**：README Architecture 图写 "Spring AI"，实际为自有 `LLMService` / `EmbeddingService` 接口 + Ollama 实现（pom 无 Spring AI 依赖）。建议下次动 README 时修正（本次任务不重构业务代码，但文档修正已列入待办）。
- **P2-3：前端测试策略缺失的落地**：Skill 05 §5 已写明策略，但 Playwright 实测流程未脚本化沉淀，依赖会话记忆。建议把既有验证步骤（主题三态 / 弹窗 / 多分辨率）固化为脚本。
- **P2-4：`mock/notes.ts` 846 行**：Mock 数据文件，功能无害但体积大；后续接入真实后端后可裁剪。

---

## Low Priority

### P3

- **P3-1：`ChatService` 使用 `Executors.newCachedThreadPool()`**（无界线程池）。单用户本地场景风险低，但与 Skill 08 "有界线程池"规则不符；建议 Phase 6 实现真实 Streaming 时改为有界池 + 队列策略。
- **P3-2：`SearchView.vue` 中存在 `eslint-disable-next-line vue/no-v-html` 注释**，但项目并未安装 ESLint——注释指向不存在的规则，属误导性残留。
- **P3-3：错误信封无 `requestId`**：当前无日志追踪需求，记录为"待有需求再评估"（Skill 03 已声明，非缺陷）。

---

## Technical Debt

| 项 | 说明 | 建议偿还时点 |
| --- | --- | --- |
| Mock Chat / Mock services | 前后端 Mock 是设计产物；真实链路就绪后需按签名替换并裁剪 | Phase 2~6 逐个替换 |
| SSE 骨架 | phase/message/done 事件已定，真实 RAG 填充 | Phase 6 |
| VectorRepository 仅健康探测 | 完整索引 / 检索待实现 | Phase 4 |
| README Spring AI 表述 | 见 P2-2 | 下次文档更新 |
| 无 lint / 无前端测试 | 见 P1-2 | 用户决策后引入 |

---

## Recommended Next Steps（按优先级）

1. **P1-1**：确认后引入 DOMPurify 统一消毒 `v-html`（安全类，优先）。
2. **P1-2**：确认后引入 ESLint + Prettier（+ vitest 可第二步）。
3. **P2-2**：修正 README 架构图中的 Spring AI 表述（纯文档，低风险）。
4. **P2-1**：下次迭代各巨型视图时顺势拆分（不专门重构）。
5. **P2-3**：固化 Playwright 验证脚本。
6. **P3**：随 Phase 6 Streaming 实现一并处理线程池；清理 SearchView 的 eslint 残留注释。
7. Phase 2 启动时：按 Skill 04 实施 Retrieval → Context → Sources，并补齐 RAG 行为测试（Skill 05 §4）。

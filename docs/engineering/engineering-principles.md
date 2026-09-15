# 工程原则（Engineering Principles）

> 给人看的决策与背景文档。给 AI Agent 的执行规则在 `.agents/skills/00-coding-standards/SKILL.md`，两者不重复展开。

## 定位

ObsidianMind 是**个人项目 + AI 工程学习项目**：Local-First 个人知识库（Obsidian 知识树 + ChatGPT 对话 + Perplexity Sources）。工程规范的目标是让 AI 写出更稳定、更专业、更可维护的代码——**不是制造更多文件、不是过度工程化**。

明确不引入（除非代码真的需要）：Nx / Turborepo、复杂 CI/CD、Kubernetes、几十个 Git Hook、复杂 DDD、微服务、Event Sourcing、不必要的数据库（本项目无数据库）、消息队列。

## 五条核心原则

1. **Local First** —— 用户主动选择 Vault；不自动扫描未授权目录；外部依赖（Ollama / Milvus）不可用不得阻塞应用启动。
2. **Markdown Source of Truth** —— 知识只存在 Vault 文件里；Milvus 只是可重建的索引；索引与原文冲突以文件为准。
3. **Security First** —— Secret 零容忍入库；路径访问必须过 `VaultPaths`；Vault 内容视为 untrusted（Prompt 注入 / XSS 防线）。
4. **Small Changes / Existing Project First** —— 最小修改；先理解再动手；已有模式优先于通用最佳实践。
5. **Verify Before Done** —— 验证命令真实执行；后端 MockMvc 基线（当前 37/37）只增不减；前端 `npm run build` 零错误。

## 三层文档体系（职责边界）

| 层 | 位置 | 读者 | 内容 |
| --- | --- | --- | --- |
| 总入口 | `AGENTS.md`（仓库根） | AI Agent | 路由 + 流程 + 门禁，不写细节 |
| 执行规范 | `.agents/skills/` | AI Agent | 可执行、可检查的规则与 Checklist |
| 人读文档 | `docs/engineering/` | 维护者（人） | 背景、决策原因、演进记录 |

规则：三者不重复展开同一条规则；修改 SKILL.md 时检查本目录是否需要同步，反之亦然。

## 规范冲突时的优先级

Security > 已工作的架构 > 项目需求 > 已有测试 > 项目编码规范 > 新 Skill > 外部通用最佳实践。

## 技术决策记录（为什么是现在这样）

- **无数据库**：Vault 文件系统即存储层，Markdown 是 Source of Truth；引入 DB 反而制造双源。
- **未引 Spring AI（截至 Phase 1）**：自有 `LLMService` / `EmbeddingService` 接口 + Ollama 实现已满足需求且边界更清晰；Phase 2 接 RAG 时再评估。
- **Mock 服务是设计而非欠账**：前端 service 层 Mock 化让 UI 与数据层解耦，后端就绪时按签名替换（aiService 即将走这条路径）。
- **MockMvc 作为后端验收基准**：开发沙箱无法实机 HTTP 冒烟，集成测试承担契约验证。
- **.gitignore 必须锚定 `/Vault/`**：macOS `core.ignoreCase=true` 时未锚定规则会连 `src/components/vault/` 一起忽略（真实踩坑记录）。

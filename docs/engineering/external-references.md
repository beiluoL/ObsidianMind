# 外部参考（External References）

> 记录本套 Skills 参考了哪些外部工程知识、借鉴了什么、为什么这样取舍。原则：**只提取思想与规范，不整仓复制、不大段原文复制**。参考对象为通用工程实践（阿里 Java 手册、Apple HIG / WCAG、社区 RAG 工程经验等），均经过项目技术栈相关性判断与冲突检测后才写入。

| Source | Area | Adopted | Adapted / 未采用说明 |
| --- | --- | --- | --- |
| 《阿里巴巴 Java 开发手册》（强制级规约） | Java | Yes（命名 / 异常 / 日志 / 并发 / 集合） | 项目直接沿用；跨项目长期约定（用户级规范），本机评审按强制级执行 |
| Spring Boot 官方最佳实践（@ConfigurationProperties / 构造器注入 / @RestControllerAdvice） | Spring | Yes | 按项目现有 `AiProperties` / `GlobalExceptionHandler` 模式落地，未引入 profile 体系等当前不需要的部分 |
| SpringDoc / OpenAPI 注解实践 | API | Yes | 仅用 `@Tag` / `@Operation`；未生成 SDK / 契约测试框架（个人项目过重） |
| Vue 3 官方风格指南 + Composition API 最佳实践 | Frontend | Yes | 组件行数阈值按项目现状定为 ~400 行；状态三分类（UI/App/Server）为项目自定归纳 |
| Apple HIG / WCAG 2.1 AA | Accessibility | Partial | 键盘 / 焦点 / ARIA / 对比度进入 Skill 01；完整 WCAG 审计流程未采用（超出个人项目预算） |
| 社区 RAG 工程经验（chunking 策略 / TopK 预算 / citation 后端生成 / prompt 注入防线） | RAG | Yes（思想） | 全部项目化为 Skill 04 规则；未采用任何特定框架（LangChain / LlamaIndex / LangGraph——本项目自有接口 + Ollama） |
| OWASP（Top10 / LLM Top10 中相关条目） | Security | Partial | 路径穿越 / XSS / Prompt Injection / Secret 管理进入 Skill 07；未引入安全扫描流水线（当前无 CI） |
| Google Test Pyramid / MockMvc 实践 | Testing | Yes | 金字塔策略 + MockMvc 契约测试；未引入 Testcontainers（无 DB）、未引入前端 E2E 框架（以 Playwright 手工实测替代） |
| Measure-first 性能方法论 | Performance | Yes | 流程进入 Skill 08；所有具体阈值标注为"触发条件"而非强制项 |

## 未采用的外部模式及原因

| 模式 | 未采用原因 |
| --- | --- |
| JPA / MyBatis 等持久层规范 | 项目无数据库，Markdown 文件系统即存储 |
| React / Next.js 前端规范 | 技术栈是 Vue 3，引入即冲突 |
| Elasticsearch / 向量库多后端抽象 | 当前只有 Milvus 一个目标；`VectorRepository` 接口已预留边界 |
| LangGraph / 复杂 Agent 框架 | Agent / MCP 属 Phase 11+，且已预立"Read-only 默认"规则 |
| Turborepo / Nx monorepo 工具链 | 两应用规模远不需要 |
| 复杂 CI/CD 流水线 | 本地个人仓库，验证以本地命令为准（mvn test / npm run build） |

## 维护约定

后续引入新外部参考时追加到表格，并注明：来源、借鉴内容、直接采用 or 项目适配、理由。发现某条外部规范与项目现状冲突时，按 AGENTS.md 优先级裁决，并在此文件记录 Conflict / Reason / Decision。

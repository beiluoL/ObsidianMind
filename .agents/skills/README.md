# ObsidianMind Engineering Skills v1.0

本目录是 ObsidianMind 项目自己的工程规范体系（Project-Specific Engineering Standards）。
所有规范都基于对当前代码库的真实扫描生成，不包含与本项目技术栈无关的内容。

## 总览与依赖关系

```text
                    AGENTS.md（总入口 / 路由）
                            │
                            ▼
                 00-coding-standards（基础）
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
01-frontend          02-java-spring      03-api-design
   │                    │                   │
   └────────────────────┼───────────────────┘
                        ▼
                 04-ai-rag-engineering（核心链路）
                        │
           ┌────────────┴────────────┐
           ▼                         ▼
      05-testing                 07-security
           │                         │
           └────────────┬────────────┘
                        ▼
                 06-code-review
                        │
                        ▼
                 08-performance
```

**依赖关系的完整说明**：

1. **00-coding-standards 是所有代码修改的基础**。无论改前端、后端还是 API，命名、错误处理、小函数、配置管理等基础规则一律适用，因此它位于依赖树根部。
2. **01 / 02 / 03 是具体工程实现规范**。前端组件、Java 服务、API 契约三条线并行，各自只约束自己的领域。
3. **04-ai-rag-engineering 建立在 02 和 03 之上**。RAG 链路是 Java 后端实现的、通过 API 暴露的，因此必须先满足 Java 与 API 规范，再叠加 RAG 特有规则（Chunking、Embedding、Citation、Prompt 安全）。
4. **05-testing 验证各层行为**。前端、后端、RAG 的测试策略分别依附于对应层的实现规范，测试金字塔决定"优先单元、按需集成"。
5. **07-security 横向覆盖所有层**。Secret 管理、路径安全、Prompt Injection 防护不属于任何单层，是横切关注点。
6. **06-code-review 在实现之后进行统一质量检查**。它消费前面所有 Skill 的规则，转化为逐项 Checklist。
7. **08-performance 最后介入**。先测量、再定位、后优化；禁止在无证据时提前优化。

## Skill 路由速查

| 任务类型 | 必读 Skill |
| --- | --- |
| 任何代码修改 | 00 |
| 前端组件 / 状态 / 主题 / 可访问性 | 00 + 01 |
| Java / Spring Boot / 后端服务 | 00 + 02 |
| 新增 / 修改 REST 端点 | 00 + 03 + 02 |
| RAG / Embedding / Chunk / Chat / Sources | 00 + 02 + 03 + 04 |
| 写测试 / 修测试 | 00 + 05（+ 对应实现层 Skill） |
| 完成功能后的自审 | 06 |
| 涉及 Secret / Vault / 路径 / Prompt | 07（横切，任何时候优先级最高） |
| 性能问题 / 优化 | 08（先测量） |

## Skill 索引

| Skill | 领域 | Status |
| --- | --- | --- |
| [00-coding-standards](./00-coding-standards/SKILL.md) | 通用编码规范 | active |
| [01-frontend-engineering](./01-frontend-engineering/SKILL.md) | Vue 3 前端工程 | active |
| [02-java-spring-boot](./02-java-spring-boot/SKILL.md) | Java 17 / Spring Boot 3.5 | active |
| [03-api-design](./03-api-design/SKILL.md) | REST API 契约 | active |
| [04-ai-rag-engineering](./04-ai-rag-engineering/SKILL.md) | AI / RAG 链路 | planned（部分已落地） |
| [05-testing](./05-testing/SKILL.md) | 测试策略 | active |
| [06-code-review](./06-code-review/SKILL.md) | 代码评审 | active |
| [07-security](./07-security/SKILL.md) | Local-First 安全 | active |
| [08-performance](./08-performance/SKILL.md) | 性能优化 | optional |

## Status 约定

- **active**：当前代码已实际使用该规范，修改相关代码必须遵守。
- **planned**：领域存在且是项目路线图的一部分（如 RAG 完整链路），但实现尚未完成；做相关 Phase 开发时按此规范执行，不得假装已实现。
- **optional**：规范成立但当前无度量数据支撑，仅在出现真实瓶颈时启用。

## 与其他文件的关系

- `AGENTS.md`（仓库根）：AI Agent 的总入口，负责路由到这些 Skill。
- `docs/engineering/`：同样规则的**人读版**文档，不与本目录重复展开，只做背景与决策记录。
- 修改任一 SKILL.md 时，检查 `docs/engineering/` 对应文件是否需要同步。

# ObsidianMind Comment Audit v1.0

> 审计日期：2026-09-15。原则：代码负责解释 WHAT，注释只负责 WHY / CONTEXT / CONSTRAINT / TRADE-OFF / NON-OBVIOUS BEHAVIOR。不追求注释覆盖率，只追求关键设计意图可理解（依据 `.agents/skills/00-coding-standards/SKILL.md` §8）。

## Audit Principles

- 删除测试法：若删掉注释开发者仍能正确理解设计意图，则不加 / 删掉。
- 注释不得制造错误事实：与代码行为冲突时以代码为准修注释。
- 中文注释为项目惯例，技术术语保留英文。

## Files Reviewed

- **Backend**（全部 38 个 Java 源文件 + 33 个测试）：`Chunker`、`KnowledgeIndexService`、`OllamaEmbeddingService`、`MilvusVectorStore`、`MarkdownParser`、`VaultPaths`、`VaultRepository`、`AiProperties`、domain 模型、测试类。
- **Frontend**：`services/`（api / settingsService / aiService / repositories / fs / searchService / knowledgeService）、`stores/`（theme / vault / knowledge）、`views/`、`components/ui|knowledge|vault`、`index.html`。
- **Infrastructure / Scripts**：`docker-compose.yml`、`infrastructure/milvus/docker-compose.yml`、`scripts/index-demo-vault.sh`。
- 排除：`node_modules`、`target`、`dist`、生成文件。

## Comments Added

1. `infrastructure/milvus/docker-compose.yml` — MinIO `minioadmin` 默认凭据的边界说明（仅限本机开发，禁止暴露公网）。属于 P0 安全类 CONSTRAINT 注释。

## Comments Updated

1. `services/repositories/vaultRepository.ts` `connect()` — 原翻译型注释「验证可读权限」升级为 WHY：只拦截明确 `denied`，`prompt` 状态放行（首次读取时浏览器自动请求授权）。原注释既复述代码又掩盖了真正的非直观行为。

## Comments Removed

1. `views/SearchView.vue` `onMounted` — 「进入页面自动聚焦搜索框」纯翻译型（下一行即 `.focus()`）。

## Important Design Comments（已存在、审计确认保留）

- `Chunker`：边界优先级（heading > 围栏 > 表格 > 段落 > 行）、offset 不变量 `substring(start,end)==content`、超限代码块整块独立、重叠只取普通段落行。
- `KnowledgeIndexService`：编排职责边界、错误归账原则（每文件有且仅一条错误）、hash 单一事实源、MVP 同步执行的理由。
- `MilvusVectorStore`：client 惰性建连的原因（SDK 构造即建连，不得阻塞启动）、upsert=文档级 replace 防幽灵 Chunk、表达式转义防注入。
- `OllamaEmbeddingService`：批量契约（顺序一致/维度基准）、30s 超时宽于健康探测的原因（冷启动）、日志不输出向量。
- `VaultPaths` / `VaultRepository`：Local-First 授权边界、路径穿越防护。
- `index.html`：FOUC 防闪主题内联脚本的三态语义。
- 测试注释：均为行为约束型（如「重试恰好 1 次：共 2 次请求」），符合极少数原则。

## Remaining Areas

- `KnowledgeGraph.vue` 的「斥力 / 弹簧」短注释保留：对不熟悉 d3-force 自定义力的人有区块导航价值。
- `aiService.ts` / `settingsService.ts` 头注释的「未来替换为 / 其余仍为 Mock」描述与代码事实一致，Phase 5 接真实 RAG 时需同步更新（已在 Phase 4 报告遗留项中记录）。

## Risks

- 无注释与代码行为不一致的硬伤；无 `TODO/FIXME/HACK` 残留（源码范围）。

## Recommendations

- 后续新代码沿用现状基线：类级 WHY、公共契约 Javadoc、私有方法默认无注释。
- Phase 5 实现 RAG Answer 时，把 `aiService.ts` 与 `ChatService` 的 Mock 注释改为真实契约描述，避免注释过期。

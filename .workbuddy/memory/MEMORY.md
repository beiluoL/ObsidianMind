# ObsidianMind 项目长期记忆

## 技术栈与结构
- 前端：Vue 3.5 + TS strict + Vite 6 + Pinia + Vue Router(hash) + CSS Design Tokens；5 个 service 全 Mock 化，UI 与数据层解耦。
- 后端（2026-09-12 起）：**backend/ 目录，Spring Boot 3.5.16 + Java 17 + Maven**（本机无全局 mvn，用 ~/.m2/wrapper/dists 内的 3.9.16）。包结构 com.obsidianmind.{controller,service,repository,domain,dto,parser,config,exception,util}。
- AI：自有 LLMService/EmbeddingService 接口 + Ollama 实现（未引 Spring AI，Phase 2 再评估）；Milvus 仅健康探测 + VectorRepository 接口设计。
- 无数据库；Markdown 文件系统 = Source of Truth。
- **Theme 体系（2026-09-13）**：`src/assets/styles/` 四件套 tokens.css(骨架) / theme-dark.css(:root 默认) / theme-light.css([data-theme=light]) / base.css；加载顺序 tokens→dark→light→base。主题三态 system/light/dark，store `src/stores/theme.ts`，key=`obsidianmind-theme`，index.html 内联脚本防 FOUC。组件禁硬编码 hex/rgba；实心按钮用 `--primary-solid`+`--on-primary`（对比度 AA），强调/描边用 `--primary`。弹窗统一走 `components/ui/AppModal.vue`（ESC/遮罩/焦点陷阱/data-autofocus）。详见 docs/development/design-system.md。
- **Vault 弹窗组件**：`components/vault/{VaultConnectModal,VaultPicker,LocalFirstNotice,VaultWelcome(兼容入口)}`；拖拽目录走 `DataTransferItem.getAsFileSystemHandle()`（仅 Chromium）→ `vaultRepository.connectExternal` → `vaultStore.connectHandle`；`vault.connecting||progress.scanning` 时按钮 Loading。

## 硬约束
- Note id = Vault 相对路径；扫描只收集元数据；保存 = 原子写回原文件。
- 搜索 score 必须诚实标注 TEXT_MATCH，不得伪装向量相似度。
- Ollama/Milvus 不可用不得阻塞应用启动。
- Boot 3 PathPattern：`{*id}` 必须在路径末尾 → backlinks 用 `GET /notes/backlinks?note=`。
- 沙箱内无法对本地端口实机 HTTP 冒烟（透明代理 AUTH_REQUIRED，SERVER__PORT 被注入覆盖）→ 后端以 MockMvc 集成测试为验收基准；**前端可用 Vite dev + managed-node workspace 里的 playwright 实测**（NO_PROXY=127.0.0.1 需设置；ESM 不认 NODE_PATH，脚本要放进 workspace 目录）。
- **本机 Bash 的 `grep` 不可靠**（部分 pattern 静默无输出/exit 1），查代码一律用 Grep 工具。
- **git：本地仓库（main，无远程，永不 push）**；Phase 1 首提交 77ea2f6。
- **.gitignore 的 `Vault/` 已改为 `/Vault/`**：macOS core.ignoreCase=true 时未锚定规则会连 `src/components/vault/` 一起忽略（曾导致新组件不入库）。新增顶层目录若与忽略规则同名需检查。
- 主题默认 `system`：用户显式选择后写入 localStorage；改主题色只动 theme-dark.css / theme-light.css。

## 验证基线
- 后端：`mvn test` 37/37（单元 + MockMvc 全链路）；`mvn package` 产出可执行 jar。
- 前端：`npm run build`（vue-tsc + vite）0 错误。
- UI 改造附加验证（2026-09-13 已通过）：Playwright 实测 Light/Dark/System、刷新持久化、390~1440 五档分辨率无横向溢出、ESC/遮罩关闭、演示 Vault 连接。

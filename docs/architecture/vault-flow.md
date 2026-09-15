# Vault 数据流：从 Obsidian 目录到知识树

> Phase 2 架构说明。本阶段为 **Read-only Vault Integration**（只读接入）：系统读取、解析、展示用户的 Markdown，绝不修改原始文件。

## 一、数据流总览

```text
用户主动选择 Vault 目录
        ↓
浏览器 File System Access API（用户授权，拒绝即不读）
        ↓
Markdown Scanner（vaultRepository.scan：只扫 .md，跳过 .obsidian / node_modules / 缓存目录）
        ↓
Markdown Parser（parseMarkdown：frontmatter / tags / wiki links / 标题）
        ↓
结构化 Document（VaultFileMeta：path / title / tags / frontmatter / size / mtime）
        ↓
Pinia Store（knowledgeStore：tree + notes）
        ↓
Knowledge Tree（侧边栏文件树） + NoteView（marked 渲染正文）
```

## 二、流程的完整文字描述（面试讲稿版）

用户首先**主动选择**一个 Obsidian Vault 目录——这是整个系统的安全前提：浏览器不允许网页凭一个路径字符串就读取本地文件，因此我们使用 File System Access API，由用户在系统目录选择器中显式授权，或把文件夹拖入页面拿到目录句柄；未经授权的目录一个字节都不会被读取，也绝不自动扫描 Documents、Desktop 等任何位置。

授权后，**Markdown Scanner** 从目录句柄递归遍历，只收集 `.md` 文件，并跳过 `.obsidian`（Obsidian 的配置目录，不属于知识正文）、`.git`、`.trash` 以及 `node_modules`、`cache`、`build` 等缓存/构建目录。扫描阶段即完成第一次解析：每个文件读出文本后立刻交给 **Markdown Parser**，解析出 frontmatter、标题（frontmatter title → 正文首个 `#` 标题 → 文件名三级兜底）、tags（frontmatter 列表与正文行内 `#tag` 合并去重）和 `[[Wiki Link]]`，最终沉淀为结构化的 Document 元数据（vault 相对路径作为唯一 id、标题、标签、大小、mtime），**原始 Markdown 文件本身不发生任何改动**——ObsidianMind 是只读的知识组织层，写入能力只服务于后续用户显式保存的场景。

结构化 Document 进入前端 **Pinia 状态层**，由 `buildTree()` 按 vault 的真实目录结构构建 Knowledge Tree 渲染到侧边栏；点击笔记时才按需懒加载正文，交给 marked 渲染。目录句柄与扫描时间持久化在 IndexedDB 中，应用重启后可恢复连接（浏览器安全模型要求重新授权一次）。单个文件读取失败只计数不中断（成功 127 / 失败 1 依然可用），完全为空的目录会得到「没有发现 Markdown 文件」的明确提示。

**为什么 Markdown 不经过后端入库？** 因为本项目的第一原则是 **Local-first + Markdown 是 Source of Truth**：原始知识永远只存在于用户自己的 `.md` 文件里，Milvus 只是检索索引，后端（Spring Boot）是为 RAG 阶段准备的独立服务，通过显式连接 Vault 路径来读取同一份文件。即使将来 Milvus 数据损坏、后端不可用，用户的原始 Markdown 与前端知识树也完全不受影响——这正是「前端文件系统直读 + 后端按需索引」双通道设计的意义：读路径不依赖任何服务，索引路径随时可重建。

## 三、与后端的分工

| 关注点 | 前端（本阶段） | 后端（Phase 3+） |
| --- | --- | --- |
| 文件读取 | File System Access API 直读 | 显式连接的 Vault 路径（`/api/v1/vault/connect`） |
| 元数据/正文 | 扫描即解析、按需懒加载 | `/api/v1/notes/{*id}` 读取与原子写回 |
| 存储真相 | 用户磁盘上的 `.md` 文件 | 同一份 `.md`（不复制、不另存） |
| 索引 | 无（仅内存 Map） | Milvus 向量索引（可损毁可重建） |
| 通信 | — | Phase 3+ Chat/RAG 走 REST/SSE |

> 设计原因：浏览器 Web App 的安全模型不允许后端凭用户输入的路径读文件；而本应用后端运行在同一台本机（local-first 桌面化部署），因此前端走浏览器授权直读、后端走本机路径直读，两者读的是同一份文件，天然一致，无需同步协议。

## 四、关键代码位置

| 环节 | 位置 |
| --- | --- |
| 目录选择/拖入/恢复授权 | `apps/frontend/src/services/repositories/vaultRepository.ts`（pick / connectExternal / restore） |
| 扫描 + 忽略目录 + 单文件容错 | 同上 `scan()` |
| Markdown 解析 | `apps/frontend/src/services/fs/markdown.ts` |
| 状态机与进度 | `apps/frontend/src/stores/vault.ts` |
| 文件树 | `apps/frontend/src/components/knowledge/FileTree.vue` + `AppSidebar.vue`（文件名过滤） |
| 正文渲染 | `apps/frontend/src/views/NoteView.vue`（marked） |
| 后端 Parser（RAG 预留） | `apps/backend/src/main/java/com/obsidianmind/parser/` |

## 五、边界与不做的事

- **不做**：Embedding、Milvus、向量检索、RAG、Reranker、Agent、MCP（后续 Phase）
- **不做**：自动扫描未授权目录；修改/重写用户 Markdown；要求 `.obsidian` 必须存在（含 Markdown 的目录即有效 Vault）
- **保留**：frontmatter / tags / links / headings（标题层级）等结构化信息，为下一阶段 `Markdown → Section → Chunk` 直接复用

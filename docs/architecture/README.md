# ObsidianMind 系统架构

## 总览

```text
ObsidianMind
     │
     ├── Frontend（apps/frontend）
     │     Vue 3.5 + TypeScript(strict) + Vite 6 + Pinia + Vue Router
     │     文件树 / Markdown 工作区 / AI Chat / 知识图谱 / 全文搜索
     │
     └── Backend（apps/backend）
             │
             ├── REST API（/api/**，Spring Boot 3.x）
             │
             ├── Spring AI（Phase 2+ 接入）
             │
             ├── Ollama（本地 LLM / Embedding，infrastructure/ollama）
             │
             └── Milvus（向量索引，infrastructure/milvus）
```

## 核心原则：Markdown 是 Source of Truth

> **Markdown 是知识源的 Source of Truth（唯一事实来源），Milvus 只是检索索引，不作为原始知识的最终存储。**

- 所有笔记的读取、编辑、保存都直接发生在用户的 Obsidian Vault（文件系统）上
- Embedding / 向量数据只是 Markdown 内容的**派生索引**，可随时全量重建
- 索引与原文不一致时，以 Markdown 文件为准
- 后端无数据库：Vault 文件系统即存储层（Phase 1 起）

## 数据流（目标态）

```text
Markdown（Vault，Source of Truth）
   ↓ Parser（frontmatter / wiki links / heading）
Chunk
   ↓ Ollama Embedding Model
Milvus（向量索引，可重建）
   ↓ Retriever（Phase 8: Hybrid Search / Phase 9: Rerank）
RAG Context
   ↓ LLM（Ollama Chat）
Streaming Answer + Sources（引用回指 Markdown 原文）
```

## 目录职责

| 目录 | 职责 |
| --- | --- |
| `apps/frontend` | 用户界面，浏览器端 Vault 连接（File System Access API / 后端 API 双轨） |
| `apps/backend` | Vault 扫描/读写、搜索、索引管线、AI 问答（Phase 1 已含 REST API 骨架） |
| `infrastructure/` | Milvus / Ollama / Docker 配置，本地运行数据不入 Git |
| `tests/fixtures/demo-vault` | 测试用 Markdown 数据（真实 Vault 严禁入库） |

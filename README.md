# ObsidianMind

> **Local-first AI Personal Knowledge Base**
> Obsidian 的知识树 + ChatGPT 的 AI 对话 + Perplexity 的 Sources

核心原则：**Markdown 是知识源的 Source of Truth（唯一事实来源）**，Milvus 只是检索索引，不作为原始知识的最终存储。

## Architecture

```text
Frontend (Vue 3 + TypeScript + Vite)
   ↓  REST / SSE（/api）
Spring Boot Backend (Java 17+, Spring Boot 3.x)
   ↓
Spring AI
   ↓
Ollama（本地 LLM + Embedding） + Milvus（向量索引）
```

## Project Structure

```text
ObsidianMind/
├── apps/
│   ├── frontend/          # Vue 3.5 + TS strict + Vite 6 + Pinia
│   └── backend/           # Spring Boot 3.x + Maven（Phase 1 REST API）
├── infrastructure/
│   ├── docker/            # Docker 相关配置
│   ├── milvus/            # Milvus standalone 官方 compose（etcd + MinIO + Milvus）
│   └── ollama/            # Ollama 配置与说明
├── scripts/               # dev.sh / start-frontend.sh / start-backend.sh
├── docs/
│   ├── architecture/      # 架构文档
│   ├── api/               # API 文档
│   └── development/       # 开发指南
├── tests/
│   └── fixtures/demo-vault/  # Obsidian Markdown 测试数据
├── docker-compose.yml     # 基础设施统一入口（Ollama + Milvus）
└── README.md
```

## Development

### 启动 Frontend

```bash
./scripts/start-frontend.sh
# 或手动：
cd apps/frontend && npm install && npm run dev
```

### 启动 Backend

```bash
./scripts/start-backend.sh
# 或手动：
cd apps/backend && mvn spring-boot:run
```

- 默认端口 `8080`，健康检查：`GET http://localhost:8080/api/health` → `{"status":"UP"}`
- API 文档（Swagger）：http://localhost:8080/swagger-ui.html

### 启动 Milvus

```bash
docker compose -f infrastructure/milvus/docker-compose.yml up -d
# 端口 19530（gRPC）/ 9091（metrics）
```

### 启动 Ollama

```bash
docker compose up -d ollama
# 或本机安装：ollama serve
# 拉取模型：ollama pull qwen3 && ollama pull qwen3-embedding
```

## Backend 环境变量（全部可覆盖，无任何硬编码密钥）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OBSIDIAN_VAULT_PATH` | 空（API 显式连接） | 默认 Vault 路径 |
| `OLLAMA_BASE_URL` | http://localhost:11434 | Ollama 地址 |
| `OLLAMA_CHAT_MODEL` | qwen3 | Chat 模型 |
| `OLLAMA_EMBEDDING_MODEL` | qwen3-embedding | Embedding 模型 |
| `MILVUS_HOST` / `MILVUS_PORT` | localhost / 19530 | Milvus 地址 |
| `CORS_ALLOWED_ORIGINS` | 本地前端来源 | 生产必须显式收敛 |

Frontend 环境变量见 `apps/frontend/.env.example`（`VITE_API_BASE_URL`）。

## Frontend UI / Design System

所有颜色统一走 Theme Token，组件内禁止硬编码 `hex` / `rgba`。

```text
apps/frontend/src/assets/styles/
├── tokens.css        # 与主题无关的骨架变量：字体 / 间距 / 圆角 / 布局 / 动效 / 层级
├── theme-dark.css    # 深色配色（:root 默认 + [data-theme="dark"]）
├── theme-light.css   # 浅色配色（[data-theme="light"] 覆盖）
└── base.css          # reset + 通用元素 + focus-visible + Markdown 渲染
```

- **主题三态**：跟随系统 / 浅色 / 深色。设置入口「设置 → 外观」，顶栏提供快捷切换按钮。
- **持久化**：`localStorage['obsidianmind-theme']`，默认 `system`，刷新不丢失。
- **防闪烁（FOUC）**：`index.html` 内联脚本在任何样式执行前，按同一份 key 写入 `<html data-theme>`。
- **Vault 导入弹窗**：`components/vault/`（`VaultConnectModal` / `VaultPicker` / `LocalFirstNotice`），
  支持目录选择与拖入文件夹（`DataTransferItem.getAsFileSystemHandle()`，Chromium），带 Loading / 错误态。

## Roadmap

| Phase | 内容 |
| --- | --- |
| Phase 1 | 项目基础架构（Monorepo + REST API + Health Check）✅ |
| Phase 2 | Markdown Parser + Chunking |
| Phase 3 | Embedding（Ollama Embedding Model） |
| Phase 4 | Milvus 向量索引 |
| Phase 5 | RAG（检索增强问答） |
| Phase 6 | Streaming Chat（SSE） |
| Phase 7 | Sources / Citation |
| Phase 8 | Hybrid Search |
| Phase 9 | Reranker |
| Phase 10 | Related Notes |
| Phase 11 | Agent |
| Phase 12 | MCP |

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
| `OLLAMA_CHAT_MODEL` | qwen3 | Chat 模型（须为本机已安装模型；本机实测用 qwen3.5:9b） |
| `OLLAMA_EMBEDDING_MODEL` | bge-m3 | Embedding 模型（本机已安装，1024 维；换模型需同步 `MILVUS_VECTOR_DIMENSION` 并清空 Collection） |
| `AI_CHUNK_SIZE` / `AI_CHUNK_OVERLAP` | 800 / 100 | 切块上限与相邻块重叠（字符） |
| `AI_EMBEDDING_BATCH_SIZE` | 16 | 单次 Embedding HTTP 请求携带的 Chunk 数 |
| `AI_EMBEDDING_TIMEOUT_SECONDS` | 30 | Embedding 请求超时（冷启动需宽于健康探测） |
| `MILVUS_HOST` / `MILVUS_PORT` | localhost / 19530 | Milvus 地址 |
| `MILVUS_COLLECTION` | obsidianmind_chunks | 向量 Collection 名称（固定，惰性创建） |
| `MILVUS_VECTOR_DIMENSION` | 1024 | 向量维度（来源：bge-m3 / qwen3-embedding 实测，运行时校验） |
| `AI_RETRIEVAL_DEFAULT_TOP_K` / `AI_RETRIEVAL_MAX_TOP_K` | 5 / 20 | 语义检索默认召回数 / 上限 |
| `AI_RAG_MAX_CHUNKS` / `AI_RAG_MAX_CHARS` | 6 / 12000 | 进入 Prompt 的 Context 条目数 / 总字符上限（超限按相似度整体截断） |
| `AI_RAG_TEMPERATURE` / `AI_RAG_MAX_TOKENS` | 0.1 / 1024 | RAG 生成温度（贴资料取低值）/ 输出 token 上限 |
| `AI_RAG_THINK` | false | thinking 模型是否先推理再作答（关闭防 token 预算被推理耗尽） |
| `AI_RAG_LLM_TIMEOUT_SECONDS` / `AI_RAG_STREAM_TIMEOUT_SECONDS` | 120 / 180 | LLM 流式读超时 / SSE 整体生命周期超时 |
| `RETRIEVAL_DEFAULT_MODE` | HYBRID | 混合检索默认模式（VECTOR / KEYWORD / HYBRID，Phase 6） |
| `RETRIEVAL_RRF_K` | 60 | RRF 融合平滑常数（Cormack 2009 经典取值） |
| `RETRIEVAL_VECTOR_CANDIDATES` / `RETRIEVAL_KEYWORD_CANDIDATES` | 20 / 20 | RRF 前两路各自取回的候选数 |
| `RETRIEVAL_KEYWORD_K1` / `RETRIEVAL_KEYWORD_B` | 1.5 / 0.75 | BM25（Okapi）参数 |
| `RETRIEVAL_RERANKER_ENABLED` / `RETRIEVAL_RERANKER_TOP_N` | false / 20 | Reranker 开关与重排候选数（未接神经模型，默认关） |
| `RETRIEVAL_DEBUG_ENABLED` | false | Retrieval Debug 端点门禁（生产保持关闭） |
| `MODEL_CENTER_STORAGE_DIR` | ~/.obsidianmind | Model Center 配置与加密凭据存储目录（Provider/Model 配置 + AES-GCM 凭据 + 独立密钥） |
| `CORS_ALLOWED_ORIGINS` | 本地前端来源 | 生产必须显式收敛 |

Frontend 环境变量见 `apps/frontend/.env.example`（`VITE_API_BASE_URL`）。

## Knowledge Index（Phase 3）

一条命令验证索引管线（需后端与 Ollama 已启动）：

```bash
./scripts/index-demo-vault.sh           # 首次：全量 INDEXED
./scripts/index-demo-vault.sh --repeat  # 第二次：全部 SKIPPED（增量 hash 对比）
```

或前端入口：设置 → 索引 → 「立即同步知识库」（返回 indexed / updated / skipped / deleted / failed 计数）。
设计文档：`docs/architecture/knowledge-pipeline.md`、`embedding.md`、`milvus.md`。

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
| Phase 2 | Markdown Parser + Chunking ✅ |
| Phase 3 | Embedding（Ollama）+ Milvus 向量索引 + 增量索引 ✅ |
| Phase 4 | RAG 查询链路（Query → Vector Search → TopK Retrieval → Sources） |
| Phase 5 | Streaming Chat 正式版（SSE 接真实 RAG） |
| Phase 6 | Hybrid Search |
| Phase 7 | Reranker |
| Phase 8 | Related Notes |
| Phase 9 | Agent |
| Phase 10 | MCP |

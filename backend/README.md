# ObsidianMind Backend

Local First AI Personal Knowledge Base 后端（Phase 1）。

## 技术栈

- Java 17 + Spring Boot 3.5.x + Maven
- REST API + SSE（Chat 流式预留）
- Ollama（本地 AI，健康检查按需探测，未启动不阻塞应用）
- Milvus（未来向量索引，Phase 1 仅健康探测与 VectorRepository 接口设计）
- 无数据库：Markdown 文件系统 = Source of Truth

## 启动

```bash
cd backend
# 方式一：Maven
mvn spring-boot:run
# 方式二：Jar
java -jar target/obsidianmind-backend-0.1.0.jar
```

默认端口 8080，API 文档：http://localhost:8080/swagger-ui.html

## 环境变量（全部可覆盖）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `OBSIDIAN_VAULT_PATH` | 空（需 API 显式连接） | 默认 Vault 路径 |
| `OLLAMA_BASE_URL` | http://localhost:11434 | Ollama 地址 |
| `OLLAMA_CHAT_MODEL` | qwen3 | Chat 模型 |
| `OLLAMA_EMBEDDING_MODEL` | qwen3-embedding | Embedding 模型 |
| `MILVUS_HOST` / `MILVUS_PORT` | localhost / 19530 | Milvus 地址 |
| `CORS_ALLOWED_ORIGINS` | 本地前端来源清单 | 生产必须显式收敛 |

## API 一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/v1/system/status` | 应用 + Ollama/Milvus/Vault 健康状态 |
| GET | `/api/v1/vault` | Vault 元信息 |
| POST | `/api/v1/vault/connect` | 连接 Vault（校验存在/目录/可读） |
| POST | `/api/v1/vault/disconnect` | 断开 Vault |
| POST | `/api/v1/vault/scan` | 扫描（仅元数据，不读正文） |
| GET | `/api/v1/vault/files` | 文件树（folder/markdown/image/other） |
| GET | `/api/v1/notes/{*id}` | 读取笔记（id = Vault 相对路径） |
| PUT | `/api/v1/notes/{*id}` | 保存笔记（写回原 Markdown，防路径穿越） |
| GET | `/api/v1/notes/backlinks?note={id}` | 反向链接 |
| GET | `/api/v1/search?q=` | 全文搜索（TEXT_MATCH 评分，非向量） |
| POST | `/api/v1/index` | 触发索引任务（异步：解析→Chunk） |
| GET | `/api/v1/index/status` | 索引状态机 IDLE/SCANNING/INDEXING/READY/FAILED |
| POST | `/api/v1/chat` | 问答（Phase 1 Mock，结构为 RAG 预留） |
| POST | `/api/v1/chat/stream` | 流式问答（SSE：phase/message/done） |

注：PathPattern 语法要求 `{*id}` 位于末尾，故 backlinks 用查询参数形式（见 NoteController 说明）。

## 测试

```bash
mvn test   # 37 个用例：Parser / 路径安全 / Vault / Note / Search / 全链路集成
```

## Milvus（未来 Phase 2 使用）

```bash
docker compose -f docker-compose.milvus.yml up -d
```

## 安全原则

- 只访问用户显式连接的 Vault，绝不扫描 Home / 全盘
- 所有路径经过 `VaultPaths` 校验（拒绝 `../`、绝对路径、盘符、反斜杠）
- 日志不打印用户 Markdown 正文
- 无任何密钥硬编码；Secret 一律走环境变量

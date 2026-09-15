# 07-security

## Purpose

Local-First 应用的安全规范。用户把整个 Obsidian Vault 交给这个应用，安全是信任的根基。横切所有层，优先级最高。

## Scope

Secret 管理、文件系统访问、Prompt 注入、用户数据处理——覆盖前后端与配置。

## When To Use

- 任何涉及文件路径、环境变量、AI Prompt、用户 Markdown 的改动。
- 引入新依赖 / 新外部调用时评估。

## When NOT To Use

- 无。

## Rules

### 1. Secrets（红线，零容忍）

禁止进入 Git（提交与 diff 都不允许）：`.env`、API Key、Token、Password、Credential、真实用户数据。

- 环境变量默认值只允许非敏感本地默认（`http://localhost:11434` 这类）。
- key 只留本机环境变量（用户机器 `~/.zshrc`），禁止写进代码 / 测试 / 配置 / 日志。
- `.gitignore` 已覆盖 `.env*`（保留 `.env.example`）、`data/ volumes/ models/` 等；新增敏感目录先补 gitignore。
- 发现泄露：立即告知用户并停止相关提交（AGENTS.md 冲突优先级里 Security 第一）。

### 2. Vault 访问（Local-First 核心）

- **禁止自动扫描用户未授权目录**。Vault 只能通过用户显式动作连接（前端选目录 / `POST /vault/connect`），扫描只针对已连接的 Vault 根。
- 未连接 Vault 时，笔记类端点必须拒绝（`VAULT_NOT_FOUND` / `VAULT_ACCESS_DENIED`）。
- 扫描只收集元数据；保存 = 原子写回原文件，不另建副本。

### 3. Path Traversal

- 一切用户提供的相对路径必须经 `VaultPaths.sanitizeRelative`（拒绝绝对路径 / `..` / 反斜杠 / 盘符 / `~`）+ `resolveSafe`（resolve 后必须仍在 Vault 根内）。
- 新增任何接收路径参数的端点，禁止绕过该工具自行 `Paths.get()`。

### 4. Prompt Injection

Vault 的 Markdown 内容是 **untrusted input**：

- Chunk 内容只能进入 Prompt 中明确标注的"参考资料"区块，禁止拼进 system 指令区。
- 系统指令不得被检索内容覆盖（拼接顺序固定：system → 分隔的资料区 → 用户问题）。
- 指令里明确要求 LLM 忽略资料中出现的"指令式文本"（如"忽略以上规则"）。
- RAG 工具调用（未来 Agent）：从资料派生的动作默认拒绝执行。

### 5. Markdown 渲染（前端 XSS）

- 用户 Markdown 属 untrusted content：`v-html` 渲染 Markdown 前必须消毒（sanitize）。当前若存在直接 `marked()` → `v-html` 的路径，视为 P1 缺口（见审计报告），补 `DOMPurify` 或等价方案前不得扩大使用面。
- Markdown 中的指令文本永远不当作系统指令执行。

### 6. CORS 与网络

- CORS 白名单走 `CORS_ALLOWED_ORIGINS`，开发默认本地前端来源；生产必须显式收敛。
- 后端不主动外呼除 Ollama / Milvus（本地）之外的地址；未来接入远程 LLM Provider 时，地址与 key 全部配置化且出网日志可审。

### 7. 未来 Agent / MCP（预先立规）

- 默认 **Read-only**：Agent / MCP 工具只能读 Vault 与索引。
- 任何写操作（改文件、删文件、外发数据）必须 Explicit Approval（用户逐次确认），不得由模型自主决定。

## Patterns

- `VaultPaths`：路径安全唯一入口。
- `VaultAccessDeniedException` → 403：未授权访问的统一表达。
- `.gitignore` 分层注释结构：新增忽略规则带原因注释（`/Vault/` 锚定教训已写在注释里）。

## Anti-Patterns

- "临时"把 key 写进代码测试一下。
- 拼接用户路径后直接读文件。
- 把 Vault 内容原样拼进 system prompt。
- 为了功能顺滑自动扫描全盘找 Vault。
- 生产 CORS 用 `*`。

## Checklist

- [ ] 无新增 Secret 入库路径（diff / gitignore 双查）
- [ ] 新路径参数走 VaultPaths
- [ ] 未连接 Vault 的访问被正确拒绝
- [ ] 检索内容与 system prompt 隔离
- [ ] Markdown 渲染有消毒层
- [ ] 新外部调用有超时、地址可配置

## Related Skills

00-coding-standards（Secret 规则）、02（VaultPaths 实现）、03（403/404 语义）、04（Prompt 组装）、01（XSS / v-html）。

## Project-specific Notes

- 用户隐私敏感：涉及本地配置 / 权限的改动，先列清单请用户拍板，不擅自动手（用户长期偏好）。
- 本仓库无远程自动推送，Secret 入库的主要风险面在本地 diff 与未来可能的导出。

## Status

active

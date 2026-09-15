# 安全规则（Security Rules）

> 人读版。执行细则与 Checklist 见 `.agents/skills/07-security/SKILL.md`。

## 为什么安全在这个项目里权重最高

用户把整个 Obsidian Vault（可能包含工作笔记、私人记录）交给应用；Local-First 的信任根基一旦破坏不可逆。因此在规范冲突优先级里 Security 排第一，高于任何功能与架构考量。

## 六条防线

1. **Secret 零容忍**：API Key / token / password / `.env` 永不进 Git；key 只留本机环境变量；新增敏感目录先补 `.gitignore`。
2. **Vault 授权边界**：只有用户显式动作才能连接 Vault；禁止自动扫描未授权目录；未连接时笔记端点拒绝（403/404 语义化错误）。
3. **路径穿越防护**：所有用户提供路径过 `VaultPaths.sanitizeRelative` + `resolveSafe`，禁止绕过自行 `Paths.get()`。
4. **Prompt Injection**：Vault Markdown = untrusted；Chunk 只进 Prompt 资料区，与 system 指令隔离；检索内容派生的"指令"不执行。
5. **前端 XSS**：Markdown 渲染必须消毒后再 `v-html`（现状缺口见审计报告 P1）。
6. **未来 Agent / MCP 预立规**：默认 Read-only；写操作必须用户逐次 Explicit Approval。

## 红线操作约定

- 发现 Secret 泄露风险：立即停止相关提交并告知用户（唯一允许"顺手修"的类别之一）。
- 涉及本地配置 / 权限的改动：先列清单请用户拍板，不擅自动手。

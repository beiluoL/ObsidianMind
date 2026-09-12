# demo-vault — ObsidianMind 测试数据

仅用于开发与测试的演示 Obsidian Vault（与 `apps/frontend/src/services/repositories/vaultRepository.ts` 的 OPFS 演示播种同源）。

## 结构

```text
demo-vault/
├── AI/                # RAG / Embedding / Milvus / LLM / Agent / Transformer
├── Java/              # JVM 内存结构 / G1 / GC Roots / 并发 / CAS / Spring IOC 与 AOP
├── Projects/          # ObsidianMind.md
└── 01-个人成长/        # 读书笔记方法.md
```

覆盖的解析场景：frontmatter、tags、wiki links（`[[...]]`）、Markdown 表格、中文标题。

## 红线

- 这里只放**测试数据**，严禁把真实 Obsidian Vault 提交进 Git
- 新增笔记请同步更新 `vaultRepository.ts` 的 `DEMO_FILES` 播种表

---
title: ObsidianMind 项目
tags:
  - 项目
  - AI
---

# ObsidianMind

> Obsidian 的知识组织 + ChatGPT 的 AI 对话 + 本地优先 AI

## 产品定位

让 Obsidian 不只是记录知识，而是拥有一个可以理解、检索、关联和解释知识的 AI 大脑。

## 架构

```
Obsidian Vault（Markdown = Source of Truth）
      ↓
ObsidianMind UI（Vue3 + TS）
      ↓ REST / SSE
Spring Boot（Knowledge / Search / AI / Index Service）
      ↓
Spring AI → Ollama；Milvus → 向量检索
```

技术选型涉及 [[RAG]]、[[Embedding]]、[[Milvus]] 与 [[Agent]]。

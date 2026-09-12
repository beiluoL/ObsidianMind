---
title: RAG 检索增强生成
tags:
  - AI
  - RAG
  - 检索
status: learning
created: 2026-09-01
---

# RAG（Retrieval-Augmented Generation）

RAG 是一种**检索增强生成**技术：在 LLM 生成答案之前，先从外部知识库中检索相关内容，把检索结果作为上下文一起交给模型。

它解决两个核心问题：

1. 模型知识截止（不知道最新 / 私有知识）
2. 幻觉（编造不存在的事实）

## 工作流程

```
用户问题
   ↓
向量检索（Embedding 相似度）
   ↓
知识库 / 向量数据库（Milvus）
   ↓
Top-K 相关片段
   ↓
拼装 Prompt → LLM
   ↓
最终答案（带引用来源）
```

## 关键环节

| 环节 | 说明 | 常见方案 |
| --- | --- | --- |
| 分块 Chunking | 把长文档切成语义完整的片段 | 固定长度 / 递归 / 语义分块 |
| 向量化 | 文本 → 高维向量 | bge、Qwen-Embedding |
| 检索 | 相似度召回 Top-K | Milvus、HNSW |
| 重排 Rerank | 对召回结果精排 | bge-reranker |
| 生成 | LLM 基于上下文回答 | Qwen、DeepSeek |

## 与微调的区别

- RAG：知识外挂，更新成本低，可追溯引用
- 微调：改变模型行为，成本高，不可追溯

> 结论：知识类问题优先 RAG，行为类需求才考虑微调。

RAG 依赖 [[Embedding]] 把文本变成向量，检索通常由 [[Milvus]] 这类向量数据库完成，底层语言能力来自 [[LLM]]。

#RAG #学习笔记

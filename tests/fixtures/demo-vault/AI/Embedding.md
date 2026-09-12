---
title: Embedding 向量化
tags:
  - AI
  - Embedding
status: learning
---

# Embedding

Embedding 把文本映射成高维向量，语义相近的文本在向量空间中距离更近。

## 核心思想

- 文本 → 神经网络 → 固定长度向量（如 1024 维）
- 余弦相似度衡量语义相似程度
- 查询与文档必须使用**同一个** Embedding 模型

## 常见模型

| 模型 | 维度 | 说明 |
| --- | --- | --- |
| bge-m3 | 1024 | 中英双语，开源 |
| Qwen-Embedding | 1024 | 阿里出品 |
| text-embedding-3 | 3072 | OpenAI |

Embedding 是 [[RAG]] 与 [[Milvus]] 的基础：没有向量化就没有语义检索。

Transformer 架构（见 [[Transformer]]）是 Embedding 模型的技术底座。

---
title: Milvus 向量数据库
tags:
  - AI
  - Milvus
  - 向量数据库
---

# Milvus

Milvus 是开源向量数据库，专为海量向量的相似度检索设计。

## 核心概念

- **Collection**：类似关系库的表
- **Segment**：数据分片，检索的基本单元
- **Index**：HNSW / IVF 等近似最近邻索引

## 典型架构

```
应用 → Milvus SDK → Query Node
                        ↓
                  HNSW 索引
                        ↓
                  Top-K 相似向量
```

Milvus 在 [[RAG]] 链路中承担向量检索职责，召回的片段来自 [[Embedding]] 模型的向量化结果。

Agent（见 [[Agent]]）做记忆检索时也常使用 Milvus。

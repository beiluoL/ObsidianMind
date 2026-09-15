---
title: RAG
tags:
  - AI
  - 检索
---

# RAG

检索增强生成（Retrieval-Augmented Generation）：先从知识库检索相关片段，再让 LLM 基于片段回答。

## 基本流程

1. 文档切块并向量化入库
2. 查询向量化后做相似度检索
3. 命中片段作为上下文交给 LLM
4. 回答附带来源（Sources）

依赖 [[Embedding]] 产生的向量与 [[Token]] 上下文窗口概念。

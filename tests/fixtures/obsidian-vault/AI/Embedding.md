---
title: Embedding
tags:
  - AI
  - 向量
---

# Embedding

Embedding 把文本映射为固定维度的稠密向量，语义相近的文本在向量空间中距离相近。

## 与 Token 的关系

文本先经 Tokenizer 变为 Token，模型再整体编码为一个向量——向量化是语义压缩，不是逐 Token 查表。

参见 [[Token]]、[[RAG]]。

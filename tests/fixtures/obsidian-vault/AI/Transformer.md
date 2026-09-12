---
title: Transformer 架构
tags:
  - AI
  - 深度学习
---

# Transformer

Transformer 是现代大模型的基础架构，核心是**自注意力机制**。

## 注意力公式

```
Attention(Q, K, V) = softmax(QK^T / √d_k) · V
```

## 组件

- Multi-Head Attention
- Feed-Forward Network
- Layer Normalization + 残差连接

几乎所有 [[LLM]] 与 [[Embedding]] 模型都基于 Transformer。

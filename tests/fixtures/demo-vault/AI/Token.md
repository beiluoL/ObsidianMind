---
title: Token
tags:
  - AI
  - LLM
status: learning
created: 2026-09-02
---

# Token

Token 是 LLM 处理文本的**最小单位**：模型不直接读「字」，而是先把文本切成 token，再查词表映射成向量。

## 切分方式

1. **BPE**（字节对编码）：GPT 系列使用，高频词保留、低频词再切分
2. **WordPiece**：BERT 系列使用
3. **SentencePiece**：语言无关，中文场景友好

## 为什么重要

| 关心点 | 与 token 的关系 |
| --- | --- |
| 上下文窗口 | 模型能读的长度按 token 计（如 128K） |
| 计费 | API 价格按输入/输出 token 数计算 |
| 中文成本 | 通常 1 个汉字 ≈ 1~2 个 token |

## 与 Embedding 的关系

每个 token ID 经过 Embedding 层变成向量，语义相近的 token 向量距离更近，见 [[Embedding]]。

一个窗口内的 token 数量直接影响模型的理解质量与费用，规划 [[LLM]] 应用时必须预估 token 用量。

#AI #学习笔记

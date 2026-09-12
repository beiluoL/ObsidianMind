---
title: LLM 大语言模型
tags:
  - AI
  - LLM
---

# LLM（Large Language Model）

大语言模型是基于 Transformer 的生成式模型，通过预测下一个 token 完成文本生成。

## 推理参数

| 参数 | 作用 |
| --- | --- |
| temperature | 采样随机性，越高越发散 |
| top_p | 核采样截断 |
| max_tokens | 最大生成长度 |

LLM 是 [[RAG]] 的生成端，也是 [[Agent]] 的推理引擎。

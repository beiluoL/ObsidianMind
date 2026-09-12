---
title: Agent 智能体
tags:
  - AI
  - Agent
---

# Agent

Agent = LLM + 规划 + 工具 + 记忆。让模型从"回答问题"进化为"完成任务"。

## 基本循环

```
目标
 ↓
思考（LLM 推理）
 ↓
行动（调用工具）
 ↓
观察结果
 ↓
循环直到完成
```

## 关键组件

- **Planner**：任务拆解与规划
- **Tools**：函数调用 / MCP
- **Memory**：短期上下文 + 长期向量记忆（可用 [[Milvus]]）

Agent 的底层推理能力来自 [[LLM]]，记忆检索依赖 [[Embedding]]。

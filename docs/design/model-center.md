# Model Center UI 设计说明（Phase 5.5）

## 信息架构

```text
Settings
 ├─ AI 模型（ModelCenter.vue）     ← 本阶段主体
 │    ├─ 左：Provider 列表（图标 + 名称 + 状态）
 │    └─ 右：Provider 详情
 │         ├─ 连接配置（名称 / Base URL / API Key / 启用）
 │         ├─ [保存] [测试连接] + Model ID 输入（非 Ollama）
 │         ├─ Models：卡片列表 + 添加/编辑表单
 │         └─ 每卡：显示名 / Model ID / 能力 / 默认徽标 / 设默认·编辑·删除
 ├─ 知识库 / Embedding / 向量库 / 索引 / 外观 / 高级（Phase 1-3 原有）
Chat
 └─ 头部 [Provider / Model ▼]（ModelSelector.vue，Popover 切换）
```

## 状态设计

| 状态 | 位置 | 表现 |
| --- | --- | --- |
| Loading | Provider 列表 | spinner + 「加载中」 |
| Configured | Provider 项 | 绿点「已连接」（环境凭据标注「（环境变量）」） |
| Not Configured | Provider 项 | 灰点「未配置」 |
| Disabled | Provider 项 | 灰点「已禁用」 |
| Testing | 测试连接按钮 | spinner，禁用 |
| Success | 测试结果条 | `✓ 连接成功 · model · 842ms`（success 色） |
| Error | 测试结果条 | `✗ safe message（ERROR_CODE）`（danger 色），无技术细节轰炸 |
| Streaming | Chat 模型选择器 | disabled + title 提示「生成期间不可切换模型」 |

## 交互要点

- **保存保留原 Key**：编辑时 Key 输入框 placeholder 显示 `已配置（••••abcd）· 留空保留原 Key`；
  只有用户显式输入才随请求发送。
- **Ollama 特权**：显示「本地服务，无需 API Key」+「读取本机模型」按钮；其他类型明确提示
  手动填写 Model ID（不假装支持模型列表）。
- **默认模型**：全局唯一；模型卡上「设为默认」操作 + 默认徽标；Chat 选择器有「默认模型」项。
- **凭据来源透明**：`credentialSource`（USER_CONFIGURED / ENVIRONMENT / NONE）以状态文案呈现，
  绝不显示值。

## 安全呈现

- Key 输入框旁固定安全说明：「Key 加密存储于本机，仅用于调用你配置的模型服务；
  页面与 API 响应只显示遮蔽值。」——只陈述已实现的事实，不夸大。
- 前端任何类型/组件不持有 Key 原文字段；`ProviderView.apiKeyMasked` 是唯一可见形态。

## 主题

全部使用 Design Tokens（--surface / --border / --primary / --success / --danger 等），
Light / Dark / System 三态下均正确渲染，无硬编码颜色与阴影。

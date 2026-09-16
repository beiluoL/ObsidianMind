# 模型 Provider 配置指南

> ObsidianMind 支持 4 种 Provider 类型。配置入口：**设置 → AI 模型**。
> 本文档不包含任何真实 API Key；示例一律 `sk-xxxx`。

## Ollama（本地）

- Base URL：`http://localhost:11434`（默认播种）
- API Key：不需要
- Model：本机已安装的模型名（如 `qwen3.5:9b`）。可在 Provider 详情点「读取本机模型」
  从后端真实读取 `/api/tags`，不要凭记忆猜名字。
- 隐私：请求不出本机（前提是 Base URL 指向本机）。

## DeepSeek Direct

- Base URL：`https://api.deepseek.com`（OpenAI 兼容，官方文档为准）
- API Key：DeepSeek 开放平台申请（`sk-xxxx`），仅存本机加密文件
- Model（官方当前列表）：
  - `deepseek-v4-flash`——快速通用
  - `deepseek-v4-pro`——强推理
  - 旧名 `deepseek-chat` / `deepseek-reasoner` 已于 2026-07-24 弃用，不要再用
- 能力：CHAT / STREAMING（/ REASONING 按需声明）

## DashScope（阿里云百炼，兼容模式）

- Base URL：`https://dashscope.aliyuncs.com/compatible-mode/v1`（中国大陆；国际 /
  其他地域 Base URL 不同，以你的控制台为准）
- API Key：百炼控制台申请（`sk-xxxx`），即 DASHSCOPE_API_KEY——**不是** DEEPSEEK_API_KEY，
  两者不能混用
- Model：手动填写 Model ID（如 `deepseek-v4-flash`、`deepseek-v4-pro`、`qwen-max`）。
  第一版不提供「自动获取模型列表」——该接口不可靠时不假装支持。
- 能力：CHAT / STREAMING；思考型模型会返回 reasoning_content，系统自动忽略不进正文。

## OpenAI Compatible（自定义）

- Base URL：任意 OpenAI 兼容服务（如自建网关 / 其他厂商兼容端点）
- API Key / Model：按服务商文档填写
- 适合：SiliconFlow、vLLM 自部署、企业网关等。

## 测试连接

每个 Provider 详情页有 [测试连接]：

- Ollama：检查服务可达 + 模型已安装（不消耗 token）
- 其余：发送一个 `max_tokens=16` 的最小 chat 请求

结果只包含 success / model / latency / 语义化错误码（如 `AUTHENTICATION_FAILED` = Key 无效），
绝不包含 Key、Authorization 头或上游错误体原文。

## API Key 安全须知

- Key 以 AES-256-GCM 加密存于本机（`~/.obsidianmind/credentials.json` + 独立密钥文件），
  页面与 API 响应只显示 `••••abcd` 遮蔽值。
- 编辑 Provider 时 API Key 留空 = 保留原 Key；换 Key 保存后立即生效（旧客户端缓存失效）。
- 开发 / 测试可用环境变量 `DEEPSEEK_API_KEY` / `DASHSCOPE_API_KEY` 回退（对应类型专属，
  不可跨用）；环境凭据仅调用时读取，绝不写入存储。
- 严禁把 Key 提交进 Git 或写进文档。

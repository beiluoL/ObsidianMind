# infrastructure/ollama

Ollama（本地 LLM + Embedding）配置与说明。

## 使用

```bash
# 方式一：本机安装
ollama serve

# 方式二：Docker（根目录 compose 统一入口）
docker compose up -d ollama
```

## 模型

后端默认模型（可用环境变量覆盖，见 apps/backend/README.md）：

```bash
ollama pull qwen3              # Chat 模型
ollama pull qwen3-embedding    # Embedding 模型（Phase 3 使用）
```

## 注意

- 模型文件体积大，只存放在 named volume / 本地 `~/.ollama`，**严禁提交到 Git**（.gitignore 已覆盖 `ollama-data/`、`models/`）
- Ollama 未启动不阻塞后端应用，`/api/v1/system/status` 中对应组件显示 DOWN

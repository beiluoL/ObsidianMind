# Model Provider 架构（Phase 5.5）

> 本文档描述 ObsidianMind 的 AI Model Center：Provider / Model / Credential 三层分离，
> 以及 RAG 链路如何通过 ModelRouter 与具体 Provider 解耦。

## 1. 全景

```text
Settings → AI Models（前端 Model Center UI）
              │  REST /api/v1/ai/**
              ▼
      ModelCenterController（薄壳）
              ▼
      ModelCenterService（CRUD / 测试连接 / 默认模型）
              │
    ┌─────────┼──────────────────┐
    ▼         ▼                  ▼
ModelCenterStorage   CredentialStore      ModelRouter
（config.json）      （credentials.json     │ resolve(modelId)
 Provider + Model     + 独立密钥，AES-GCM）  ▼
              CredentialResolver（用户配置 > 环境变量 > NONE）
                                  ▼
                          ChatModelAdapter（complete / streamComplete）
                          ├─ OllamaChatModelAdapter（复用 Phase 5 OllamaLLMService）
                          └─ OpenAiCompatibleChatModelAdapter（DeepSeek / DashScope / 自定义）
                                  ▼
                          RagAnswerService（RAG 编排，Phase 5 不变）
                                  ▼
                          ChatService → SSE → 前端
```

## 2. 联系描述（为什么这样设计）

1. **Provider 与 Model 分离**：Provider 只描述「连哪里、怎么认证」（baseUrl / type / enabled），
   Model 是其下的具体模型条目（modelName / capabilities / isDefault）。这样新增一个 Provider 类型
   只是加一个枚举值，而不是复制一整套 Config 类；同一 Provider 下可挂任意多模型。
2. **凭据与配置分离**：`config.json`（Provider/Model）不含任何敏感信息，可以随意备份；
   `credentials.json` 独立存放且 AES-GCM 加密，解密密钥在另一个权限 600 的文件里。
   配置文件单独泄露 ≠ Key 泄露。
3. **CredentialStore 是接口**：MVP 用加密文件实现；未来换 OS Keychain / 数据库加密列时
   ModelRouter 与 CRUD 层零改动。
4. **CredentialResolver 三级来源**：USER_CONFIGURED > ENVIRONMENT > NONE。
   环境回退只允许白名单映射（DEEPSEEK→DEEPSEEK_API_KEY，DASHSCOPE→DASHSCOPE_API_KEY），
   禁止跨类型取用；环境凭据只在调用时解析，绝不持久化。
5. **ModelRouter 是 RAG 的唯一依赖**：RagAnswerService 不知道 DeepSeek/Ollama 的存在，
   只知道「modelId → ChatModelAdapter」。切换 Provider 不改 RAG 一行代码。
6. **legacy 回退保证零迁移**：Model Center 未配置默认模型时，路由回 Phase 5 的配置文件 Ollama
   路径——老用户的 RAG 行为完全不变。
7. **缓存 + 失效**：适配器按 (provider 配置 + 凭据指纹) 缓存；任何写操作 `router.invalidate()`，
   保证「换 Key → 旧客户端立即作废 → 新请求用新 Key」。
8. **Provider 差异止步于适配器**：Ollama 的 NDJSON、OpenAI 兼容系的 SSE 在适配器内部归一为
   `onToken` 回调；前端只见 Phase 5 的统一事件流（phase/citation/message/done/error）。

## 3. REST API

| Method | Path | 说明 |
| --- | --- | --- |
| GET | /api/v1/ai/providers | Provider 列表（含 apiKeyMasked / credentialSource） |
| POST | /api/v1/ai/providers | 新增 Provider |
| PUT | /api/v1/ai/providers/{id} | 更新（apiKey 留空 = 保留原 Key） |
| DELETE | /api/v1/ai/providers/{id} | 删除（级联删模型与凭据） |
| POST | /api/v1/ai/providers/{id}/test | 测试连接（返回 success/latency/语义化错误码） |
| GET | /api/v1/ai/providers/{id}/models | 真实可用模型（当前仅 Ollama /api/tags） |
| GET | /api/v1/ai/models | Model 列表 |
| POST | /api/v1/ai/models | 新增 Model |
| PUT | /api/v1/ai/models/{id} | 更新 Model |
| DELETE | /api/v1/ai/models/{id} | 删除 Model |
| POST | /api/v1/ai/models/{id}/default | 设为默认对话模型 |

Chat API（`/api/v1/chat`、`/chat/stream`）新增可选 `modelId`：客户端只能指定模型 ID，
apiKey / baseUrl 永不接受；凭据由后端解析。

## 4. 错误码

| code | HTTP | 场景 |
| --- | --- | --- |
| MODEL_NOT_FOUND / PROVIDER_NOT_FOUND | 404 | modelId / providerId 无效 |
| MODEL_DISABLED / PROVIDER_DISABLED | 409 | 已禁用仍被路由 |
| PROVIDER_NOT_CONFIGURED | 409 | 无凭据（且无环境回退） |
| INVALID_REQUEST | 400 | baseUrl 非法 / 能力声明不支持等 |
| NOT_SUPPORTED | 400 | 如对非 Ollama 请求自动模型列表 |
| PROVIDER_ERROR（测试连接结果内） | 200 | AUTHENTICATION_FAILED / MODEL_NOT_FOUND / RATE_LIMITED |

## 5. 持久化

- 目录：`model-center.storage-dir`（默认 `~/.obsidianmind`，可用 `MODEL_CENTER_STORAGE_DIR` 覆盖）。
- `model-center.json`：`{version, providers[], models[]}`，原子写（tmp + ATOMIC_MOVE）。
- `credentials.json`：`{providerId: {v: base64(iv‖ciphertext)}}`，AES-256-GCM；
  解密密钥 `.credential.key`（首次随机生成，POSIX 600）。
- 环境变量凭据绝不落盘（测试已断言）。

## 6. 测试

- 单测：`ModelCenterStorageTest`（播种/加密落盘/默认唯一/级联删除/遮蔽）、`ModelRouterTest`
  （路由/404/禁用/未配置/**换 Key 缓存失效**）。
- 真实 IT：`Phase55ProviderIT`（-Dprovider.it=1）：Ollama 真实 /api/tags + DashScope 真实
  Test Connection（环境凭据回退，绝不打印 Key、绝不写库）。
- 回归基线：`mvn test` 123 全绿（含 Phase 1-5 全部旧测试）。

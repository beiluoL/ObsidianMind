# 开发指南

## 环境要求

- Node.js ≥ 20 + npm（前端，包管理器固定为 npm）
- Java 17+（后端，当前 pom `java.version=17`；升级 Java 21 需同步本机 JDK）
- Maven（或使用 IntelliJ 内置 Maven；wrapper 后续补充）
- Docker（可选，仅 Milvus / Ollama 需要）

## 一键脚本

```bash
./scripts/dev.sh             # 同时启动前端(:5173) + 后端(:8080)
./scripts/start-frontend.sh  # 仅前端
./scripts/start-backend.sh   # 仅后端
```

## 验证命令

```bash
# 前端：类型检查 + 构建（必须零错误）
cd apps/frontend && npm install && npm run build

# 后端：37 个测试用例
cd apps/backend && mvn test

# 健康检查
curl http://localhost:8080/api/health   # {"status":"UP"}
```

## 开发约定

1. **Markdown 是 Source of Truth**，Milvus 只是可重建的检索索引
2. 前端 API 调用统一走 `src/services/api.ts`，禁止组件内硬编码后端地址
3. 安全红线：只访问用户显式连接的 Vault，绝不扫描 Home / 全盘；路径必须过 `VaultPaths` 校验
4. 密钥一律走环境变量，任何 Key / Token / 模型文件 / Vault 数据不入 Git

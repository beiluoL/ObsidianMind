# 脚本说明 / Scripts

本目录包含 ObsidianMind 的本地开发辅助脚本。

## 一、macOS 一键启停（双击即用）

| 文件 | 用途 | 双击行为 |
| --- | --- | --- |
| `start.command` | 一键启动前后端 | 自动打开终端窗口，依次启动后端 → 前端，并在窗口内显示状态 |
| `stop.command`  | 一键停止前后端 | 自动打开终端窗口，优雅终止服务，并回显停止结果 |

> `.command` 是 macOS 的原生「双击在终端运行」格式，无需手动打开终端敲命令。
> 首次双击若提示「无法打开，因为来自身份不明的开发者」：右键 → 打开 → 仍要打开；或在「系统设置 → 隐私与安全性」中允许一次即可。

### 启动顺序
1. Spring Boot 后端（默认 `:8080`，健康检查 `GET /api/health`）
2. Vue + Vite 前端（默认 `:5173`）

### 停止顺序
前端 → 后端；先优雅终止（SIGTERM），8 秒未退出再强制（SIGKILL）。

### 幂等处理（重复操作不报错）
- **重复启动**：若端口已被占用（服务已在运行），脚本会跳过该项并提示，不会启动重复实例。
- **重复停止**：若服务本就未运行，脚本提示「无需停止」，直接退出，不会因找不到进程而报错。

### 后台运行与日志
- 服务以 `nohup` 方式后台运行，关闭脚本窗口**不会影响**正在运行的服务。
- PID 与日志统一放在项目根的 `.run/` 目录（已加入 `.gitignore`）：
  - `backend.pid` / `frontend.pid`：进程号
  - `backend.log` / `frontend.log`：启动输出，排查问题时查看这里

## 二、终端 / CI 脚本（`.sh`）

| 文件 | 用途 |
| --- | --- |
| `start-frontend.sh` | 仅启动前端（复用 `apps/frontend`，npm 不变） |
| `start-backend.sh`  | 仅启动后端（自动 fallback 到 IntelliJ 内置 Maven） |
| `dev.sh`            | 同时启动前后端（控制台阻塞，Ctrl+C 结束） |

用法：

```bash
./scripts/start-frontend.sh
./scripts/start-backend.sh
./scripts/dev.sh
```

> 本机若 `mvn` 不在 PATH，脚本会自动尝试使用 IntelliJ IDEA 内置 Maven
> （`/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn`）。

## 三、环境变量（可选覆盖）

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `BACKEND_PORT`   | `8080` | 后端端口；若设置了 `SERVER_PORT` 则以其为准 |
| `FRONTEND_PORT`  | `5173` | 前端端口 |
| `SERVER_PORT`    | 未设置 | 等价于 `server.port`，Spring Boot 兼容写法 |

示例：

```bash
BACKEND_PORT=9090 ./scripts/start.command
```

## 四、真实端口解析

后端可能因 `SERVER_PORT` / `server.port` 覆盖而监听非默认端口。
启停脚本会从后端日志解析 `Tomcat started on port ...` 实际端口，
并对该真实端口做健康检查与清理，因此改端口后也能正确启停。

#!/bin/bash
# ============================================================
# ObsidianMind 一键启动（macOS 双击运行 / 终端运行均可）
#   · 启动顺序：Spring Boot 后端(:8080) → Vue 前端(:5173)
#   · 幂等：服务已在运行则跳过，不报错、不重复启动
#   · 关闭服务请双击 scripts/stop.command
# ============================================================
set -u

# ---------- 基础路径 ----------
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
RUN_DIR="$ROOT/.run"

# 同时兼容 Spring 的环境变量写法 SERVER_PORT（等价于 server.port）
BACKEND_PORT="${BACKEND_PORT:-${SERVER_PORT:-8080}}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

mkdir -p "$RUN_DIR"
cd "$ROOT" || exit 1

# ---------- 环境变量（双击运行时 GUI 不继承 shell 环境） ----------
export PATH="/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
[ -s "$HOME/.zshrc" ] && source "$HOME/.zshrc" >/dev/null 2>&1
[ -s "$HOME/.nvm/nvm.sh" ] && source "$HOME/.nvm/nvm.sh" >/dev/null 2>&1
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
  export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null)"
fi

# ---------- 工具函数 ----------

# 查找 Maven：优先 PATH，其次 IntelliJ 内置
find_mvn() {
  if command -v mvn >/dev/null 2>&1; then echo "mvn"; return; fi
  local idea="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
  if [ -x "$idea" ]; then echo "$idea"; return; fi
  echo ""
}

# 占用指定端口的进程 PID（只认 LISTEN 监听态，避免匹配到客户端连接；无则空）
port_pid() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -1
}

# 存活检测（PID 文件 + 进程存活）
alive() {
  local pid="$1"
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

# 从后端启动日志解析真实端口（用户覆盖 SERVER_PORT / server.port 时以日志为准）
# 注意：Spring Boot 3 的实际日志形如 "Tomcat started on port(s): 8080 (http)"
# 因此这里不能用 mvn -q 启动（-q 会压制 INFO 日志，导致此行根本不会写入日志）
# 兼容两种日志格式：
#   单端口：Tomcat started on port 18080 (http) with context path '/'
#   多端口：Tomcat started on port(s): 8080 (http)
resolve_backend_port() {
  local p
  p="$(grep -oE 'Tomcat started on port(\(s\))?:? [0-9]+' "$RUN_DIR/backend.log" 2>/dev/null \
        | tail -1 | grep -oE '[0-9]+$')"
  echo "${p:-$BACKEND_PORT}"
}

# 等待某个端口可访问，超时返回 1
wait_ready() {
  local port="$1" name="$2" tries="${3:-40}"
  for ((i = 1; i <= tries; i++)); do
    if [ -n "$(port_pid "$port")" ]; then
      if [ "$name" = "backend" ]; then
        if curl -s --noproxy '*' -m 2 "http://localhost:$port/api/health" 2>/dev/null | grep -q '"UP"'; then
          return 0
        fi
      else
        return 0
      fi
    fi
    sleep 1
  done
  return 1
}

echo "============================================================"
echo " ObsidianMind 一键启动"
echo " 后端 :$BACKEND_PORT   前端 :$FRONTEND_PORT"
echo " 项目目录：$ROOT"
echo "============================================================"
echo

# ---------- 1. 启动后端 ----------
echo "[1/2] 后端 Spring Boot"
if [ -n "$(port_pid "$BACKEND_PORT")" ]; then
  echo "  ⚠️  端口 $BACKEND_PORT 已被占用（PID $(port_pid "$BACKEND_PORT")），后端可能已在运行 → 跳过启动"
else
  MVN="$(find_mvn)"
  if [ -z "$MVN" ]; then
    echo "  ❌ 未找到 Maven，请先安装（brew install maven）或使用 IntelliJ 内置 Maven"
  else
    # 不加 -q：保留 Spring INFO 日志，stop/端口探测依赖其中的 "Tomcat started on port(s): N"
    nohup "$MVN" -f "$ROOT/apps/backend/pom.xml" spring-boot:run \
      > "$RUN_DIR/backend.log" 2>&1 &
    echo $! > "$RUN_DIR/backend.pid"
    echo "  启动中…（PID $(cat "$RUN_DIR/backend.pid")，日志：$RUN_DIR/backend.log）"
  fi
fi

# ---------- 2. 启动前端 ----------
echo
echo "[2/2] 前端 Vue + Vite"
if [ -n "$(port_pid "$FRONTEND_PORT")" ]; then
  echo "  ⚠️  端口 $FRONTEND_PORT 已被占用（PID $(port_pid "$FRONTEND_PORT")），前端可能已在运行 → 跳过启动"
else
  if ! command -v npm >/dev/null 2>&1; then
    echo "  ❌ 未找到 npm，请先安装 Node.js（https://nodejs.org）"
  else
    if [ ! -d "$ROOT/apps/frontend/node_modules" ]; then
      echo "  未检测到依赖，先执行 npm install（首次约 1-2 分钟）…"
      (cd "$ROOT/apps/frontend" && npm install > "$RUN_DIR/frontend-install.log" 2>&1)
    fi
    (cd "$ROOT/apps/frontend" && nohup npm run dev -- --host 127.0.0.1 \
      > "$RUN_DIR/frontend.log" 2>&1 & echo $! > "$RUN_DIR/frontend.pid")
    echo "  启动中…（PID $(cat "$RUN_DIR/frontend.pid" 2>/dev/null)，日志：$RUN_DIR/frontend.log）"
  fi
fi

# ---------- 3. 健康确认 ----------
echo
echo "等待服务就绪…"
REAL_BACKEND_PORT="$(resolve_backend_port)"
if [ "$REAL_BACKEND_PORT" != "$BACKEND_PORT" ]; then
  echo "  提示：后端实际监听 $REAL_BACKEND_PORT（由 SERVER_PORT / server.port 覆盖）"
fi
# 后端首次启动需 Maven 编译，等待窗口给足余量（真实机器重复启动通常几秒即可）
if wait_ready "$REAL_BACKEND_PORT" backend 90; then
  echo "  ✅ 后端就绪  http://localhost:$REAL_BACKEND_PORT/api/health"
else
  echo "  ⚠️  后端暂未就绪（仍在启动或 Maven 不可用），查看日志：$RUN_DIR/backend.log"
fi
if wait_ready "$FRONTEND_PORT" frontend 45; then
  echo "  ✅ 前端就绪  http://localhost:$FRONTEND_PORT"
else
  echo "  ⚠️  前端暂未就绪，查看日志：$RUN_DIR/frontend.log"
fi

echo
echo "============================================================"
echo " 完成。关闭服务请双击 scripts/stop.command"
echo " 提示：本窗口可直接关闭，不会影响正在运行的服务"
echo "============================================================"
echo
read -r -p "按回车键关闭此窗口…" _
exit 0

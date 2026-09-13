#!/bin/bash
# ============================================================
# ObsidianMind 一键停止（macOS 双击运行 / 终端运行均可）
#   · 停止顺序：Vue 前端(:5173) → Spring Boot 后端(:8080)
#   · 幂等：服务本就未运行则提示“无需停止”，不报错
#   · 先优雅终止(SIGTERM)，超时后再强制(SIGKILL)，并清理残留端口占用
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

export PATH="/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

# ---------- 工具函数 ----------

# 占用指定端口的进程 PID（只认 LISTEN 监听态，避免匹配到客户端连接；无则空）
port_pid() {
  lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null | head -1
}

# 从后端启动日志解析真实端口（与 start.command 保持一致；日志缺失时回退默认端口）
resolve_backend_port() {
  local p
  p="$(grep -oE 'Tomcat started on port(\(s\))?:? [0-9]+' "$RUN_DIR/backend.log" 2>/dev/null \
        | tail -1 | grep -oE '[0-9]+$')"
  echo "${p:-$BACKEND_PORT}"
}

# 递归终止进程树：先子进程再父进程
kill_tree() {
  local pid="$1"
  local children
  children="$(pgrep -P "$pid" 2>/dev/null)"
  for c in $children; do
    kill_tree "$c"
  done
  kill -TERM "$pid" 2>/dev/null || true
}

# 按 PID 文件停止（返回 0=已处理，1=无需处理）
stop_by_pidfile() {
  local file="$1" name="$2"
  if [ ! -f "$file" ]; then
    return 1
  fi
  local pid
  pid="$(cat "$file" 2>/dev/null)"
  if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
    rm -f "$file"
    return 1
  fi
  echo "  ⏹  停止$name（PID $pid）…"
  kill_tree "$pid"

  # 最多等待 8 秒优雅退出，超时强制结束
  for ((i = 1; i <= 8; i++)); do
    if ! kill -0 "$pid" 2>/dev/null; then break; fi
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    pkill -9 -P "$pid" 2>/dev/null || true
    kill -9 "$pid" 2>/dev/null || true
    echo "     已强制结束"
  fi
  rm -f "$file"
  return 0
}

# 兜底：端口仍被占用则按端口清理
cleanup_port() {
  local port="$1" name="$2"
  local pid
  pid="$(port_pid "$port")"
  if [ -n "$pid" ]; then
    echo "  🧹 端口 $port 仍被占用（PID $pid），清理$name残留进程…"
    kill_tree "$pid"
    sleep 2
    if kill -0 "$pid" 2>/dev/null; then
      kill -9 "$pid" 2>/dev/null || true
    fi
  fi
}

echo "============================================================"
echo " ObsidianMind 一键停止"
echo " 后端 :$BACKEND_PORT   前端 :$FRONTEND_PORT"
echo "============================================================"
echo

# ---------- 1. 停止前端 ----------
echo "[1/2] 前端 Vue + Vite"
if ! stop_by_pidfile "$RUN_DIR/frontend.pid" "前端"; then
  echo "  ℹ️  前端未在运行（无进程记录），无需停止"
fi
cleanup_port "$FRONTEND_PORT" "前端"

# ---------- 2. 停止后端 ----------
echo
echo "[2/2] 后端 Spring Boot"
if ! stop_by_pidfile "$RUN_DIR/backend.pid" "后端"; then
  echo "  ℹ️  后端未在运行（无进程记录），无需停止"
fi
cleanup_port "$BACKEND_PORT" "后端"
# 若后端实际端口被 SERVER_PORT 覆盖，额外清理真实端口上的残留
REAL_BACKEND_PORT="$(resolve_backend_port)"
if [ "$REAL_BACKEND_PORT" != "$BACKEND_PORT" ]; then
  cleanup_port "$REAL_BACKEND_PORT" "后端(真实端口 $REAL_BACKEND_PORT)"
fi

# ---------- 3. 残留清理（npm/vite/java 启动器进程） ----------
if command -v pgrep >/dev/null 2>&1; then
  pgrep -f "obsidianmind-backend" >/dev/null 2>&1 && pkill -f "obsidianmind-backend" 2>/dev/null || true
fi

# ---------- 4. 结果确认 ----------
echo
echo "状态确认："
REAL_BACKEND_PORT="$(resolve_backend_port)"
if [ -n "$(port_pid "$REAL_BACKEND_PORT")" ]; then
  echo "  ⚠️  后端 :$REAL_BACKEND_PORT 仍在运行（PID $(port_pid "$REAL_BACKEND_PORT")）"
else
  echo "  ✅ 后端已停止"
fi
if [ -n "$(port_pid "$FRONTEND_PORT")" ]; then
  echo "  ⚠️  前端 :$FRONTEND_PORT 仍在运行（PID $(port_pid "$FRONTEND_PORT")）"
else
  echo "  ✅ 前端已停止"
fi

echo
echo "============================================================"
echo " 停止完成。重新启动请双击 scripts/start.command"
echo "============================================================"
echo
read -r -p "按回车键关闭此窗口…" _
exit 0

#!/usr/bin/env bash
# 同时启动前端(:5173) + 后端(:8080)，Ctrl-C 一并退出
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

"$ROOT/scripts/start-backend.sh" &
BACKEND_PID=$!
"$ROOT/scripts/start-frontend.sh" &
FRONTEND_PID=$!

trap 'kill $BACKEND_PID $FRONTEND_PID 2>/dev/null' EXIT INT TERM
echo "[dev] backend pid=$BACKEND_PID  frontend pid=$FRONTEND_PID"
wait

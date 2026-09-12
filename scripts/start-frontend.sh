#!/usr/bin/env bash
# 启动前端（apps/frontend，Vite dev server，默认 :5173）
set -euo pipefail
cd "$(dirname "$0")/../apps/frontend"

if [ ! -d node_modules ]; then
  echo "[frontend] 首次运行，安装依赖..."
  npm install
fi

npm run dev

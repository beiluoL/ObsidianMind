#!/bin/bash
# index-demo-vault.sh —— 一条命令验证知识索引管线（开发调试入口，不影响生产 API）
# 前置条件：后端已启动（默认 :8080），Ollama 已运行（embedding 模型见 application.yml）
# 用法：./scripts/index-demo-vault.sh [--repeat]
#   --repeat  第二次同步：验证增量索引（全部 SKIPPED）

set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${OM_BASE_URL:-http://localhost:8080}"
VAULT_ABS="$(pwd)/tests/fixtures/obsidian-vault"

echo "==> 1/3 连接 Demo Vault: $VAULT_ABS"
curl -fsS --noproxy '*' -X POST "$BASE_URL/api/v1/vault/connect" \
  -H 'Content-Type: application/json' \
  -d "{\"path\": \"$VAULT_ABS\"}" | head -c 400; echo

echo "==> 2/3 触发知识索引（Markdown → Chunk → Embedding → Milvus）"
RESULT=$(curl -fsS --noproxy '*' -X POST "$BASE_URL/api/v1/index/run")
echo "$RESULT"

echo "==> 3/3 数量核对"
TOTAL=$(echo "$RESULT" | /usr/bin/python3 -c 'import json,sys; print(json.load(sys.stdin)["total"])')
CHUNKS=$(echo "$RESULT" | /usr/bin/python3 -c 'import json,sys; print(json.load(sys.stdin)["chunkCount"])')
FILES=$(find tests/fixtures/obsidian-vault -name '*.md' | wc -l | tr -d ' ')
echo "Markdown 文件数: $FILES, 结果 total: $TOTAL, 成功索引 Chunk 数: $CHUNKS"
if [ "${1:-}" = "--repeat" ]; then
  echo "==> 提示：带 --repeat 时应观察到 total 不变且 indexed/updated 均为 0（全部 SKIPPED）"
fi
echo "完成。向量数请以 Milvus（若已启动）或后端日志为准。"

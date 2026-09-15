#!/bin/bash
# ============================================================
# ObsidianMind Git 同步脚本
# 用途：将本地 main 同时推送到 GitHub (origin) 和 Gitee (gitee)
# 原则：本地仓库是唯一开发源，两个远程只是镜像
# 注意：本脚本只负责 Push，不负责 Commit —— 提交必须由开发者明确执行
# 用法：./scripts/sync-git.sh
# ============================================================

set -e

echo "=== ObsidianMind Git Sync ==="

echo "1. Checking Git status..."
git status

echo "2. Pushing to GitHub..."
git push origin main

echo "3. Pushing to Gitee..."
git push gitee main

echo "4. Sync completed."

git remote -v

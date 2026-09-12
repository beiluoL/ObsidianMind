#!/usr/bin/env bash
# 启动后端（apps/backend，Spring Boot，默认 :8080）
set -euo pipefail
cd "$(dirname "$0")/../apps/backend"

# 定位 Maven：优先 PATH，其次 IntelliJ 内置
if command -v mvn >/dev/null 2>&1; then
  MVN="mvn"
elif [ -x "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" ]; then
  MVN="/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
else
  echo "错误：未找到 mvn，请安装 Maven 或配置 JAVA_HOME" >&2
  exit 1
fi

JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null || true)}" exec "$MVN" spring-boot:run

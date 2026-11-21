#!/usr/bin/env bash
# aero-blueprints 构建入口（Git Bash）
# 用法：
#   ./build.sh build        # 完整构建，产物 build/libs/aeroblueprints-1.21.1-1.0.0.jar
#   ./build.sh clean build
set -euo pipefail

MOD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLS="$(cd "$MOD_DIR/../.tools" && pwd)"

export JAVA_HOME="$TOOLS/jdk-21.0.12.1+1"
GRADLE="$TOOLS/gradle-8.10.2/bin/gradle"

# 本机默认走 IPv6 会连接超时，强制 IPv4。
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"

cd "$MOD_DIR"
exec "$GRADLE" "$@"

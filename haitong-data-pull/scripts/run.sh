#!/usr/bin/env bash
set -euo pipefail
APP_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
cd "$APP_DIR"
source "$APP_DIR/scripts/java-env.sh"
JAVA_BIN="$(haitong_resolve_java "$APP_DIR")"
echo "使用 Java 21：$JAVA_BIN"
JAR=target/haitong-data-pull.jar
[[ -f "$JAR" ]] || { echo '未找到 target/haitong-data-pull.jar，请检查安装包是否完整。' >&2; exit 1; }
# 前台运行便于看日志；Ctrl+C 优雅停止。修改 config/application.yml 后重新执行本脚本。
exec "$JAVA_BIN" -Duser.timezone=UTC -Dloader.path=lib -jar "$JAR" --spring.config.additional-location=file:./config/ "$@"

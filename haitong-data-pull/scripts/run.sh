#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# `HAITONG_JAVA_BIN` lets a production deployment select Java 21 without
# changing the server-wide `java` command.  Example:
# HAITONG_JAVA_BIN=/home/soft/backend/jdk-21.0.9/bin/java bash scripts/run.sh
JAVA_BIN="${HAITONG_JAVA_BIN:-${JAVA_HOME:+$JAVA_HOME/bin/java}}"
if [[ -z "$JAVA_BIN" ]]; then
  JAVA_BIN="$(command -v java || true)"
fi
[[ -n "$JAVA_BIN" && -x "$JAVA_BIN" ]] || {
  echo '未找到可执行 Java。请设置 HAITONG_JAVA_BIN 为 JDK 21 的 java 路径。' >&2
  exit 1
}
"$JAVA_BIN" -version 2>&1 | grep -qE '"21\\.| version "21\\.' || {
  echo "需要 JDK/JRE 21，当前 Java：$JAVA_BIN" >&2
  exit 1
}
JAR=target/haitong-data-pull.jar
[[ -f "$JAR" ]] || { echo '未找到应用 JAR。请先执行 bash scripts/build.sh，构建失败时先修复，不要跳过测试。' >&2; exit 1; }
# 前台运行便于看日志；Ctrl+C 优雅停止。修改 config/application.yml 后重新执行本脚本。
exec "$JAVA_BIN" -Duser.timezone=UTC -Dloader.path=lib -jar "$JAR" --spring.config.additional-location=file:./config/ "$@"

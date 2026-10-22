#!/usr/bin/env bash
# Start an unpacked Haitong deployment in the background without changing the
# server-wide Java default.  For long-running production use, systemd remains
# the preferred option; this script is useful when a systemd unit is not used.
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
JAVA_BIN="${HAITONG_JAVA_BIN:-${JAVA_HOME:+$JAVA_HOME/bin/java}}"
PID_FILE="$APP_DIR/data/haitong.pid"
LOG_FILE="$APP_DIR/logs/haitong-console.out"

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
[[ -f "$APP_DIR/target/haitong-data-pull.jar" ]] || {
  echo "未找到 $APP_DIR/target/haitong-data-pull.jar" >&2
  exit 1
}
[[ -f "$APP_DIR/config/application.yml" ]] || {
  echo "未找到 $APP_DIR/config/application.yml" >&2
  exit 1
}

mkdir -p "$APP_DIR/data" "$APP_DIR/logs"
if [[ -f "$PID_FILE" ]]; then
  CURRENT_PID="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [[ "$CURRENT_PID" =~ ^[0-9]+$ ]] && kill -0 "$CURRENT_PID" 2>/dev/null; then
    echo "海通已在运行，PID=$CURRENT_PID。若需重启，请先执行 bash scripts/stop-background.sh。" >&2
    exit 1
  fi
  rm -f "$PID_FILE"
fi

nohup "$JAVA_BIN" \
  -Duser.timezone=UTC \
  -Dloader.path="$APP_DIR/lib" \
  -jar "$APP_DIR/target/haitong-data-pull.jar" \
  --spring.config.additional-location="file:$APP_DIR/config/" \
  >"$LOG_FILE" 2>&1 &

PID=$!
printf '%s\n' "$PID" >"$PID_FILE"
sleep 2
if ! kill -0 "$PID" 2>/dev/null; then
  echo "海通未能启动；请查看日志：$LOG_FILE" >&2
  rm -f "$PID_FILE"
  exit 1
fi

echo "海通已提交启动，PID=$PID"
echo "日志文件：$LOG_FILE"

#!/usr/bin/env bash
# Start an unpacked Haitong deployment in the background without changing the
# server-wide Java default.  For long-running production use, systemd remains
# the preferred option; this script is useful when a systemd unit is not used.
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
cd "$APP_DIR"
source "$APP_DIR/scripts/java-env.sh"
JAVA_BIN="$(haitong_resolve_java "$APP_DIR")"
PID_FILE="$APP_DIR/data/haitong.pid"
LOG_FILE="$APP_DIR/logs/haitong-console.out"

echo "使用 Java 21：$JAVA_BIN"
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

#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
PID_FILE="$APP_DIR/data/haitong.pid"
LOG_FILE="$APP_DIR/logs/haitong-console.out"
HEALTH_URL="${HAITONG_HEALTH_URL:-}"
if [[ -z "$HEALTH_URL" ]]; then
  PORT="$(awk '/^server:[[:space:]]*$/ {server=1;next} server && /^[^[:space:]#]/ {server=0} server && /^  port:/ {value=$0;sub(/^  port:[[:space:]]*/,"",value);sub(/[[:space:]#].*$/,"",value);gsub(/[\047\042]/,"",value);print value;exit}' "$APP_DIR/config/application.yml")"
  [[ "$PORT" =~ ^[0-9]+$ ]] || { echo '无法读取 server.port；请设置 HAITONG_HEALTH_URL。' >&2; exit 1; }
  HEALTH_URL="http://127.0.0.1:$PORT/actuator/health"
fi
if [[ -f "$PID_FILE" ]]; then
  PID="$(tr -d '[:space:]' < "$PID_FILE" || true)"
  if [[ "$PID" =~ ^[0-9]+$ ]] && kill -0 "$PID" 2>/dev/null; then
    echo "服务运行中，PID=$PID"
    curl -fsS --max-time 3 "$HEALTH_URL" || { echo "健康检查暂未通过；请查看：tail -n 100 $LOG_FILE" >&2; exit 1; }
    exit 0
  fi
fi
echo "服务未运行。最近日志："
tail -n 40 "$LOG_FILE" 2>/dev/null || true
exit 1

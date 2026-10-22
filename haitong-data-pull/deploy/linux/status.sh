#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd -P)"
PID_FILE="$APP_DIR/data/haitong-data-pull.pid"
LOG_FILE="$APP_DIR/logs/haitong-data-pull.out"
if [[ -f "$PID_FILE" ]]; then
  PID="$(tr -d '[:space:]' < "$PID_FILE" || true)"
  if [[ "$PID" =~ ^[0-9]+$ ]] && kill -0 "$PID" 2>/dev/null; then
    echo "服务运行中，PID=$PID"
    curl -fsS --max-time 3 http://127.0.0.1:18083/actuator/health || echo "健康检查暂未通过；请查看：tail -n 100 $LOG_FILE"
    exit 0
  fi
fi
echo "服务未运行。最近日志："
tail -n 40 "$LOG_FILE" 2>/dev/null || true
exit 1

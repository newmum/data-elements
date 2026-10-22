#!/usr/bin/env bash
# Stop a process started by start-background.sh.  It intentionally acts only
# on the PID file instead of searching/killing unrelated Java processes.
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
PID_FILE="$APP_DIR/data/haitong.pid"

[[ -f "$PID_FILE" ]] || {
  echo "未找到 PID 文件：$PID_FILE。该脚本没有可停止的后台海通进程。" >&2
  exit 1
}

PID="$(cat "$PID_FILE" 2>/dev/null || true)"
[[ "$PID" =~ ^[0-9]+$ ]] || {
  echo "PID 文件内容无效：$PID_FILE" >&2
  exit 1
}

if ! kill -0 "$PID" 2>/dev/null; then
  rm -f "$PID_FILE"
  echo "海通进程已退出，已清理旧 PID 文件。"
  exit 0
fi

kill -TERM "$PID"
for _ in $(seq 1 30); do
  if ! kill -0 "$PID" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "海通已停止，PID=$PID"
    exit 0
  fi
  sleep 1
done

echo "海通仍在正常退出中，PID=$PID；请稍后再次检查。未执行强制终止。" >&2
exit 1

#!/usr/bin/env bash
# Stops only the process launched by start.sh; it never searches for or kills unrelated Java processes.
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd -P)"
PID_FILE="$APP_DIR/data/haitong-data-pull.pid"

die() { echo "停止失败：$*" >&2; exit 1; }
[[ -f "$PID_FILE" ]] || die "未发现 PID 文件，服务可能不是由 start.sh 启动。"
PID="$(tr -d '[:space:]' < "$PID_FILE" || true)"
[[ "$PID" =~ ^[0-9]+$ ]] || die "PID 文件内容无效：$PID_FILE"
if ! kill -0 "$PID" 2>/dev/null; then
  rm -f "$PID_FILE"
  echo "进程已不存在，已清理 PID 文件。"
  exit 0
fi

kill -TERM "$PID"
for _ in $(seq 1 30); do
  if ! kill -0 "$PID" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "服务已停止，PID=$PID"
    exit 0
  fi
  sleep 1
done
die "服务仍在优雅停止中；未执行 kill -9，可稍后再检查。"

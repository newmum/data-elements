#!/usr/bin/env bash
# Linux direct launcher. No export and no systemd environment file are needed.
# It reads only config/application.yml and starts the service in background.
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd -P)"
CONFIG_FILE="$APP_DIR/config/application.yml"
PID_FILE="$APP_DIR/data/haitong-data-pull.pid"
LOG_FILE="$APP_DIR/logs/haitong-data-pull.out"
JAR_FILE="$APP_DIR/haitong-data-pull.jar"

die() { echo "启动失败：$*" >&2; exit 1; }

[[ -f "$JAR_FILE" ]] || die "未找到 $JAR_FILE"
[[ -f "$CONFIG_FILE" ]] || die "未找到 $CONFIG_FILE。请在 config/application.yml 中填写运行配置。"

JAVA_BIN="$(command -v java || true)"
[[ -n "$JAVA_BIN" && -x "$JAVA_BIN" ]] || die "未找到 Java。请安装 Java 21，并使 java 位于 PATH 中。"
"$JAVA_BIN" -version 2>&1 | grep -qE '"21\.| version "21\.' || die "需要 Java 21，当前：$JAVA_BIN"

mkdir -p "$APP_DIR/data" "$APP_DIR/logs"
if [[ -f "$PID_FILE" ]]; then
  CURRENT_PID="$(tr -d '[:space:]' < "$PID_FILE" || true)"
  if [[ "$CURRENT_PID" =~ ^[0-9]+$ ]] && kill -0 "$CURRENT_PID" 2>/dev/null; then
    die "服务已运行，PID=$CURRENT_PID。查看日志：tail -f $LOG_FILE；需要重启请先执行 bash stop.sh。"
  fi
  rm -f "$PID_FILE"
fi

chmod 600 "$CONFIG_FILE" 2>/dev/null || true
nohup "$JAVA_BIN" \
  -Duser.timezone=UTC \
  -Dloader.path="$APP_DIR/lib" \
  -jar "$JAR_FILE" \
  --spring.config.location="file:$CONFIG_FILE" \
  >"$LOG_FILE" 2>&1 &
PID=$!
printf '%s\n' "$PID" > "$PID_FILE"

sleep 3
if ! kill -0 "$PID" 2>/dev/null; then
  rm -f "$PID_FILE"
  tail -n 80 "$LOG_FILE" >&2 || true
  die "进程启动后退出，请根据上述日志修正配置。"
fi

echo "海通服务已启动，PID=$PID"
echo "配置：$CONFIG_FILE（启动脚本不会扫描配置中的占位说明文本）"
echo "日志：$LOG_FILE"
echo "状态：bash status.sh"
echo "停止：bash stop.sh"
echo "健康检查：curl -fsS http://127.0.0.1:18083/actuator/health"

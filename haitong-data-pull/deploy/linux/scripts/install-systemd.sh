#!/usr/bin/env bash
# Install haitong-data-pull from the Linux release archive as a systemd service.
set -euo pipefail

usage() {
  cat <<'EOF'
用法：
  sudo bash scripts/install-systemd.sh --app-dir /opt/haitong-data-pull [选项]

部署目录必须包含：haitong-data-pull.jar、config/application.yml、config/haitong-data-pull.env。

选项：
  --app-dir PATH       部署根目录（必填）
  --env-file PATH      systemd 环境变量文件；默认 <app-dir>/config/haitong-data-pull.env
  --user USER          运行用户，默认 haitong
  --group GROUP        运行组，默认运行用户的主组
  --java-bin PATH      Java 21 的 java；默认从 PATH 查找
  --service-name NAME  服务名，默认 haitong-data-pull
  --enable             安装后开机自启
  --start              安装后立即启动
  --replace            覆盖已有同名 unit
  -h, --help           显示帮助
EOF
}

die() { echo "错误：$*" >&2; exit 1; }

APP_DIR=""; ENV_FILE=""; SERVICE_USER="haitong"; SERVICE_GROUP=""; JAVA_BIN=""
SERVICE_NAME="haitong-data-pull"; ENABLE=false; START=false; REPLACE=false

while (($#)); do
  case "$1" in
    --app-dir) APP_DIR="${2:-}"; shift 2 ;;
    --env-file) ENV_FILE="${2:-}"; shift 2 ;;
    --user) SERVICE_USER="${2:-}"; shift 2 ;;
    --group) SERVICE_GROUP="${2:-}"; shift 2 ;;
    --java-bin) JAVA_BIN="${2:-}"; shift 2 ;;
    --service-name) SERVICE_NAME="${2:-}"; shift 2 ;;
    --enable) ENABLE=true; shift ;;
    --start) START=true; shift ;;
    --replace) REPLACE=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "不支持的参数：$1" ;;
  esac
done

[[ $EUID -eq 0 ]] || die "请使用 root 或 sudo 执行。"
[[ -n "$APP_DIR" ]] || { usage >&2; die "缺少 --app-dir。"; }
command -v systemctl >/dev/null || die "当前系统没有 systemd/systemctl。"
id "$SERVICE_USER" >/dev/null 2>&1 || die "运行用户不存在：$SERVICE_USER；请先创建。"
[[ "$SERVICE_NAME" =~ ^[A-Za-z0-9][A-Za-z0-9_.@-]*$ ]] || die "非法服务名。"

APP_DIR="$(cd "$APP_DIR" 2>/dev/null && pwd -P)" || die "应用目录不存在：$APP_DIR"
[[ "$APP_DIR" != *[[:space:]]* ]] || die "应用目录不能包含空白字符。"
[[ -f "$APP_DIR/haitong-data-pull.jar" ]] || die "未找到 $APP_DIR/haitong-data-pull.jar"
[[ -f "$APP_DIR/config/application.yml" ]] || die "未找到配置文件 config/application.yml"
if [[ -z "$ENV_FILE" ]]; then ENV_FILE="$APP_DIR/config/haitong-data-pull.env"; fi
ENV_FILE="$(readlink -f "$ENV_FILE" 2>/dev/null || true)"
[[ -n "$ENV_FILE" && -f "$ENV_FILE" ]] || die "未找到环境变量文件。先复制 config/haitong-data-pull.env.example 为 config/haitong-data-pull.env。"
[[ "$ENV_FILE" != *[[:space:]]* ]] || die "环境变量文件路径不能包含空白字符。"
grep -qE '^HAITONG_MAGIC_EDITOR_PASSWORD=.+$' "$ENV_FILE" || die "环境变量文件未设置 HAITONG_MAGIC_EDITOR_PASSWORD。"
grep -qE '^HAITONG_DB_PASSWORD=.+$' "$ENV_FILE" || die "环境变量文件未设置 HAITONG_DB_PASSWORD。"
grep -qE '^HAITONG_STANDALONE_PUSH_KEY=.+$' "$ENV_FILE" || die "环境变量文件未设置 HAITONG_STANDALONE_PUSH_KEY。"

if [[ -z "$SERVICE_GROUP" ]]; then SERVICE_GROUP="$(id -gn "$SERVICE_USER")"; fi
getent group "$SERVICE_GROUP" >/dev/null || die "运行组不存在：$SERVICE_GROUP"
if [[ -z "$JAVA_BIN" ]]; then JAVA_BIN="$(command -v java || true)"; fi
[[ -n "$JAVA_BIN" && -x "$JAVA_BIN" ]] || die "未找到 Java 21；使用 --java-bin 指定。"
JAVA_BIN="$(readlink -f "$JAVA_BIN")"
"$JAVA_BIN" -version 2>&1 | grep -qE '"21\.| version "21\.' || die "需要 Java 21：$JAVA_BIN"

install -d -m 0750 -o "$SERVICE_USER" -g "$SERVICE_GROUP" "$APP_DIR/data" "$APP_DIR/logs"
chown root:"$SERVICE_GROUP" "$ENV_FILE"
chmod 0640 "$ENV_FILE"

UNIT_PATH="/etc/systemd/system/${SERVICE_NAME}.service"
[[ ! -e "$UNIT_PATH" || "$REPLACE" == true ]] || die "服务已存在：$UNIT_PATH；确认覆盖时追加 --replace。"

UNIT_TMP="$(mktemp)"
trap 'rm -f "$UNIT_TMP"' EXIT
cat >"$UNIT_TMP" <<EOF
[Unit]
Description=Haitong Data Pull and Push Service
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_GROUP
WorkingDirectory=$APP_DIR
UMask=0027
Environment=TZ=UTC
EnvironmentFile=$ENV_FILE
ExecStart=$JAVA_BIN -Duser.timezone=UTC -Dloader.path=$APP_DIR/lib -jar $APP_DIR/haitong-data-pull.jar --spring.config.additional-location=file:$APP_DIR/config/
Restart=on-failure
RestartSec=10s
TimeoutStopSec=150s
SuccessExitStatus=143
NoNewPrivileges=true
PrivateTmp=true
ReadWritePaths=$APP_DIR/data $APP_DIR/logs
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
install -m 0644 "$UNIT_TMP" "$UNIT_PATH"
systemctl daemon-reload
[[ "$ENABLE" == true ]] && systemctl enable "$SERVICE_NAME"
[[ "$START" == true ]] && systemctl start "$SERVICE_NAME"

echo "已安装 $UNIT_PATH"
echo "查看状态：systemctl status $SERVICE_NAME"
echo "实时日志：journalctl -u $SERVICE_NAME -f"
EOF

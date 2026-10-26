#!/usr/bin/env bash
# Install a systemd unit for an unpacked haitong production deployment.
# Run as root after copying the JAR, config, optional lib drivers and this
# scripts directory to the target host.  The installer never replaces an
# existing unit unless --replace is explicitly supplied.
set -euo pipefail

usage() {
  cat <<'EOF'
用法：
  sudo bash scripts/install-systemd.sh --app-dir /opt/haitong-data-pull [选项]

必填：
  --app-dir PATH       海通数据推送服务部署根目录；须包含 target/haitong-data-pull.jar 与 config/application.yml

可选：
  --user USER          运行服务的 Linux 用户，默认 haitong
  --group GROUP        运行服务的 Linux 用户组，默认该用户的主组
  --java-bin PATH      Java 21 可执行文件；未指定时检查环境变量、相邻 JDK 和 PATH
  --service-name NAME  systemd 服务名，默认 haitong-data-pull
  --enable             安装后设为开机自启
  --start              安装后立即启动
  --replace            覆盖同名已有服务定义
  -h, --help           显示本说明

安装后常用命令：
  systemctl start haitong-data-pull
  systemctl stop haitong-data-pull
  systemctl restart haitong-data-pull
  systemctl status haitong-data-pull
  journalctl -u haitong-data-pull -f
EOF
}

die() {
  echo "错误：$*" >&2
  exit 1
}

APP_DIR=""
SERVICE_USER="haitong"
SERVICE_GROUP=""
JAVA_BIN=""
SERVICE_NAME="haitong-data-pull"
ENABLE=false
START=false
REPLACE=false

while (($#)); do
  case "$1" in
    --app-dir) APP_DIR="${2:-}"; shift 2 ;;
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

[[ "${EUID}" -eq 0 ]] || die "请使用 root 或 sudo 执行。"
[[ -n "$APP_DIR" ]] || { usage >&2; die "缺少 --app-dir。"; }
command -v systemctl >/dev/null || die "当前主机未发现 systemd/systemctl，不能安装 systemd 服务。"
id "$SERVICE_USER" >/dev/null 2>&1 || die "运行用户不存在：$SERVICE_USER。请先创建该用户。"
[[ "$SERVICE_NAME" =~ ^[A-Za-z0-9][A-Za-z0-9_.@-]*$ ]] || die "服务名只能包含字母、数字、点、下划线、@ 和连字符。"

APP_DIR="$(cd "$APP_DIR" 2>/dev/null && pwd -P)" || die "应用目录不存在：$APP_DIR"
[[ "$APP_DIR" != *[[:space:]]* ]] || die "应用目录不能包含空白字符；请使用例如 /opt/haitong-data-pull 的路径。"
[[ -f "$APP_DIR/target/haitong-data-pull.jar" ]] || die "未找到 $APP_DIR/target/haitong-data-pull.jar。请先完成构建并复制 JAR。"
[[ -f "$APP_DIR/config/application.yml" ]] || die "未找到 $APP_DIR/config/application.yml。生产配置不能缺失。"

if [[ -z "$SERVICE_GROUP" ]]; then
  SERVICE_GROUP="$(id -gn "$SERVICE_USER")"
fi
getent group "$SERVICE_GROUP" >/dev/null || die "运行用户组不存在：$SERVICE_GROUP"

source "$APP_DIR/scripts/java-env.sh"
JAVA_BIN="$(haitong_resolve_java "$APP_DIR" "$JAVA_BIN")"
JAVA_BIN="$(readlink -f "$JAVA_BIN")"

# The service account owns only mutable runtime state.  Keep the JAR and
# configuration under the deployment account/root ownership when possible.
install -d -m 0750 -o "$SERVICE_USER" -g "$SERVICE_GROUP" "$APP_DIR/data" "$APP_DIR/logs"

UNIT_PATH="/etc/systemd/system/${SERVICE_NAME}.service"
if [[ -e "$UNIT_PATH" && "$REPLACE" != true ]]; then
  die "服务定义已存在：$UNIT_PATH。确认需要替换时追加 --replace。"
fi

UNIT_TMP="$(mktemp)"
trap 'rm -f "$UNIT_TMP"' EXIT
cat >"$UNIT_TMP" <<EOF
[Unit]
Description=Haitong data push service
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=$SERVICE_USER
Group=$SERVICE_GROUP
WorkingDirectory=$APP_DIR
UMask=0027
Environment=TZ=UTC
ExecStart=$JAVA_BIN -Duser.timezone=UTC -Dloader.path=$APP_DIR/lib -jar $APP_DIR/target/haitong-data-pull.jar --spring.config.additional-location=file:$APP_DIR/config/
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

if [[ "$ENABLE" == true ]]; then
  systemctl enable "$SERVICE_NAME"
fi
if [[ "$START" == true ]]; then
  systemctl start "$SERVICE_NAME"
fi

echo "已安装服务：$UNIT_PATH"
echo "应用目录：$APP_DIR"
echo "运行用户：$SERVICE_USER:$SERVICE_GROUP"
echo "查看状态：systemctl status $SERVICE_NAME"
echo "查看实时日志：journalctl -u $SERVICE_NAME -f"

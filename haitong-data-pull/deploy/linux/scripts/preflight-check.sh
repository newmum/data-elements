#!/usr/bin/env bash
# Read-only preflight check. It deliberately does not source the env file.
set -euo pipefail

APP_DIR="${1:-$(cd "$(dirname "$0")/../.." && pwd -P)}"
ENV_FILE="${2:-$APP_DIR/config/haitong-data-pull.env}"
failed=0

check() { if "$@"; then printf '[PASS] %s\n' "$*"; else printf '[FAIL] %s\n' "$*" >&2; failed=1; fi; }
note() { printf '[INFO] %s\n' "$*"; }

note "应用目录：$APP_DIR"
check test -f "$APP_DIR/haitong-data-pull.jar"
check test -f "$APP_DIR/config/application.yml"
check test -f "$ENV_FILE"
check command -v java
if command -v java >/dev/null; then java -version 2>&1 | head -n 1; fi
if [[ -f "$ENV_FILE" ]]; then
  for key in HAITONG_MAGIC_EDITOR_PASSWORD HAITONG_DB_HOST HAITONG_DB_PASSWORD HAITONG_STANDALONE_PUSH_KEY HAITONG_FTP_HOST HAITONG_FTP_PASSWORD HAITONG_FTP_OUTPUT_DIR; do
    if grep -qE "^${key}=.+$" "$ENV_FILE" && ! grep -qE "^${key}=REPLACE_" "$ENV_FILE"; then
      printf '[PASS] %s 已设置\n' "$key"
    else
      printf '[FAIL] %s 未设置或仍是占位符\n' "$key" >&2
      failed=1
    fi
  done
fi
if [[ $failed -ne 0 ]]; then
  echo '预检失败：修正上述项后再安装服务。' >&2
  exit 1
fi
echo '预检通过。'

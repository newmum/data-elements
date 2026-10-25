#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
command -v java >/dev/null || { echo '请先安装 JDK 21。' >&2; exit 1; }
command -v mvn >/dev/null || { echo '请先安装 Maven 3.9+ 并加入 PATH。' >&2; exit 1; }
mvn --batch-mode --no-transfer-progress clean verify "$@"
echo '构建完成：target/haitong-data-pull.jar。运行 bash scripts/run.sh'

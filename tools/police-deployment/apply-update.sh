#!/usr/bin/env bash
set -euo pipefail
umask 077
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
case ${1:-} in --plan|--apply) ;; *) echo '用法: bash apply-update.sh --plan|--apply' >&2; exit 2 ;; esac
[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] || { echo '需要 Linux x86_64' >&2; exit 1; }
[[ -f data/.initialized && -x runtime/java/bin/java ]] || { echo '请先完成原部署包的首次初始化' >&2; exit 1; }
mkdir -p logs run
exec 8>run/defaults-update.lock
flock -n 8 || { echo '另一个初始化数据更新正在执行' >&2; exit 1; }
sha256sum -c update-manifest.sha256
config=${POLICE_ENV_FILE:-"$PWD/config/runtime.env"}
[[ -r $config ]] || { echo '缺少私有运行配置' >&2; exit 1; }
set -a
source "$config"
set +a
runtime/java/bin/java -cp 'tools/lib/*' tools/PoliceDefaults.java --plan
if [[ $1 == --apply ]]; then
    runtime/java/bin/java -cp 'tools/lib/*' tools/PoliceDefaults.java --apply
    backup="logs/police-update-$(date +%Y%m%d-%H%M%S)-$$"
    mkdir -p "$backup"
    cp -p backend/data-element.jar "$backup/data-element.jar"
    bash platform.sh stop
    cp updates/police-defaults/data-element.jar backend/data-element.jar.next
    mv backend/data-element.jar.next backend/data-element.jar
    if ! bash platform.sh start; then
        echo "新后端启动失败，正在从 $backup 恢复原后端" >&2
        bash platform.sh stop
        cp "$backup/data-element.jar" backend/data-element.jar.next
        mv backend/data-element.jar.next backend/data-element.jar
        bash platform.sh start
        exit 1
    fi
    bash platform.sh status
fi

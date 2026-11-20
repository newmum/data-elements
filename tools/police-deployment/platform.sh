#!/usr/bin/env bash
set -euo pipefail
umask 077
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] || { echo '需要 Linux x86_64' >&2; exit 1; }
mkdir -p logs run data
exec 9>run/launcher.lock
flock -n 9 || { echo '另一个管理操作正在执行' >&2; exit 1; }

if [[ ${1:-} != status && ${1:-} != stop ]]; then
    config=${POLICE_ENV_FILE:-"$PWD/config/runtime.env"}
    [[ -r $config ]] || { echo '请从 config/runtime.env.example 创建私有运行配置' >&2; exit 1; }
    set -a
    source "$config"
    set +a
    export POLICE_BACKEND_PORT=${POLICE_BACKEND_PORT:-18188}
    export POLICE_WEB_PORT=${POLICE_WEB_PORT:-18191}
    export POLICE_WEB_BIND=${POLICE_WEB_BIND:-127.0.0.1}
    export POLICE_REDIS_PORT=${POLICE_REDIS_PORT:-18179}
    export DATA_ELEMENTS_LOG_DIR="$PWD/logs/backend"
    : "${POLICE_DM_URL:?}" "${POLICE_CONTROL_PASSWORD:?}" "${POLICE_TENANT_PASSWORD:?}" "${POLICE_REDIS_PASSWORD:?}"
fi

extract() {
    local name=$1 asset=$2 strip=$3
    if [[ ! -e runtime/$name/.ready ]]; then
        mkdir -p "runtime/$name"
        tar -xf "vendor/$asset" -C "runtime/$name" "--strip-components=$strip"
        touch "runtime/$name/.ready"
    fi
}

pid() {
    local name=$1 process started stat
    [[ -r run/$name.pid ]] || return 1
    read -r process started <"run/$name.pid"
    [[ -r /proc/$process/stat ]] || return 1
    stat=$(cat "/proc/$process/stat") || return 1
    stat=${stat##*) }
    set -- $stat
    [[ $1 != Z && ${20:-} == "$started" ]] || return 1
    printf '%s\n' "$process"
}

spawn() {
    local name=$1 process stat
    shift
    pid "$name" >/dev/null && return 0
    nohup "$@" 9>&- </dev/null >>"logs/$name.log" 2>&1 &
    process=$!
    stat=$(cat "/proc/$process/stat")
    stat=${stat##*) }
    set -- $stat
    printf '%s %s\n' "$process" "${20}" >"run/$name.pid"
}

stop_one() {
    local name=$1 process
    if process=$(pid "$name"); then
        kill -TERM "$process"
        for _ in {1..45}; do
            if ! pid "$name" >/dev/null; then break; fi
            sleep 1
        done
        if pid "$name" >/dev/null; then echo "$name 未停止，请看日志" >&2; return 1; fi
    fi
    rm -f -- "run/$name.pid"
}

ready() {
    local name=$1 url=$2
    for _ in {1..180}; do
        pid "$name" >/dev/null || break
        if curl -fsS --max-time 2 "$url" >/dev/null 2>&1; then return 0; fi
        sleep 1
    done
    echo "$name 未就绪，请查看 logs/$name.log" >&2
    stop_one "$name"
    return 1
}

case ${1:-} in
    create-users|init-db)
        extract java java21.tar.gz 1
        if [[ $1 == init-db && -f data/.initialized ]]; then
            echo '已完成初始化'
            exit 0
        fi
        runtime/java/bin/java -cp 'tools/lib/*' tools/Bootstrap.java "$1"
        if [[ $1 == init-db ]]; then
            runtime/java/bin/java -cp 'tools/lib/*' tools/PoliceDefaults.java --apply
            if [[ -f updates/police-defaults/data-element.jar ]]; then
                sha256sum -c update-manifest.sha256
                cp updates/police-defaults/data-element.jar backend/data-element.jar.next
                mv backend/data-element.jar.next backend/data-element.jar
            fi
            touch data/.initialized
        fi
        ;;
    start)
        extract java java21.tar.gz 1
        extract redis redis.tar.gz 0
        extract caddy caddy.tar.gz 0
        for item in "redis:$POLICE_REDIS_PORT" "backend:$POLICE_BACKEND_PORT" "web:$POLICE_WEB_PORT"; do
            service=${item%%:*}
            port=${item#*:}
            if ! pid "$service" >/dev/null && ( : >/dev/tcp/127.0.0.1/"$port" ) 2>/dev/null; then
                echo "端口 $port 已占用" >&2
                exit 1
            fi
        done
        [[ $POLICE_REDIS_PASSWORD =~ ^[A-Za-z0-9_!%+=@#-]{12,64}$ ]] || exit 1
        cat >run/redis.conf <<EOF
bind 127.0.0.1
port $POLICE_REDIS_PORT
requirepass $POLICE_REDIS_PASSWORD
dir $PWD/data
dbfilename valkey.rdb
appendonly yes
EOF
        spawn redis "$PWD/runtime/redis/lib/ld-musl-x86_64.so.1" --library-path \
            "$PWD/runtime/redis/lib:$PWD/runtime/redis/usr/lib" "$PWD/runtime/redis/usr/bin/valkey-server" "$PWD/run/redis.conf"
        runtime/java/bin/java -cp 'tools/lib/*' tools/PoliceDefaults.java --refresh-cache
        spawn backend "$PWD/runtime/java/bin/java" -Xms256m -Xmx1024m -Dfile.encoding=UTF-8 \
            -jar backend/data-element.jar --spring.config.location=file:./config/application.yml
        ready backend "http://127.0.0.1:$POLICE_BACKEND_PORT/actuator/health"
        spawn web "$PWD/runtime/caddy/caddy" run --config config/Caddyfile --adapter caddyfile
        ready web "http://127.0.0.1:$POLICE_WEB_PORT/"
        echo "源数据管理平台已就绪，网页端口 $POLICE_WEB_PORT"
        ;;
    stop) for service in web backend redis; do stop_one "$service"; done ;;
    status)
        for service in web backend redis; do
            if process=$(pid "$service"); then printf '%s running (%s)\n' "$service" "$process"
            else printf '%s stopped\n' "$service"; fi
        done
        ;;
    *) echo "用法: bash platform.sh {create-users|init-db|start|stop|status}" >&2; exit 2 ;;
esac

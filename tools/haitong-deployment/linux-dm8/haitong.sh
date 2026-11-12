#!/usr/bin/env bash
set -euo pipefail
umask 077
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
cd "$root"
mkdir -p logs run

set -a
source config/ports.env
set +a
export HAITONG_BACKEND_BIND=127.0.0.1
for port in "$HAITONG_BACKEND_PORT" "$HAITONG_HTTP_PORT"; do
    if [[ ! $port =~ ^[1-9][0-9]{0,4}$ ]] || ((port > 65535)); then
        echo '端口必须是 1–65535，请修改 config/ports.env。' >&2
        exit 2
    fi
done
if [[ $HAITONG_BACKEND_PORT == "$HAITONG_HTTP_PORT" ]]; then
    echo '前端与后端端口不能相同。' >&2
    exit 2
fi

case $(uname -m) in
    x86_64|amd64) arch=x64 ;;
    aarch64|arm64) arch=aarch64 ;;
    *) echo "不支持的 CPU 架构：$(uname -m)" >&2; exit 2 ;;
esac

command -v flock >/dev/null || { echo '缺少 flock（util-linux）。' >&2; exit 2; }
exec 9>run/control.lock
flock -n 9 || { echo '另一个海通管理操作正在执行。' >&2; exit 1; }
java="$root/runtime/$arch/jdk21/bin/java"
caddy="$root/runtime/$arch/web/caddy"
jar="$root/backend/data-element.jar"
caddyfile="$root/config/Caddyfile"

unpack_runtime() {
    local temporary
    if [[ ! -x $java ]]; then
        temporary=$(mktemp -d "runtime/$arch/jdk21.XXXXXX")
        tar -xzf "runtime/$arch/jdk21.tar.gz" -C "$temporary" --strip-components=1
        test -x "$temporary/bin/java"
        mv -T -- "$temporary" "runtime/$arch/jdk21"
    fi
    if [[ ! -x $caddy ]]; then
        temporary=$(mktemp -d "runtime/$arch/web.XXXXXX")
        tar -xzf "runtime/$arch/caddy.tar.gz" -C "$temporary"
        test -x "$temporary/caddy"
        mv -T -- "$temporary" "runtime/$arch/web"
    fi
}

owned_pid() {
    local name=$1 expected=$2 pid argument
    [[ -s run/$name.pid ]] || return 1
    read -r pid < "run/$name.pid" || return 1
    [[ $pid =~ ^[1-9][0-9]*$ && -r /proc/$pid/cmdline ]] || return 1
    while IFS= read -r -d '' argument; do
        if [[ $argument == "$expected" ]]; then
            printf '%s\n' "$pid"
            return 0
        fi
    done 2>/dev/null < "/proc/$pid/cmdline"
    return 1
}

stop_one() {
    local name=$1 marker=$2 pid
    if pid=$(owned_pid "$name" "$marker"); then
        kill -TERM "$pid" 2>/dev/null || true
        for ((attempt=0; attempt<30; attempt++)); do
            owned_pid "$name" "$marker" >/dev/null || break
            sleep 1
        done
        if owned_pid "$name" "$marker" >/dev/null; then
            echo "$name 停止超时，进程 $pid 仍在退出，请查看日志。" >&2
            return 1
        fi
    fi
    rm -f -- "run/$name.pid"
}

health() {
    local reply
    reply=$(curl --noproxy '*' -fsS --max-time 3 "$1/actuator/health" 2>/dev/null) || return 1
    [[ $reply =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]]
}

start() {
    command -v curl >/dev/null || { echo '缺少 curl，无法检查服务状态。' >&2; return 2; }
    if grep -Eq '^[[:space:]]*password:.*CHANGE_ME' config/application.yml; then
        echo '请先在 config/application.yml 中填写达梦连接密码。' >&2
        return 2
    fi
    local backend="http://127.0.0.1:$HAITONG_BACKEND_PORT"
    local web_host=$HAITONG_HTTP_BIND
    [[ $web_host != 0.0.0.0 ]] || web_host=127.0.0.1
    local web="http://$web_host:$HAITONG_HTTP_PORT"
    if owned_pid backend "$jar" >/dev/null && owned_pid web "$caddyfile" >/dev/null; then
        health "$web/prod-api" || { echo '海通进程已存在，但健康检查未通过。' >&2; return 1; }
        echo "海通已启动，入口端口 $HAITONG_HTTP_PORT。"
        return
    fi
    stop_one web "$caddyfile"
    stop_one backend "$jar"
    if (echo >/dev/tcp/127.0.0.1/"$HAITONG_BACKEND_PORT") 2>/dev/null || (echo >/dev/tcp/"$web_host"/"$HAITONG_HTTP_PORT") 2>/dev/null; then
        echo '配置的端口已被其他进程占用，请查看 config/ports.env。' >&2
        return 1
    fi
    unpack_runtime
    XDG_DATA_HOME="$root/run/web-data" XDG_CONFIG_HOME="$root/run/web-config" \
        "$caddy" validate --adapter caddyfile --config "$caddyfile" >logs/web-validation.log 2>&1 || {
        echo '网页入口配置检查失败，详见 logs/web-validation.log。' >&2; return 1;
    }
    nohup "$java" -Dfile.encoding=UTF-8 -jar "$jar" \
        --spring.config.location=file:./config/application.yml \
        </dev/null >>logs/backend-console.log 2>&1 9>&- &
    local backend_pid=$!
    echo "$backend_pid" >run/backend.pid
    local ready=false
    for ((attempt=0; attempt<120; attempt++)); do
        if owned_pid backend "$jar" >/dev/null && health "$backend"; then ready=true; break; fi
        kill -0 "$backend_pid" 2>/dev/null || break
        sleep 1
    done
    if [[ $ready != true ]]; then
        stop_one backend "$jar" || true
        echo '后端未就绪。请检查 config/application.yml 和 logs/backend-console.log。' >&2
        return 1
    fi
    XDG_DATA_HOME="$root/run/web-data" XDG_CONFIG_HOME="$root/run/web-config" \
        nohup "$caddy" run --adapter caddyfile --config "$caddyfile" \
        </dev/null >>logs/web.log 2>&1 9>&- &
    local caddy_pid=$!
    echo "$caddy_pid" >run/web.pid
    for ((attempt=0; attempt<20; attempt++)); do
        if owned_pid web "$caddyfile" >/dev/null && health "$web/prod-api" && curl --noproxy '*' -fsS --max-time 3 "$web/" >/dev/null; then
            echo "海通已启动。浏览器访问 http://服务器地址:$HAITONG_HTTP_PORT/"
            return
        fi
        kill -0 "$caddy_pid" 2>/dev/null || break
        sleep 1
    done
    stop_one web "$caddyfile" || true
    stop_one backend "$jar" || true
    echo '网页入口未就绪，请检查端口占用及 logs/web.log。' >&2
    return 1
}

case ${1:-status} in
    start) start ;;
    stop)
        result=0
        stop_one web "$caddyfile" || result=1
        stop_one backend "$jar" || result=1
        if [[ $result == 0 ]]; then echo '海通已停止。'; fi
        exit "$result" ;;
    status)
        if back=$(owned_pid backend "$jar") && web_pid=$(owned_pid web "$caddyfile"); then
            echo "运行中：后端 PID $back，网页入口 PID $web_pid（端口 $HAITONG_HTTP_PORT）。"
        else
            echo '海通未完全启动。'
            exit 1
        fi ;;
    init-admin)
        unpack_runtime
        "$java" -cp 'tools/lib/*' tools/Dm8Admin.java ;;
    *) echo '用法：bash haitong.sh {start|stop|status|init-admin}' >&2; exit 2 ;;
esac

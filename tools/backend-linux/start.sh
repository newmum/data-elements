#!/usr/bin/env bash
set -euo pipefail
umask 077

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
cd -- "$root"
pid_file=run/data-element.pid
marker="-Ddata.elements.launcher.dir=$root"
jar="$root/data-element-runtime.jar"

fail() {
    printf '%s\n' "$*" >&2
    exit 1
}

# /proc records the process start time in clock ticks. Pair it with the PID
# so an old PID file cannot identify a new process after the PID is reused.
process_start_time() {
    local stat
    local -a fields
    { IFS= read -r stat < "/proc/$1/stat"; } 2>/dev/null || return 1
    read -r -a fields <<< "${stat##*) }"
    [[ ${fields[0]:-Z} != Z && ${fields[19]:-} =~ ^[0-9]+$ ]] || return 1
    printf '%s\n' "${fields[19]}"
}

owned_pid() {
    local pid started argument marker_found=false jar_found=false
    [[ -r $pid_file ]] && read -r pid started < "$pid_file" || return 1
    [[ $pid =~ ^[1-9][0-9]*$ && $started =~ ^[0-9]+$ && -O /proc/$pid ]] || return 1
    [[ $(process_start_time "$pid") == "$started" ]] || return 1
    while IFS= read -r -d '' argument; do
        if [[ $argument == "$marker" ]]; then marker_found=true; fi
        if [[ $argument == "$jar" ]]; then jar_found=true; fi
    done 2>/dev/null < "/proc/$pid/cmdline"
    [[ $marker_found == true && $jar_found == true ]] || return 1
    printf '%s\n' "$pid"
}

stop_backend() {
    local pid
    if ! pid=$(owned_pid); then
        rm -f -- "$pid_file"
        printf '后端已停止。\n'
        return
    fi
    kill -TERM "$pid" 2>/dev/null || {
        if owned_pid >/dev/null; then fail "无法停止后端（PID $pid）。"; fi
    }
    local deadline=$((SECONDS + 60))
    while owned_pid >/dev/null; do
        (( SECONDS < deadline )) || fail "后端停止超时（PID $pid）。请查看 logs/backend-console.log。"
        sleep 1
    done
    rm -f -- "$pid_file"
    printf '后端已停止（PID %s）。\n' "$pid"
}

start_backend() {
    local pid started='' reply attempt
    if pid=$(owned_pid); then
        printf '后端进程已存在（PID %s）。日志位于 logs/backend-console.log。\n' "$pid"
        return
    fi
    rm -f -- "$pid_file"

    # Only the operator-provided file is sourced. It supplies environment
    # variables such as Nacos settings or keys referenced by the local YAML.
    if [[ -f config/backend.env ]]; then
        set -a
        source ./config/backend.env
        set +a
    fi

    local java="$root/jdk21/bin/java"
    [[ -x $java ]] || fail '请将服务器架构对应的 Linux JDK 21 解压到 jdk21/，确保 jdk21/bin/java 可执行。'
    [[ -r $jar && -f $jar ]] || fail '请将后端 JAR 放到脚本同级，命名为 data-element-runtime.jar。'
    local java_version
    java_version=$("$java" -version 2>&1) || fail 'jdk21/bin/java 无法运行，请检查 JDK 及服务器架构。'
    [[ $java_version =~ version[[:space:]]+\"21[.\"] ]] || fail 'jdk21/bin/java 必须是 Java 21。'
    command -v curl >/dev/null || fail '缺少 curl，无法检查后端是否就绪。'

    local port=${SERVER_PORT:-8088}
    local timeout=${BACKEND_START_TIMEOUT:-120}
    [[ $port =~ ^[1-9][0-9]{0,4}$ ]] && (( port <= 65535 )) || fail 'SERVER_PORT 必须为 1–65535。'
    [[ $timeout =~ ^[1-9][0-9]{0,3}$ ]] || fail 'BACKEND_START_TIMEOUT 必须为 1–9999 秒。'
    local health_url=${BACKEND_HEALTH_URL:-http://127.0.0.1:$port/actuator/health}
    local mode=${BACKEND_MODE:-}
    if [[ -z $mode ]]; then
        if [[ -f config/application.yml ]]; then mode=file; else mode=nacos; fi
    fi
    local -a config_args
    case $mode in
        nacos)
            [[ -n ${NACOS_HOST:-} && -v NACOS_NAMESPACE && -n ${NACOS_CONFIG_IMPORTS:-} ]] ||
                fail '请在 config/backend.env 或进程环境中设置 NACOS_HOST、NACOS_NAMESPACE 和 NACOS_CONFIG_IMPORTS。'
            config_args=(--spring.config.location=classpath:/)
            ;;
        file)
            [[ -r config/application.yml && -f config/application.yml ]] || fail '请把完整业务配置放到 config/application.yml。'
            config_args=(
                --spring.config.location=file:./config/application.yml
                --spring.config.import=
                --spring.cloud.nacos.config.enabled=false
                --spring.cloud.nacos.config.import-check.enabled=false
                --spring.cloud.nacos.discovery.enabled=false
                --spring.cloud.nacos.discovery.register-enabled=false
                --spring.cloud.discovery.enabled=false
                --spring.cloud.service-registry.auto-registration.enabled=false
                --management.health.nacos.enabled=false
            )
            ;;
        *) fail 'BACKEND_MODE 可设为 nacos 或 file。' ;;
    esac

    if ( : > "/dev/tcp/127.0.0.1/$port" ) 2>/dev/null; then
        fail "端口 $port 已被占用。请核对现有进程或设置 SERVER_PORT。"
    fi
    mkdir -p -- logs
    nohup "$java" -Dfile.encoding=UTF-8 "$marker" -jar "$jar" \
        "--server.port=$port" --server.shutdown=graceful "${config_args[@]}" \
        </dev/null >>logs/backend-console.log 2>&1 9>&- &
    pid=$!
    for attempt in {1..5}; do
        if started=$(process_start_time "$pid"); then break; fi
        sleep 0.1
    done
    [[ -n $started ]] || fail '后端进程启动失败，请查看 logs/backend-console.log。'
    printf '%s %s\n' "$pid" "$started" > "$pid_file"

    printf '正在启动后端（PID %s，端口 %s，配置模式 %s）…\n' "$pid" "$port" "$mode"
    local deadline=$((SECONDS + timeout))
    while (( SECONDS < deadline )); do
        if [[ $(process_start_time "$pid") != "$started" ]]; then
            rm -f -- "$pid_file"
            fail '后端进程已退出，请查看 logs/backend-console.log。'
        fi
        if reply=$(curl --noproxy '*' -fsS --max-time 3 "$health_url" 2>/dev/null) &&
            [[ $reply =~ \"status\"[[:space:]]*:[[:space:]]*\"UP\" ]] &&
            owned_pid >/dev/null; then
            printf '后端已就绪（PID %s，端口 %s）。日志位于 logs/backend-console.log。\n' "$pid" "$port"
            return
        fi
        sleep 1
    done
    fail "后端进程 $pid 仍在运行，健康检查超时。请查看 logs/backend-console.log，必要时运行 stop.sh。"
}

command -v flock >/dev/null || fail '缺少 flock，请安装 util-linux。'
mkdir -p -- run
[[ -O run ]] || fail '请使用拥有 run/ 目录的部署账号执行启停。'
exec 9>run/control.lock
flock -n 9 || fail '另一个启停操作正在执行。'
case ${1:-start} in
    start) start_backend ;;
    stop) stop_backend ;;
    *) fail '用法：bash start.sh 或 bash stop.sh' ;;
esac

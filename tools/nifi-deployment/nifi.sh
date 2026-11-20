#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
action=${1:-status}
case $action in
    start) bash "$root/install-nifi.sh" ;;
    stop|status) ;;
    *) echo '用法 bash nifi.sh {start|stop|status}' >&2; exit 2 ;;
esac
export JAVA_HOME="$root/runtime/java"
test -x "$JAVA_HOME/bin/java" || { echo '请先执行 bash install-nifi.sh' >&2; exit 1; }
export PATH="$JAVA_HOME/bin:$PATH"
exec bash "$root/nifi-2.9.0/bin/nifi.sh" "$action"

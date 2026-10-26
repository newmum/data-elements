#!/usr/bin/env bash
set -euo pipefail
MODULE="$(cd "$(dirname "$0")/../../.." && pwd -P)"
ROOT="$MODULE/../logs/haitong-data-pull/launcher-tests"
mkdir -p "$ROOT"
ROOT="$(cd "$ROOT" && pwd -P)"
WORK="$(mktemp -d "$ROOT/discovery.XXXXXX")"
APP="$WORK/backend with spaces/haitong-data-pull"
JDK="$WORK/backend with spaces/jdk-21.0.9"
mkdir -p "$APP/scripts" "$APP/target" "$APP/config" "$JDK/bin" "$WORK/old/bin" "$WORK/new/bin" "$WORK/isolated/deploy/app"
cp "$MODULE"/scripts/{java-env,run,start-background,stop-background}.sh "$APP/scripts/"
touch "$APP/target/haitong-data-pull.jar" "$APP/config/application.yml"
cat >"$JDK/bin/java" <<'JAVA'
#!/usr/bin/env bash
if [[ "${1:-}" == '-version' ]]; then echo 'openjdk version "21.0.9"' >&2; exit 0; fi
printf '%s\n' "$PWD" >"${FAKE_INVOCATION:?}"
printf '%s\n' "$@" >>"$FAKE_INVOCATION"
if [[ "${FAKE_STAY_ALIVE:-0}" == 1 ]]; then
  trap 'exit 0' TERM
  while :; do sleep 1; done
fi
JAVA
cat >"$WORK/old/bin/java" <<'JAVA'
#!/usr/bin/env bash
if [[ "${1:-}" == '-version' ]]; then echo 'java version "1.8.0_402"' >&2; exit 0; fi
echo 'Unrecognized option: --version' >&2
exit 1
JAVA
sed 's/21.0.9/25.0.4/' "$JDK/bin/java" >"$WORK/new/bin/java"
chmod +x "$JDK/bin/java" "$WORK/old/bin/java" "$WORK/new/bin/java"
source "$APP/scripts/java-env.sh"
unset HAITONG_JAVA_BIN JAVA_HOME
export PATH="$WORK/old/bin:$PATH"
[[ "$(haitong_resolve_java "$APP")" == "$JDK/bin/java" ]]
JAVA_HOME="$WORK/old"; export JAVA_HOME
[[ "$(haitong_resolve_java "$APP")" == "$JDK/bin/java" ]]
unset JAVA_HOME
[[ "$(haitong_resolve_java "$APP" "$JDK/bin/java")" == "$JDK/bin/java" ]]
if haitong_resolve_java "$APP" "$WORK/old/bin/java" >"$WORK/rejected.out" 2>"$WORK/rejected.err"; then exit 1; fi
grep -q '不是 Java 21' "$WORK/rejected.err"
if haitong_resolve_java "$APP" "$WORK/new/bin/java" >/dev/null 2>&1; then exit 1; fi
if haitong_resolve_java "$WORK/isolated/deploy/app" >/dev/null 2>&1; then exit 1; fi
echo 'Java discovery: sibling Java 21 wins over legacy PATH/JAVA_HOME; explicit Java 8/25 rejected.'

export FAKE_INVOCATION="$WORK/foreground.txt"
(cd "$APP/scripts"; bash ./run.sh)
[[ "$(head -n 1 "$FAKE_INVOCATION")" == "$APP" ]]
export FAKE_INVOCATION="$WORK/background.txt" FAKE_STAY_ALIVE=1
cleanup() {
  if [[ -f "$APP/data/haitong.pid" ]]; then
    local child; child="$(cat "$APP/data/haitong.pid")"
    [[ ! "$child" =~ ^[0-9]+$ ]] || kill -TERM "$child" 2>/dev/null || true
  fi
}
trap cleanup EXIT
(cd "$APP/scripts"; bash ./start-background.sh)
[[ "$(head -n 1 "$FAKE_INVOCATION")" == "$APP" ]]
bash "$APP/scripts/stop-background.sh"
[[ ! -f "$APP/data/haitong.pid" ]]
for script in java-env run start-background stop-background status install-systemd; do bash -n "$MODULE/scripts/$script.sh"; done
echo 'Launchers: starting from scripts/ uses the deployment root, handles spaces, and stops only the test process.'

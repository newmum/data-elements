#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
if [[ -f application.env ]]; then
  while IFS= read -r line || [[ -n "$line" ]]; do
    line=${line%$'\r'}
    key=${line%%=*}
    [[ "$line" == *=* ]] || continue
    case "$key" in
      HAITONG_BIND|HAITONG_PORT|HAITONG_ADMIN_PASSWORD|HAITONG_STORE_KEY|NIFI_URL|NIFI_PARENT_GROUP|NIFI_USERNAME|NIFI_PASSWORD|NIFI_TRUSTSTORE|NIFI_TRUSTSTORE_PASSWORD)
        if [[ ! -v "$key" ]]; then export "$key=${line#*=}"; fi ;;
    esac
  done < application.env
fi
java=java
if [[ -n "${JAVA_HOME:-}" ]]; then java="$JAVA_HOME/bin/java"; fi
exec "$java" -Xms32m -Xmx256m -cp 'backend/haitong-flow-lite.jar:backend/lib/*' com.linewell.haitong.standalone.LiteApplication

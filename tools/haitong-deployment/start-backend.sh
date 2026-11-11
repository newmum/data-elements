#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
java=java
if [[ -n "${JAVA_HOME:-}" ]]; then java="$JAVA_HOME/bin/java"; fi
exec "$java" -Dfile.encoding=UTF-8 -jar backend/data-element.jar --spring.config.location=file:./config/application.yml

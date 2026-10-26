#!/usr/bin/env bash
# Internal helper sourced by the Linux launchers; it does not start a service.

haitong_is_java21() {
  local candidate="$1" version
  [[ -x "$candidate" ]] || return 1
  # Java 8 also understands -version, avoiding its misleading --version error.
  version="$("$candidate" -version 2>&1)" || return 1
  printf '%s\n' "$version" | grep -qE '^(openjdk|java) version "21([.+"-]|$)'
}

haitong_resolve_java() {
  local app_dir="$1" explicit="${2:-${HAITONG_JAVA_BIN:-}}" candidate parent
  if [[ -n "$explicit" ]]; then
    if haitong_is_java21 "$explicit"; then printf '%s\n' "$explicit"; return 0; fi
    echo "指定的 Java 不可执行或不是 Java 21：$explicit" >&2
    echo '请将 HAITONG_JAVA_BIN（systemd 安装时为 --java-bin）设为 JDK 21 的 bin/java。' >&2
    return 1
  fi

  if [[ -n "${JAVA_HOME:-}" ]] && haitong_is_java21 "$JAVA_HOME/bin/java"; then
    printf '%s\n' "$JAVA_HOME/bin/java"; return 0
  fi

  # Covers sibling JDKs such as /home/soft/backend/jdk-21.0.9, including
  # one additional versioned extraction directory. No whole-disk search.
  for parent in "$(dirname "$app_dir")" "$(dirname "$(dirname "$app_dir")")"; do
    for candidate in "$parent"/jdk-21*/bin/java "$parent"/java-21*/bin/java; do
      if haitong_is_java21 "$candidate"; then printf '%s\n' "$candidate"; return 0; fi
    done
  done

  candidate="$(command -v java || true)"
  if [[ -n "$candidate" ]] && haitong_is_java21 "$candidate"; then
    printf '%s\n' "$candidate"; return 0
  fi
  echo '未找到可用的 JDK/JRE 21。请设置 HAITONG_JAVA_BIN 为实际的 bin/java 绝对路径。' >&2
  echo '例如：export HAITONG_JAVA_BIN=/home/soft/backend/jdk-21.0.9/bin/java' >&2
  [[ -z "$candidate" ]] || echo "PATH 中的 Java 未满足版本要求：$candidate" >&2
  return 1
}

#!/usr/bin/env bash
set -euo pipefail
umask 077
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)
cd "$root"
[[ $(uname -s) == Linux && $(uname -m) == x86_64 ]] || { echo '需要 Linux x86_64' >&2; exit 1; }
mkdir -p logs runtime config
sha256sum -c artifact-manifest.sha256 >logs/package-check.log
if [[ ! -x runtime/java/bin/java ]]; then
    staging=$(mktemp -d "$root/runtime/java.XXXXXX")
    tar -xzf vendor/java21.tar.gz -C "$staging" --strip-components=1
    test -x "$staging/bin/java"
    mv -T -- "$staging" "$root/runtime/java"
fi
while IFS= read -r line || [[ -n $line ]]; do
    printf '%s\n' "${line//@DRIVER_DIR@/$root/drivers}"
done <config/jdbc-driver-locations.template >config/jdbc-driver-locations.conf
echo 'NiFi 2.9.0、自定义组件、JDK 21及驱动已就绪'
printf '驱动映射文件 %s/config/jdbc-driver-locations.conf\n' "$root"

"""Export current Nacos YAML to Git with credentials replaced by environment references.

Read only: this tool never publishes configuration to Nacos.
"""
import argparse
import copy
import json
import os
from pathlib import Path
import re
import sys
import urllib.parse
import urllib.request

import yaml


def sensitive_key(key):
    normalized = re.sub(r"[^a-z0-9]", "", str(key).lower())
    if normalized in {"tokenname", "tokenstyle", "tokenprefix", "tokentimeout", "tokenttl"}:
        return False
    return (any(part in normalized for part in
                ("password", "passwd", "secret", "token", "credential", "keytab", "privatekey",
                 "accesskey", "apikey", "appkey", "sharedkey", "synckey", "secretkey"))
            or normalized in {"user", "username", "loginusername", "authorization", "key"})


def environment_reference(path, value):
    # Keep the deployment's existing variable name, but never its secret default.
    match = re.fullmatch(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::.*)?\}", str(value), re.DOTALL)
    name = match.group(1) if match else "NACOS_" + re.sub(r"[^A-Z0-9]+", "_", "_".join(path).upper()).strip("_")
    return "${" + name + "}"


def redact_configuration(config):
    secrets = set()

    def redact(value, path=()):
        if isinstance(value, dict):
            return {key: redact(item, path + (str(key),)) for key, item in value.items()}
        if isinstance(value, list):
            return [redact(item, path + (str(index),)) for index, item in enumerate(value)]
        if value is not None and value != "" and path and sensitive_key(path[-1]):
            reference = re.fullmatch(r"\$\{[A-Za-z_][A-Za-z0-9_]*(?::.*)?\}", str(value), re.DOTALL)
            if not reference and len(str(value)) >= 4:
                secrets.add(str(value))
            default = re.fullmatch(r"\$\{[A-Za-z_][A-Za-z0-9_]*:(.*)\}", str(value), re.DOTALL)
            if default and len(default.group(1)) >= 4:
                secrets.add(default.group(1))
            return environment_reference(path, value)
        if isinstance(value, str) and (re.search(r"://[^/\s]*@", value)
                or re.search(r"(?i)[?;&](?:password|passwd|user|username|token|secret|api[-_]?key)=", value)):
            secrets.add(value)
            return environment_reference(path, value)
        return value

    result = redact(copy.deepcopy(config))
    # Credentials occasionally appear in an additional URL/script field as well.
    def scrub(value):
        if isinstance(value, dict):
            return {key: scrub(item) for key, item in value.items()}
        if isinstance(value, list):
            return [scrub(item) for item in value]
        if isinstance(value, str):
            if re.fullmatch(r"\$\{[A-Za-z_][A-Za-z0-9_]*\}", value):
                return value
            for secret in sorted(secrets, key=len, reverse=True):
                value = value.replace(secret, "<REDACTED>")
        return value
    return scrub(result)


def export_yaml(config, data_id, namespace, group):
    clean = redact_configuration(config)
    header = ("# Nacos 配置版本源（脱敏）；不要直接发布占位符覆盖运行密钥。\n"
              f"# Data ID: {data_id}; Group: {group}; Namespace: {namespace}\n"
              "# 部署时由受限环境提供 ${...} 变量；修改后核对运行配置的覆盖项。\n")
    return header + yaml.safe_dump(clean, allow_unicode=True, sort_keys=False, width=120)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-config", type=Path,
                        default=Path.home() / ".codex/private/data-elements/local-runtime.private.json")
    parser.add_argument("--data-id", required=True)
    parser.add_argument("--group", default="DEFAULT_GROUP")
    parser.add_argument("--namespace")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+\.ya?ml", args.data_id):
        raise ValueError("Data ID must be a YAML filename")
    env = dict(os.environ)
    if args.runtime_config.exists():
        private = json.loads(args.runtime_config.read_text(encoding="utf-8-sig"))
        env.update({key: str(value) for key, value in private.get("environment", {}).items()})
    host, port = env["NACOS_HOST"], env.get("NACOS_PORT", "8848")
    base = "http://" + host + ":" + port + "/nacos"
    namespace = args.namespace or env.get("NACOS_NAMESPACE", "data-element")
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    login = urllib.request.Request(base + "/v1/auth/login", data=urllib.parse.urlencode({
        "username": env["NACOS_USER"], "password": env["NACOS_PASSWORD"]}).encode())
    with opener.open(login, timeout=15) as response:
        token = json.load(response)["accessToken"]
    query = urllib.parse.urlencode({"dataId": args.data_id, "group": args.group,
                                   "tenant": namespace, "accessToken": token})
    with opener.open(base + "/v1/cs/configs?" + query, timeout=15) as response:
        config = yaml.safe_load(response.read().decode("utf-8"))
    if not isinstance(config, dict):
        raise ValueError("Nacos YAML must contain a configuration mapping")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(export_yaml(config, args.data_id, namespace, args.group), encoding="utf-8", newline="\n")
    print(json.dumps({"dataId": args.data_id, "output": str(args.output), "credentials": "environment-references"}))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Exception messages from HTTP/YAML libraries can contain the input or URL token.
        print("Nacos export failed (" + type(error).__name__ + "); no configuration or credentials printed.", file=sys.stderr)
        sys.exit(1)

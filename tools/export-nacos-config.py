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
import tempfile
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


def local_environment(config, runtime_environment):
    """Materialize only referenced business values for an explicitly private, no-Nacos environment."""
    public = redact_configuration(config)
    overrides = json.loads(runtime_environment.get("SPRING_APPLICATION_JSON") or "{}")
    missing = set()
    values = {}

    def lookup(mapping, path):
        dotted = ".".join(path)
        if dotted in mapping:
            return mapping[dotted], True
        current = mapping
        for part in path:
            if not isinstance(current, dict):
                return None, False
            normalize = lambda key: re.sub(r"[-_]", "", str(key)).lower()
            key = next((key for key in current if normalize(key) == normalize(part)), None)
            if key is None:
                return None, False
            current = current[key]
        return current, True

    def resolve(value):
        if not isinstance(value, str):
            return str(value).lower() if isinstance(value, bool) else str(value)
        for _ in range(8):
            def replace(match):
                name, _, default = match.group(1).partition(":")
                if name in runtime_environment:
                    return str(runtime_environment[name])
                if ":" in match.group(1):
                    return default
                missing.add(name)
                return match.group(0)
            next_value = re.sub(r"\$\{([^{}]+)\}", replace, value)
            if next_value == value:
                return value
            value = next_value
        raise ValueError("Recursive environment reference")

    def visit(clean, original, path=()):
        if tuple(re.sub(r"[-_]", "", part).lower() for part in path[:3]) == ("spring", "cloud", "nacos"):
            return
        if isinstance(clean, dict):
            for key, item in clean.items():
                visit(item, original[key], path + (str(key),))
        elif isinstance(clean, list):
            for index, item in enumerate(clean):
                visit(item, original[index], path + (str(index),))
        elif isinstance(clean, str):
            # Nonsecret references keep the same environment value when one is explicitly configured.
            for name in re.findall(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::[^}]*)?\}", clean):
                if name in runtime_environment:
                    values[name] = str(runtime_environment[name])
            reference = re.fullmatch(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}", clean)
            if reference:
                effective, found = lookup(overrides, path)
                effective = effective if found else original
                values[reference.group(1)] = resolve(effective)

    visit(public, config)
    if missing:
        raise ValueError("Missing runtime environment variables: " + ", ".join(sorted(missing)))
    for name in ("IDAAS_TRANSPORT_KEY", "IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP",
                 "JDBC_DRIVER_DIRECTORY", "DATA_ELEMENT_PUSH_REGISTRY_RELAY_BASE_URL",
                 "DATA_ELEMENT_PUSH_REGISTRY_RELAY_TIMEOUT_SECONDS", "DATA_ELEMENT_PUSH_REGISTRY_SHARED_KEY"):
        if name in runtime_environment:
            values[name] = str(runtime_environment[name])
    def business_overrides(mapping, path=()):
        result = {}
        for key, value in mapping.items():
            parts = path + tuple(re.sub(r"[-_]", "", item).lower() for item in str(key).split("."))
            if parts[:3] == ("spring", "cloud", "nacos") or parts[:2] == ("spring", "config"):
                continue
            if isinstance(value, dict):
                value = business_overrides(value, parts)
                if not value:
                    continue
            result[key] = value
        return result
    filtered_overrides = business_overrides(overrides)
    if filtered_overrides:
        values["SPRING_APPLICATION_JSON"] = json.dumps(filtered_overrides, ensure_ascii=False)
    return {"environment": values}


def write_private_environment(path, configuration):
    workspace = Path(__file__).resolve().parents[1]
    path = path.expanduser().resolve()
    if path == workspace or workspace in path.parents:
        raise ValueError("Private environment must be outside the Git workspace")
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=path.name + ".", dir=path.parent)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(configuration, stream, ensure_ascii=False, indent=2)
        os.chmod(temporary, 0o600)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main():
    from nacos_config import NacosConfigReader
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-config", type=Path,
                        default=Path.home() / ".codex/private/data-elements/local-runtime.private.json")
    parser.add_argument("--data-id", required=True)
    parser.add_argument("--group", default="DEFAULT_GROUP")
    parser.add_argument("--namespace")
    parser.add_argument("--output", type=Path,
                        help="Public YAML output; defaults to db/nacos/<data-id>")
    parser.add_argument("--local-environment-output", type=Path,
                        help="Explicitly save referenced business values to a private JSON outside the workspace for no-Nacos startup")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_.-]+\.ya?ml", args.data_id):
        raise ValueError("Data ID must be a YAML filename")
    if args.output is None:
        args.output = Path(__file__).resolve().parents[1] / "db" / "nacos" / args.data_id
    if args.local_environment_output:
        private_path = args.local_environment_output.expanduser().resolve()
        workspace = Path(__file__).resolve().parents[1]
        if private_path == workspace or workspace in private_path.parents:
            raise ValueError("Private environment must be outside the Git workspace")
    env = dict(os.environ)
    if args.runtime_config.exists():
        private = json.loads(args.runtime_config.read_text(encoding="utf-8-sig"))
        env.update({key: str(value) for key, value in private.get("environment", {}).items()})
    host, port = env["NACOS_HOST"], env.get("NACOS_PORT", "8848")
    base = "http://" + host + ":" + port + "/nacos"
    namespace = args.namespace or env.get("NACOS_NAMESPACE", "data-element")
    reader = NacosConfigReader(base, env["NACOS_USER"], env["NACOS_PASSWORD"])
    config = yaml.safe_load(reader.read(args.data_id, args.group, namespace))
    if not isinstance(config, dict):
        raise ValueError("Nacos YAML must contain a configuration mapping")
    private_environment = local_environment(config, env) if args.local_environment_output else None
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(export_yaml(config, args.data_id, namespace, args.group), encoding="utf-8", newline="\n")
    if private_environment is not None:
        write_private_environment(args.local_environment_output, private_environment)
    print(json.dumps({"dataId": args.data_id, "output": str(args.output), "credentials": "environment-references"}))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Exception messages from HTTP/YAML libraries can contain the input or URL token.
        print("Nacos export failed (" + type(error).__name__ + "); no configuration or credentials printed.", file=sys.stderr)
        sys.exit(1)

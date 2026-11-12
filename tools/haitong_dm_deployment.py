"""DM8 bootstrap and Linux runtimes for the independent Haitong deployment."""

import hashlib
import json
import re


def quote(value):
    return "NULL" if value is None else "'" + value.replace("'", "''") + "'"


def clob(value):
    # Keep each literal well below DM's SQL string limit, including multibyte text.
    return " || ".join(f"TO_CLOB({quote(value[pos:pos + 500])})" for pos in range(0, len(value), 500))


def dm_control(root, requested):
    source = (root / "db/baseline/init.sql").read_text(encoding="utf-8")
    tables = re.findall(r"(?ms)^CREATE TABLE `([^`]+)` \(\n(.*?)\n\) ENGINE=[^\n]*? COMMENT='((?:''|[^'])*)';", source)
    statements = []
    for table, definition, comment in tables:
        if table not in requested:
            continue
        columns, notes, indexes = [], [], []
        primary = None
        for line in definition.splitlines():
            line = line.strip().removesuffix(",").removesuffix(" USING BTREE")
            if line.startswith("PRIMARY KEY"):
                primary = line.removeprefix("PRIMARY KEY ").replace("`", '"').lower()
                columns.append(f"PRIMARY KEY {primary}")
                continue
            key = re.fullmatch(r"(UNIQUE )?KEY `([^`]+)` (\(.*\))", line)
            if key:
                unique, name, fields = key.groups()
                fields = fields.replace("`", '"').lower()
                indexes.append((unique, name, fields))
                continue
            col = re.fullmatch(r"`([^`]+)` (.+?) COMMENT ('(?:''|[^'])*')", line)
            if not col:
                raise ValueError(f"Unsupported control table definition in {table}: {line}")
            name, spec, note = col.groups()
            spec = re.sub(r" (?:COLLATE|CHARACTER SET) \w+", "", spec)
            spec = re.sub(r"\blongtext\b|\btext\b", "CLOB", spec)
            spec = re.sub(r"\bdatetime\b", "TIMESTAMP", spec)
            spec = re.sub(r"\btinyint\b", "SMALLINT", spec)
            columns.append(f'"{name.lower()}" {spec}')
            notes.append(f'COMMENT ON COLUMN "{table}"."{name.lower()}" IS {note};')
        statements.append(f'CREATE TABLE "{table}" (\n' + ",\n".join(columns) + "\n);")
        statements.extend([f'COMMENT ON TABLE "{table}" IS \'{comment}\';', *notes])
        # The primary key already provides the unique index. DM rejects the redundant MySQL definition.
        statements.extend(f'CREATE {unique or ""}INDEX "{name}" ON "{table}"{fields};'
                          for unique, name, fields in indexes if not unique or fields != primary)
    if len([stmt for stmt in statements if stmt.startswith("CREATE TABLE")]) != len(requested):
        raise ValueError("DM8 control schema is missing a required table")
    return "\n".join(statements)


def dm_tenant(root, requested, tenant_id):
    old = "baseline_beijing_gd"
    source = (root / f"db/{old}/init.sql").read_text(encoding="utf-8")
    tables = {name: sql for sql, name in re.findall(r'(?ms)^(CREATE TABLE "baseline_beijing_gd"\."([^"]+)"\n.*?;)\s*$', source)}
    selected = set(requested)
    while True:
        missing = selected - tables.keys()
        if missing:
            raise ValueError(f"DM8 tenant schema is missing tables: {sorted(missing)}")
        required = {ref for table in selected for ref in re.findall(r'REFERENCES "baseline_beijing_gd"\."([^"]+)"', tables[table])}
        if required <= selected:
            break
        selected |= required
    statements = [sql for name, sql in tables.items() if name in selected]
    indexes = {}
    for line in source.splitlines():
        if line.startswith(("CREATE OR REPLACE ", "COMMENT ON ", "ALTER TABLE ")):
            target = re.search(r'(?:\bON (?:TABLE |COLUMN )?|^ALTER TABLE )"baseline_beijing_gd"\."([^"]+)"', line)
            if target and target[1] in selected:
                index = re.fullmatch(r'CREATE OR REPLACE (UNIQUE )?\s*INDEX "[^"]+" ON (.+)', line)
                if index:
                    unique, definition = index.groups()
                    # Some DM exports carry a generated index and an explicitly named one with the same columns.
                    if definition not in indexes or unique and "UNIQUE INDEX" not in indexes[definition]:
                        indexes[definition] = line
                    continue
                statements.append(line)
                if line.startswith("CREATE OR REPLACE TRIGGER "):
                    statements.append("/")
    ddl = "\n\n".join([*statements, *indexes.values()]).replace(f'"{old}".', '"baseline_haitong".')
    return re.sub(r'''("tenant_id"[^\n]*?DEFAULT )'\d+' ''', rf"\g<1>'{tenant_id}' ", ddl), sorted(selected)


def dameng_database_files(root, control_tables, tenant_tables, tenant_id, magic, seed):
    tenant_ddl, tenant_tables = dm_tenant(root, tenant_tables, tenant_id)
    magic_records, magic_paths = magic
    control_seed, tenant_seed = (item.replace("NOW(6)", "CURRENT_TIMESTAMP") for item in seed)
    resources = "\n".join(
        'INSERT INTO "api_file_t" ("tid","file_path","file_content","is_del") VALUES '
        f"({quote(row_id)},{quote(path)},{clob(content)},0);" for row_id, path, content in magic_records
    )
    header = "-- DM8 新装专用。已有生产数据时不要执行。\nWHENEVER SQLERROR EXIT 1 ROLLBACK\nSET DEFINE OFF;\n"

    def script(schema, body):
        # DIsql treats CREATE SCHEMA as compound SQL and waits for a slash delimiter.
        return (header + f'CREATE SCHEMA "{schema}";\n/\nSET SCHEMA "{schema}";\n\n' + body + "\nCOMMIT;\n").encode("utf-8")

    return {
        "db/new-install-only/01-control-dm8.sql": script("baseline", dm_control(root, control_tables) + "\n" + control_seed + resources),
        "db/new-install-only/02-tenant-dm8.sql": script("baseline_haitong", tenant_ddl + "\n" + tenant_seed),
        "db/scope.json": json.dumps({"dialect": "DM8", "controlSchema": "baseline", "tenantSchema": "baseline_haitong",
                                     "controlTables": sorted(control_tables), "tenantTables": tenant_tables,
                                     "magicResources": magic_paths}, ensure_ascii=False, indent=2).encode("utf-8"),
    }


def dm_config(templates):
    source_config = (templates / "application.yml").read_text(encoding="utf-8")
    config = source_config.replace("com.mysql.cj.jdbc.Driver", "dm.jdbc.driver.DmDriver")
    config = re.sub(r'^\s*url: .*baseline(?:_haitong)?\?.*$', lambda m: (
        re.match(r"\s*", m[0])[0] + 'url: "jdbc:dm://127.0.0.1:5236?schema=' +
        ("baseline_haitong" if "baseline_haitong" in m[0] else "baseline") + '"'), config, flags=re.M)
    config = config.replace("${HAITONG_DB_USER:haitong}", "HAITONG_APP")
    config = config.replace("${HAITONG_TENANT_DB_USER:${HAITONG_DB_USER:haitong}}", "HAITONG_APP")
    config = config.replace("${HAITONG_TENANT_DB_USER:HAITONG_APP}", "HAITONG_APP")
    config = config.replace("${HAITONG_TENANT_DB_PASSWORD:${HAITONG_DB_PASSWORD}}", '"CHANGE_ME"')
    config = config.replace("${HAITONG_DB_PASSWORD}", '"CHANGE_ME"')
    config = config.replace("${HAITONG_REDIS_HOST:127.0.0.1}", "127.0.0.1").replace("${HAITONG_REDIS_PORT:6379}", "6379")
    config = config.replace("${HAITONG_REDIS_DATABASE:0}", "0").replace("${HAITONG_REDIS_PASSWORD:}", '""')
    config = config.replace("validation-query: SELECT 1", "validation-query: SELECT 1 FROM DUAL")
    config = config.replace("data-source-type: mysql", "data-source-type: oracle")
    config = config.replace("server:\n", "# 修改控制库、租户库和 Redis 的连接后运行 bash start.sh。\nserver:\n  shutdown: graceful\n", 1)
    return config


def linux_runtime_files(templates, runtime_dir):
    base = templates / "linux-dm8"
    files = {"config/application.yml": dm_config(templates).encode("utf-8"), "部署说明.md": base / "deployment.md"}
    files.update({path.name: path for path in base.glob("*.sh")})
    files["config/Caddyfile"] = base / "Caddyfile"
    files["config/ports.env"] = base / "ports.env"
    files["tools/Dm8Admin.java"] = base / "Dm8Admin.java"
    caddy_assets = json.loads((runtime_dir / "caddy-assets.json").read_text(encoding="utf-8-sig"))
    if not isinstance(caddy_assets, list):
        raise ValueError("Expected Caddy release metadata for both Linux architectures")
    checksums = next(asset for asset in caddy_assets if asset["name"].endswith("_checksums.txt"))
    checksum_file = runtime_dir / checksums["name"]
    if "sha256:" + hashlib.sha256(checksum_file.read_bytes()).hexdigest() != checksums["digest"]:
        raise ValueError("Invalid Caddy release checksum file")
    verified = {}
    for row in checksum_file.read_text(encoding="utf-8").splitlines():
        checksum, name = row.split(maxsplit=1)
        verified[name.strip().lstrip("*")] = checksum
    sources = []
    for arch, caddy_arch in [("x64", "amd64"), ("aarch64", "arm64")]:
        metadata = runtime_dir / f"corretto-{arch}.json"
        if not metadata.is_file():
            metadata = runtime_dir / f"jdk-{arch}.json"
        jdk = json.loads(metadata.read_text(encoding="utf-8-sig"))
        caddy = next(asset for asset in caddy_assets if asset["name"].endswith(f"_linux_{caddy_arch}.tar.gz"))
        caddy_digest = caddy["digest"].removeprefix("sha256:")
        with (runtime_dir / caddy["name"]).open("rb") as stream:
            if hashlib.file_digest(stream, "sha512").hexdigest() != verified[caddy["name"]]:
                raise ValueError(f"Invalid Caddy release checksum for {caddy['name']}")
        for name, url, sha, package in [(jdk["Name"], jdk["URL"], jdk["SHA256"], "jdk21.tar.gz"),
                                        (caddy["name"], caddy["browser_download_url"], caddy_digest, "caddy.tar.gz")]:
            archive = runtime_dir / name
            with archive.open("rb") as stream:
                if hashlib.file_digest(stream, "sha256").hexdigest().lower() != sha.lower():
                    raise ValueError(f"Invalid download checksum for {name}")
            files[f"runtime/{arch}/{package}"] = archive
            sources.append({"architecture": arch, "artifact": name, "url": url, "sha256": sha})
    files["runtime/SOURCES.json"] = json.dumps(sources, indent=2).encode("utf-8")
    return files

#!/usr/bin/env python3
"""Read current database metadata and export complete, data-free init.sql files.

Requires pymysql for MySQL and jaydebeapi/JPype1 plus an existing DM JDBC JAR
for Dameng. Connection settings stay outside Git. No DDL/DML is executed.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import tempfile
from urllib.parse import urlsplit


ROOT = Path(__file__).resolve().parents[1]
PLACEHOLDER = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::([^}]*))?\}")


def resolve(value):
    if not isinstance(value, str):
        return value

    def replace(match):
        name, default = match.groups()
        if name in os.environ:
            return os.environ[name]
        if default is not None:
            return default
        raise ValueError(f"Required environment variable {name} is absent")

    return PLACEHOLDER.sub(replace, value)


def connections(config):
    control = {key: resolve(value) for key, value in config["control"].items()}
    yield "control", "mysql", control["database"], control
    tenant = config["nacos_tenant_database"]
    active = sorted(set(tenant["tenant-bindings"].values()))
    for key in active:
        source = {name: resolve(value) for name, value in tenant["data-sources"][key].items()}
        url = source["url"]
        if url.startswith("jdbc:mysql:"):
            parsed = urlsplit(url[5:])
            schema = source.get("schema") or parsed.path.strip("/")
            yield key, "mysql", schema, {
                "host": parsed.hostname,
                "port": parsed.port or 3306,
                "database": schema,
                "user": source["username"],
                "password": source["password"],
                "charset": "utf8mb4",
            }
        elif url.startswith("jdbc:dm:"):
            schema = source.get("schema")
            if not schema:
                raise ValueError(f"Dameng datasource {key} needs an explicit schema")
            yield key, "dameng", schema, source
        else:
            raise ValueError(f"Unsupported active datasource {key}")


def mysql_identifier(name):
    return "`" + name.replace("`", "``") + "`"


def quoted(name):
    return '"' + name.replace('"', '""') + '"'


def literal(value):
    return "'" + value.replace("'", "''") + "'"


def normalize(ddl):
    return str(ddl).replace("\r\r\n", "\n").replace("\r\n", "\n").replace("\r", "\n").strip().rstrip(";") + ";"


def without_definer(ddl):
    # A source server account must not become a required account on a new server.
    return re.sub(r"\bDEFINER\s*=\s*(?:`(?:``|[^`])*`|'(?:''|[^'])*'|[^\s@]+)\s*@\s*(?:`(?:``|[^`])*`|'(?:''|[^'])*'|[^\s]+)\s*", "", ddl, flags=re.I)


def ordered_views(definitions):
    names = set(definitions)
    dependencies = {
        name: {other for other in names if other != name and re.search(r"(?:`|\"|\b)" + re.escape(other) + r"(?:`|\"|\b)", ddl, re.I)}
        for name, ddl in definitions.items()
    }
    pending, ordered = set(names), []
    while pending:
        ready = sorted(name for name in pending if not dependencies[name] & pending)
        if not ready:
            raise ValueError("View dependencies contain a cycle; manual initialization ordering is required")
        ordered.extend(ready)
        pending.difference_update(ready)
    return ordered


def mysql_export(schema, options):
    import pymysql

    connection = pymysql.connect(**options, connect_timeout=15, read_timeout=120, write_timeout=30, autocommit=True)
    cursor = connection.cursor()
    ident = mysql_identifier
    counts = dict.fromkeys(["tables", "views", "indexes", "foreignKeys", "triggers", "procedures", "functions", "events"], 0)
    try:
        cursor.execute("SHOW CREATE DATABASE " + ident(schema))
        create_database = cursor.fetchone()[1]
        lines = [
            "-- 当前完整结构基线：仅适用于新空库，不含业务数据、账号或授权。",
            "-- 不得直接在现有数据库执行；现有库变更须单独审阅差异并备份。",
            "SET NAMES utf8mb4;",
            normalize(create_database),
            "USE " + ident(schema) + ";",
            "SET FOREIGN_KEY_CHECKS=0;",
        ]
        cursor.execute("SELECT TABLE_NAME,TABLE_TYPE FROM information_schema.TABLES WHERE TABLE_SCHEMA=%s ORDER BY TABLE_NAME", [schema])
        table_rows = cursor.fetchall()
        views = {}
        for name, kind in table_rows:
            cursor.execute("SHOW CREATE TABLE " + ident(schema) + "." + ident(name))
            ddl = cursor.fetchone()[1]
            if kind == "VIEW":
                views[name] = without_definer(ddl)
                continue
            # The next generated ID is row state, not schema. Preserve the AUTO_INCREMENT column attribute.
            ddl = re.sub(r"\sAUTO_INCREMENT=\d+\b", "", ddl)
            lines.extend(["", "-- TABLE " + name, normalize(ddl)])
            counts["tables"] += 1
        cursor.execute("SELECT COUNT(*) FROM (SELECT DISTINCT TABLE_NAME,INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=%s) indexes_in_schema", [schema])
        counts["indexes"] = int(cursor.fetchone()[0])
        cursor.execute("SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=%s AND CONSTRAINT_TYPE='FOREIGN KEY'", [schema])
        counts["foreignKeys"] = int(cursor.fetchone()[0])
        cursor.execute("SELECT ROUTINE_NAME,ROUTINE_TYPE FROM information_schema.ROUTINES WHERE ROUTINE_SCHEMA=%s ORDER BY ROUTINE_TYPE,ROUTINE_NAME", [schema])
        for name, kind in cursor.fetchall():
            cursor.execute("SHOW CREATE " + kind + " " + ident(schema) + "." + ident(name))
            row = cursor.fetchone()
            ddl = row[2]
            if not ddl:
                raise ValueError("Routine DDL is not visible to the export account")
            lines.extend(["", "DELIMITER $$", normalize(without_definer(ddl)).rstrip(";") + "$$", "DELIMITER ;"])
            counts["procedures" if kind == "PROCEDURE" else "functions"] += 1
        for name in ordered_views(views):
            lines.extend(["", "-- VIEW " + name, normalize(views[name])])
            counts["views"] += 1
        cursor.execute("SELECT TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=%s ORDER BY TRIGGER_NAME", [schema])
        for (name,) in cursor.fetchall():
            cursor.execute("SHOW CREATE TRIGGER " + ident(schema) + "." + ident(name))
            ddl = cursor.fetchone()[2]
            if not ddl:
                raise ValueError("Trigger DDL is not visible to the export account")
            lines.extend(["", "DELIMITER $$", normalize(without_definer(ddl)).rstrip(";") + "$$", "DELIMITER ;"])
            counts["triggers"] += 1
        cursor.execute("SELECT EVENT_NAME FROM information_schema.EVENTS WHERE EVENT_SCHEMA=%s ORDER BY EVENT_NAME", [schema])
        for (name,) in cursor.fetchall():
            cursor.execute("SHOW CREATE EVENT " + ident(schema) + "." + ident(name))
            ddl = cursor.fetchone()[3]
            lines.extend(["", "DELIMITER $$", normalize(without_definer(ddl)).rstrip(";") + "$$", "DELIMITER ;"])
            counts["events"] += 1
        lines.extend(["", "SET FOREIGN_KEY_CHECKS=1;", ""])
        return schema, "\n".join(lines), counts, {"definitions": "SHOW CREATE", "routineDefiners": "omitted", "autoIncrementCounters": "omitted"}
    finally:
        cursor.close()
        connection.close()


def dm_jar():
    configured = os.environ.get("DATA_ELEMENTS_DM_JDBC_JAR")
    if configured:
        path = Path(configured)
        if not path.is_file():
            raise ValueError("DATA_ELEMENTS_DM_JDBC_JAR does not name an existing driver")
        return path
    jars = sorted((Path.home() / ".m2/repository/com/dameng").glob("**/DmJdbcDriver*.jar"), key=lambda path: path.stat().st_mtime, reverse=True)
    if not jars:
        raise ValueError("Set DATA_ELEMENTS_DM_JDBC_JAR to an existing Dameng JDBC driver")
    return jars[0]


def dameng_export(schema, options):
    import jaydebeapi

    connection = jaydebeapi.connect(options["driver-class-name"], options["url"], [options["username"], options["password"]], str(dm_jar()))
    cursor = connection.cursor()

    def query(sql, values=()):
        cursor.execute(sql, list(values))
        return cursor.fetchall()

    def ddl(kind, name, owner):
        value = query("SELECT DBMS_METADATA.GET_DDL(?,?,?) FROM DUAL", [kind, name, owner])[0][0]
        value = value.getSubString(1, int(value.length())) if hasattr(value, "getSubString") else str(value)
        return normalize(value)

    counts = dict.fromkeys(["tables", "views", "indexes", "explicitIndexes", "foreignKeys", "triggers", "procedures", "functions", "sequences"], 0)
    try:
        objects = query("SELECT OBJECT_TYPE,OBJECT_NAME FROM DBA_OBJECTS WHERE OWNER=? ORDER BY OBJECT_TYPE,OBJECT_NAME", [schema])
        schemas = [name for kind, name in objects if kind == "SCH"]
        actual_schema = schemas[0] if len(schemas) == 1 else schema
        supported = {"SCH", "TABLE", "VIEW", "INDEX", "CONSTRAINT", "TRIGGER", "PROCEDURE", "FUNCTION", "SEQUENCE"}
        unsupported = sorted({kind for kind, _ in objects} - supported)
        if unsupported:
            raise ValueError("Unsupported Dameng schema objects: " + ", ".join(unsupported))
        lines = [
            "-- 当前完整达梦结构基线：仅适用于新空 schema，不含业务数据、账号或授权。",
            "-- 由具有 schema 创建权限的既有 DBA 执行；不创建用户、不导出口令。",
            "CREATE SCHEMA " + quoted(actual_schema) + ";",
            "SET SCHEMA " + quoted(actual_schema) + ";",
        ]
        for kind, name in objects:
            if kind == "SEQUENCE":
                lines.extend(["", ddl(kind, name, schema)])
                counts["sequences"] += 1
        for kind, name in objects:
            if kind == "TABLE":
                lines.extend(["", "-- TABLE " + name, ddl(kind, name, schema)])
                counts["tables"] += 1
        constraints = query("SELECT CONSTRAINT_NAME,CONSTRAINT_TYPE,INDEX_NAME FROM DBA_CONSTRAINTS WHERE OWNER=? ORDER BY CONSTRAINT_NAME", [schema])
        implicit_indexes = {row[2] for row in constraints if row[2]}
        indexes = query("SELECT INDEX_NAME,INDEX_TYPE FROM DBA_INDEXES WHERE OWNER=? ORDER BY INDEX_NAME", [schema])
        counts["indexes"] = len(indexes)
        for name, index_type in indexes:
            # Table DDL already creates primary/unique backing indexes and heap/cluster storage indexes.
            if name in implicit_indexes or index_type == "CLUSTER":
                continue
            lines.extend(["", ddl("INDEX", name, schema)])
            counts["explicitIndexes"] += 1
        # GET_DDL(TABLE) includes local constraints; foreign keys must follow all tables.
        # Verify their inclusion before appending an independently exported constraint.
        foreign_keys = [(name, kind) for name, kind, _ in constraints if kind == "R"]
        counts["foreignKeys"] = len(foreign_keys)
        for name, _ in foreign_keys:
            statement = ddl("CONSTRAINT", name, schema)
            if not any(quoted(name) in line for line in lines):
                lines.extend(["", statement])
        for kind, name in objects:
            if kind in ("PROCEDURE", "FUNCTION"):
                lines.extend(["", ddl(kind, name, schema), "/"])
                counts["procedures" if kind == "PROCEDURE" else "functions"] += 1
        views = {name: ddl(kind, name, schema) for kind, name in objects if kind == "VIEW"}
        for name in ordered_views(views):
            lines.extend(["", "-- VIEW " + name, views[name]])
            counts["views"] += 1
        for name, comment in query("SELECT TABLE_NAME,COMMENTS FROM DBA_TAB_COMMENTS WHERE OWNER=? AND COMMENTS IS NOT NULL ORDER BY TABLE_NAME", [schema]):
            lines.append("COMMENT ON TABLE " + quoted(actual_schema) + "." + quoted(name) + " IS " + literal(str(comment)) + ";")
        for table, column, comment in query("SELECT TABLE_NAME,COLUMN_NAME,COMMENTS FROM DBA_COL_COMMENTS WHERE OWNER=? AND COMMENTS IS NOT NULL ORDER BY TABLE_NAME,COLUMN_NAME", [schema]):
            lines.append("COMMENT ON COLUMN " + quoted(actual_schema) + "." + quoted(table) + "." + quoted(column) + " IS " + literal(str(comment)) + ";")
        for kind, name in objects:
            if kind == "TRIGGER":
                lines.extend(["", ddl(kind, name, schema), "/"])
                counts["triggers"] += 1
        lines.append("")
        return actual_schema, "\n".join(lines), counts, {"definitions": "DBMS_METADATA.GET_DDL", "comments": "DBA_TAB_COMMENTS/DBA_COL_COMMENTS", "implicitIndexes": "included in table DDL"}
    finally:
        cursor.close()
        connection.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", type=Path, default=Path(os.environ.get("DATA_ELEMENTS_RESOURCE_CONFIG", str(Path.home() / ".codex/private/data-elements/resources.local.json"))))
    parser.add_argument("--output", type=Path, default=ROOT / "db")
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding="utf-8-sig"))
    exports = []
    # Prepare every database successfully before replacing any canonical baseline.
    for key, dialect, schema, options in connections(config):
        try:
            actual_schema, sql, counts, notes = (mysql_export if dialect == "mysql" else dameng_export)(schema, options)
        except Exception as error:
            # JDBC/PyMySQL exception text can contain connection settings; keep it private.
            raise SystemExit(f"Export failed for datasource {key} ({dialect}, {schema}): {type(error).__name__}; canonical files were not replaced") from None
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_$]*", actual_schema):
            raise SystemExit(f"Datasource {key} needs an explicit safe local directory mapping; canonical files were not replaced")
        exports.append((key, dialect, actual_schema, sql, counts, notes))
        print(f"Read {key}: {dialect} {actual_schema}, {counts['tables']} tables, {counts['views']} views")
    manifest = {"formatVersion": 1, "scope": "structure-only", "routingSource": "private current Nacos API/runtime snapshot", "schemas": []}
    args.output.mkdir(parents=True, exist_ok=True)
    for key, dialect, schema, sql, counts, notes in exports:
        relative = Path(schema) / "init.sql"
        destination = args.output / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile("w", encoding="utf-8", newline="\n", dir=destination.parent, delete=False) as temporary:
            temporary.write(sql)
        Path(temporary.name).replace(destination)
        manifest["schemas"].append({"dataSourceKey": key, "schema": schema, "dialect": dialect, "file": relative.as_posix(), "objects": counts, "sha256": hashlib.sha256(sql.encode("utf-8")).hexdigest(), "exportNotes": notes})
    (args.output / "schema-manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Exported {len(exports)} current schema baselines; no live database was changed")


if __name__ == "__main__":
    main()

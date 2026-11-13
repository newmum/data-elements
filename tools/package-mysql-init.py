#!/usr/bin/env python3
"""Assemble a standalone MySQL schema initialization file for a new deployment."""

import argparse
import hashlib
import json
from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / "db"
DEFAULT_OUTPUT = ROOT / "logs/mysql-initialization/data-elements-mysql-init.sql"
POLICE_ID = "2084109831682699264"
TENANT_DEFAULT = re.compile(r"(?m)^(\s*`tenant_id`[^\n]*? DEFAULT )'(\d+)'")
INSECURE_TLS = re.compile(r"(?m)^(  `insecure_tls` tinyint NOT NULL DEFAULT )'[01]'")
# New environment names, existing MySQL structure sources, and tenant settings.
SCHEMAS = (
    ("baseline", "baseline", None, None),
    ("baseline_ga", "baseline_ga", POLICE_ID, None),
    ("baseline_broadcasting", "baseline_ga", "2084109831682699265", 1),
    ("baseline_sea_fishery", "baseline_sea_fishery", "2084109831682699266", None),
    ("baseline_chengtian", "baseline_ga", "2084109831682699267", 0),
    ("haitong", "haitong", None, None),
)


def load_schema(name, manifest):
    entry = manifest[name]
    if entry["dialect"] != "mysql" or entry["file"] != f"{name}/init.sql":
        raise ValueError(f"Invalid MySQL schema source: {name}")
    source = (DB / entry["file"]).read_text(encoding="utf-8")
    if hashlib.sha256(source.encode("utf-8")).hexdigest() != entry["sha256"]:
        raise ValueError(f"Structure does not match db/schema-manifest.json: {name}")
    if re.search(r"(?im)^\s*(?:INSERT\s+INTO|REPLACE\s+INTO|LOAD\s+DATA|DROP|TRUNCATE|CREATE\s+USER|GRANT)\b", source):
        raise ValueError(f"Unexpected data or destructive statement: {name}")
    prefix = re.search(
        rf"^SET NAMES utf8mb4;\n(CREATE DATABASE `{name}`[^\n]*;)\n"
        rf"USE `{name}`;\nSET FOREIGN_KEY_CHECKS=0;\n",
        source, re.M,
    )
    suffix = "SET FOREIGN_KEY_CHECKS=1;"
    if not prefix or not source.rstrip().endswith(suffix):
        raise ValueError(f"Unexpected MySQL export format: {name}")
    return prefix[1], source[prefix.end():].rstrip()[:-len(suffix)].strip(), entry["objects"]


def assemble():
    manifest = {entry["schema"]: entry for entry in json.loads(
        (DB / "schema-manifest.json").read_text(encoding="utf-8")
    )["schemas"]}
    sources = {name: load_schema(name, manifest) for name in dict.fromkeys(s[1] for s in SCHEMAS)}
    names = ", ".join(f"'{name}'" for name, *_ in SCHEMAS)
    parts = [f"""-- Data Elements 全工程 MySQL 8 新环境结构初始化（UTF-8）。
-- 只在这些库名均未使用的实例上执行。请用 mysql 批处理，不要使用 --force。
-- 此文件不带业务数据、账号、授权、Magic 脚本或低代码编译产物。
-- 来源和租户路由见仓库 docs/data-elements.md#mysql-new-environment。
SET NAMES utf8mb4;

-- 命中任一已有目标库时，利用 MySQL 的“数据库已存在”错误在建库前停止。
SET @data_elements_existing_db = (
  SELECT SCHEMA_NAME FROM information_schema.SCHEMATA
  WHERE SCHEMA_NAME IN ({names}) ORDER BY SCHEMA_NAME LIMIT 1
);
SET @data_elements_check_sql = IF(@data_elements_existing_db IS NULL, 'DO 0',
  CONCAT('CREATE DATABASE `', REPLACE(@data_elements_existing_db, '`', '``'), '`'));
PREPARE data_elements_check FROM @data_elements_check_sql;
EXECUTE data_elements_check;
DEALLOCATE PREPARE data_elements_check;

-- 先创建全部库，再安装表和视图。
"""]
    blocks = []
    for target, name, tenant, insecure_tls in SCHEMAS:
        create_db, body, objects = sources[name]
        parts.append(create_db.replace(f"`{name}`", f"`{target}`", 1) + "\n")
        if tenant:
            defaults = {value for _, value in TENANT_DEFAULT.findall(body)}
            if defaults != {POLICE_ID if name == "baseline_ga" else tenant}:
                raise ValueError(f"Unexpected tenant ID defaults: {name}")
            if name != target:
                body = TENANT_DEFAULT.sub(lambda match: f"{match[1]}'{tenant}'", body)
                if POLICE_ID in body or f"`{name}`." in body:
                    raise ValueError(f"Cross-tenant reference in MySQL structure: {target}")
                body = body.replace("所属公安租户", "所属行业租户").replace("公安租户本地登录用户账号", "行业租户本地登录用户账号")
        if insecure_tls is not None:
            body, count = INSECURE_TLS.subn(lambda match: f"{match[1]}'{insecure_tls}'", body)
            if count != 1:
                raise ValueError(f"Cannot locate NiFi TLS default: {target}")
        blocks.append(
            f"\n-- {target}：db/{name}/init.sql；{objects['tables']} 张表、{objects['views']} 个视图。\n"
            f"USE `{target}`;\n\n{body}\n"
        )

    parts.append("\nSET @data_elements_fk_checks = @@SESSION.FOREIGN_KEY_CHECKS;\nSET SESSION FOREIGN_KEY_CHECKS=0;\n")
    parts.extend(blocks)
    tables = sum(sources[name][2]["tables"] for _, name, *_ in SCHEMAS)
    views = sum(sources[name][2]["views"] for _, name, *_ in SCHEMAS)
    parts.append(f"""
SET SESSION FOREIGN_KEY_CHECKS=@data_elements_fk_checks;

-- 建库完成后核对对象数量；预期共 {tables} 张表、{views} 个视图。
SELECT TABLE_SCHEMA AS database_name,
       SUM(TABLE_TYPE = 'BASE TABLE') AS base_tables,
       SUM(TABLE_TYPE = 'VIEW') AS views
FROM information_schema.TABLES
WHERE TABLE_SCHEMA IN ({names})
GROUP BY TABLE_SCHEMA ORDER BY TABLE_SCHEMA;
""")
    return "".join(parts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    output = args.output.resolve()
    if output == DB or DB in output.parents:
        parser.error("Generated SQL must not overwrite the database version sources")
    sql = assemble()
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes(sql.encode("utf-8"))
    print(output)


if __name__ == "__main__":
    main()

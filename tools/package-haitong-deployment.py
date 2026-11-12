#!/usr/bin/env python3
"""Assemble the full Haitong apps and generate a separate minimal database bootstrap."""
import argparse
import hashlib
import json
from pathlib import Path
import re
from zipfile import ZIP_DEFLATED, ZIP_STORED, ZipFile, ZipInfo

from haitong_quality_example import EXAMPLES, build_quality_example
from haitong_dm_deployment import dameng_database_files, linux_runtime_files

ROOT = Path(__file__).resolve().parents[1]
TEMPLATES = Path(__file__).resolve().with_name("haitong-deployment")
BUILD = ROOT / "logs/haitong-database-lite"
TENANT = "8fb3fdba44d54a729098000000000001"
APP = "8fb3fdba44d54a729098000000000002"
ORG = "8fb3fdba44d54a729098000000000003"
ROLE = "8fb3fdba44d54a729098000000000004"
MENU = "8fb3fdba44d54a729098000000000005"
CONTROL_TABLES = {
    "api_backup_t", "api_file_t", "api_job_t", "log_archive_batch_t", "log_export_job_t", "sym_config_t", "sym_tenant_t",
    "ui_component_t", "ui_component_history_t",
}
TENANT_TABLES = {
    "rm_menu_t", "rm_org_t", "rm_role_menu_rela_t", "rm_role_t",
    "rm_user_org_rela_t", "rm_user_role_rela_t", "rm_user_t",
    "log_archive_batch_t", "log_audit_outbox_t", "log_export_job_t", "log_policy_t",
    "log_request_t", "log_system_t", "log_user_login_t",
    "sym_application_t", "sym_config_t", "sym_dict_t", "sym_dict_type_t",
    "nifi_node_t", "nifi_pipeline_t", "nifi_pipeline_external_ref_t",
    "data_access_agg_task_t", "data_access_field_mapping",
    "data_access_mapping_plan_t", "data_access_mapping_publication_t", "data_access_mapping_resource_t",
    "db_datasource_t", "db_table_t", "db_table_column_t", "da_catalog_t", "da_catalog_item_t",
    "data_gov_quality_rule", "push_node_t", "service_node_t",
}
MAGIC_IDS = {
    "portal_login_01", "ga_local_login_01", "bced293de53e4b348940f99b1b5bdf9f",
}
MAGIC_PATHS = {
    "api/01.系统门户/04.系统登出.ms",
    "function/13.统一身份管理/21.接收端账号安全校验.ms",
}


def literal(value):
    if isinstance(value, str):
        return "CONVERT(0x" + value.encode("utf-8").hex() + " USING utf8mb4)" if value else "''"
    return str(value) if value is not None else "NULL"


def ddl(canonical, database, requested):
    source = (ROOT / "db" / canonical / "init.sql").read_text(encoding="utf-8")
    tables = {name: statement for statement, name in re.findall(
        r"(?ms)^(CREATE TABLE `([^\x60]+)` \(.*?^\) ENGINE=[^\r\n]+;)", source
    )}
    selected = set(requested)
    while True:
        required = {ref for name in selected for ref in re.findall(r"REFERENCES `([^\x60]+)`", tables[name])}
        if required <= selected:
            break
        selected |= required
    header = (
        f"-- 海通独立环境初始化，仅供新空库。来源 db/{canonical}/init.sql。\n"
        f"CREATE DATABASE `{database}` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;\n"
        f"USE `{database}`;\nSET NAMES utf8mb4;\nSET FOREIGN_KEY_CHECKS=0;\n"
    )
    statements = []
    for name, statement in tables.items():
        if name in selected:
            if canonical != "baseline":
                statement = re.sub(r"(?m)^(`?\s*`tenant_id`[^\r\n]*?DEFAULT )'\d+'", rf"\g<1>'{TENANT}'", statement)
            statements.append(statement)
    return header + "\n\n".join(statements) + "\nSET FOREIGN_KEY_CHECKS=1;\n", sorted(selected)


def magic_rows():
    root = ROOT / "magic"
    index = json.loads((root / ".manifest.json").read_text(encoding="utf-8"))
    records = {entry["path"]: entry for entry in index["resources"]}
    paths = MAGIC_PATHS | {entry["path"] for entry in records.values() if entry.get("metadataId") in MAGIC_IDS}
    if len(paths) != len(MAGIC_PATHS) + len(MAGIC_IDS):
        raise ValueError("A required Magic resource is missing from the canonical index")
    for path in list(paths):
        for parent in Path(path).parents:
            directory = "" if parent == Path(".") else parent.as_posix()
            if directory in records:
                paths.add(directory)
            group = (parent / "group.json").as_posix()
            if group in records:
                paths.add(group)
    rows = []
    for path in sorted(paths):
        entry = records[path]
        is_dir = entry["kind"] == "directory"
        content = "this is directory" if is_dir else (root / path).read_text(encoding="utf-8")
        row_ids = entry.get("rowIds", [])
        row_id = next((value for value in row_ids if value), None)
        rows.append((row_id, entry['databasePath'], content))
    return rows, sorted(paths)


def seed_sql():
    control = ("\nINSERT INTO sym_tenant_t (tid,code,name,init_flag,json_config,sort_no,created_time,is_del,status,isolation_mode)"
               f" VALUES ('{TENANT}','HAITONG','海通数据集成中心',1,'{{\"accountSource\":\"LOCAL\"}}',1,NOW(6),0,1,'DATABASE');\n")
    tenant = f"""
INSERT INTO sym_application_t (tid,tenant_id,app_name,client_id,is_use,is_del,created_time)
VALUES ('{APP}','{TENANT}','海通数据集成中心','haitong',1,0,NOW(6));
INSERT INTO rm_org_t (id,tenant_id,name,parent_id,serial_number,org_path,nature,status,deleted,sort_num)
VALUES ('{ORG}','{TENANT}','海通数据集成中心','ROOT','1000','/{ORG}/','2',0,0,1);
INSERT INTO rm_role_t (id,tenant_id,name,code_num,app_id,data_scope,status,deleted)
VALUES ('{ROLE}','{TENANT}','海通管理员','admin','{APP}','ALL',0,0);
INSERT INTO rm_menu_t (id,tenant_id,name,parent_id,url,code,sno,resource_type,app_id,status,deleted,sort_num)
VALUES ('{MENU}','{TENANT}','NiFi 流程','ROOT','/development/nifi-flows','nifi:flows','nifi:flows',0,'{APP}',0,0,1);
INSERT INTO rm_role_menu_rela_t (id,tenant_id,role_id,app_resource_id)
VALUES ('8fb3fdba44d54a729098000000000006','{TENANT}','{ROLE}','{MENU}');
"""
    return control, tenant


def database_files():
    control, control_tables = ddl("baseline", "baseline", CONTROL_TABLES)
    tenant, tenant_tables = ddl("baseline_ga", "baseline_haitong", TENANT_TABLES)
    rows, magic_paths = magic_rows()
    control_seed, tenant_seed = seed_sql()
    resources = [f"({literal(row_id)},{literal(path)},{literal(content)},0)" for row_id, path, content in rows]
    control += control_seed + "INSERT INTO api_file_t (tid,file_path,file_content,is_del) VALUES\n" + ",\n".join(resources) + ";\n"
    tenant += tenant_seed
    manifest = {"controlDatabase": "baseline", "tenantDatabase": "baseline_haitong",
                "controlTables": control_tables, "tenantTables": tenant_tables, "magicResources": magic_paths}
    return {
        "db/01-control.sql": control.encode("utf-8"),
        "db/02-tenant.sql": tenant.encode("utf-8"),
        "db/scope.json": json.dumps(manifest, indent=2, ensure_ascii=False).encode("utf-8"),
        "db/init-account.py": (TEMPLATES / "init-account.py").read_bytes(),
    }


def pack(output, name, files):
    target = output / name
    with ZipFile(target, "w", ZIP_DEFLATED) as archive:
        for relative, source in sorted(files.items()):
            if isinstance(source, Path):
                if source.suffix == ".sh":
                    script = ZipInfo(relative)
                    script.create_system = 3
                    script.external_attr = 0o100755 << 16
                    archive.writestr(script, source.read_text(encoding="utf-8").encode("utf-8"), compress_type=ZIP_DEFLATED)
                else:
                    archive.write(source, relative, compress_type=ZIP_STORED if source.suffix in (".jar", ".gz") else ZIP_DEFLATED)
            else:
                archive.writestr(relative, source)
    with ZipFile(target) as archive:
        if archive.testzip() is not None:
            raise ValueError(f"Archive verification failed: {target}")
    print(target)
    return target


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=BUILD / "release")
    parser.add_argument("--jar", type=Path, default=BUILD / "backend/data-element.jar")
    parser.add_argument("--frontend", type=Path, default=BUILD / "frontend")
    parser.add_argument("--nacos", action="store_true", help="Use the standard backend and import configuration from Nacos")
    parser.add_argument("--dm8-runtime-dir", type=Path, help="Bundle DM8 SQL and verified Linux x64/ARM64 runtimes")
    layout = parser.add_mutually_exclusive_group()
    layout.add_argument("--database-only", action="store_true")
    layout.add_argument("--bundle", action="store_true", help="Package the complete deployment in a single ZIP")
    args = parser.parse_args()
    if args.dm8_runtime_dir and (not args.bundle or args.nacos):
        parser.error("--dm8-runtime-dir requires --bundle with Nacos disabled")
    args.output.mkdir(parents=True, exist_ok=True)
    database = (dameng_database_files(ROOT, CONTROL_TABLES, TENANT_TABLES, TENANT, magic_rows(), seed_sql())
                if args.dm8_runtime_dir else database_files())
    if args.database_only:
        pack(args.output, "haitong-database-lite.zip", database)
        return
    examples_build = BUILD / "example-build"
    examples_build.mkdir(parents=True, exist_ok=True)
    with ZipFile(args.jar) as jar:
        names = jar.namelist()
        if "BOOT-INF/classes/com/linewell/dataelement/DataElementApplication.class" not in names:
            raise ValueError("Expected full data-element application")
        libraries = [name for name in names if name.startswith("BOOT-INF/lib/")]
        if args.nacos:
            for dependency in ("spring-cloud-starter-alibaba-nacos-config", "nacos-client"):
                if not any(name.startswith(f"BOOT-INF/lib/{dependency}-") for name in libraries):
                    raise ValueError(f"The backend is missing the required Nacos dependency: {dependency}")
        elif any("nacos" in name.lower() for name in libraries):
            raise ValueError("The full backend still includes Nacos")
        jackson = []
        for name in names:
            if re.fullmatch(r"BOOT-INF/lib/jackson-(annotations|core|databind)-\d[^/]*\.jar", name):
                dependency = examples_build / Path(name).name
                dependency.write_bytes(jar.read(name))
                jackson.append(dependency)
        driver = next(name for name in names if re.fullmatch(r"BOOT-INF/lib/mariadb-java-client-\d[^/]*\.jar", name))
        mariadb = examples_build / Path(driver).name
        mariadb.write_bytes(jar.read(driver))
        if args.dm8_runtime_dir:
            dm_driver = next(name for name in names if re.fullmatch(r"BOOT-INF/lib/DmJdbcDriver18-\d[^/]*\.jar", name, re.I))
            dm_library = examples_build / Path(dm_driver).name
            dm_library.write_bytes(jar.read(dm_driver))
            sm3 = next(name for name in names if re.fullmatch(r"BOOT-INF/lib/bcprov-jdk(?:18on|15on|15to18)-\d[^/]*\.jar", name))
            sm3_library = examples_build / Path(sm3).name
            sm3_library.write_bytes(jar.read(sm3))
    quality = examples_build / "quality-check.jar"
    build_quality_example(sorted(jackson), quality, examples_build)
    runtime_config = TEMPLATES / "application.yml"
    backend = {
        "backend/data-element.jar": args.jar,
        "config/application.yml": TEMPLATES / "nacos-bootstrap.yml" if args.nacos else runtime_config,
        "start-backend.ps1": TEMPLATES / "start-backend.ps1",
        "start-backend.sh": TEMPLATES / "start-backend.sh",
        "部署说明.md": TEMPLATES / "deployment.md",
        "操作手册.md": TEMPLATES / "operations.md",
        "examples/QualityCheck.java": EXAMPLES / "QualityCheck.java",
        "examples/quality-check.jar": quality,
        "examples/example-mariadb.sql": EXAMPLES / "mariadb.sql",
        f"examples/drivers/{mariadb.name}": mariadb,
    }
    if args.nacos:
        backend["nacos/haitong-data-element.yml"] = runtime_config
    index = args.frontend / "index.html"
    if "/haitong/assets/" not in index.read_text(encoding="utf-8"):
        raise ValueError("Expected the full Haitong production build")
    frontend = {f"www/haitong/{file.relative_to(args.frontend).as_posix()}": file
                for file in args.frontend.rglob("*") if file.is_file()}
    library = ROOT / "data-elements-chengtian/node_modules/.pnpm/sm-crypto@0.3.14/node_modules/sm-crypto"
    frontend.update({
        "www/haitong/deployment.js": TEMPLATES / "deployment.js",
        "www/index.html": TEMPLATES / "login.html",
        "www/login.js": TEMPLATES / "login.js",
        "www/login.css": TEMPLATES / "login.css",
        "www/sm3.js": library / "dist/sm3.js",
        "licenses/sm-crypto.txt": library / "LICENCE_MIT",
        "config/nginx.conf": TEMPLATES / "nginx.conf",
        "logs/.gitkeep": b"",
        "temp/.gitkeep": b"",
    })
    configuration = "nacos" if args.nacos else "no-nacos"
    if args.dm8_runtime_dir:
        configuration = "dm8-no-nacos-kylin-v10"
        backend.update(linux_runtime_files(TEMPLATES, args.dm8_runtime_dir))
        backend[f"tools/lib/{dm_library.name}"] = dm_library
        backend[f"tools/lib/{sm3_library.name}"] = sm3_library
        for name in ("start-backend.ps1", "start-backend.sh"):
            del backend[name]
        del frontend["config/nginx.conf"]
    if args.bundle:
        packages = [pack(args.output, f"haitong-deployment-{configuration}.zip", database | backend | frontend)]
    else:
        packages = [
            pack(args.output, "haitong-database-lite.zip", database),
            pack(args.output, f"haitong-backend-{configuration}.zip", backend),
            pack(args.output, "haitong-frontend-full.zip", frontend),
        ]
    checksums = []
    for path in sorted(packages):
        with path.open("rb") as package:
            checksums.append(f"{hashlib.file_digest(package, 'sha256').hexdigest()}  {path.name}\n")
    (args.output / "SHA256SUMS.txt").write_text("".join(checksums), encoding="utf-8")


if __name__ == "__main__":
    main()

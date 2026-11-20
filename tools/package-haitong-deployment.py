#!/usr/bin/env python3
"""Assemble the full Haitong apps and generate a separate minimal database bootstrap."""
import argparse
import hashlib
from io import BytesIO
import json
from pathlib import Path
import re
import shutil
import tarfile
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
    "portal_login_01", "bced293de53e4b348940f99b1b5bdf9f",
}
MAGIC_PATHS = {
    "api/01.系统门户/02.登录与会话/02.系统登出.ms",
    "function/13.统一身份管理/21.接收端账号安全校验.ms",
}
STANDALONE_API_GROUPS = (
    "api/03.数据接入/",
    "api/09.海通流程接口/",
)
STANDALONE_API_ROUTES = {
    "post:/dst/db/hive", "get:/sym/dict/query", "post:/sym/user/me", "get:/portal/sso/session",
    "post:/dst/db/srcpage", "post:/dst/db/srcdetail", "post:/dst/db/ddl", "post:/dst/db/create",
    "post:/dst/db/dept", "get:/dst/db/tables", "get:/dst/db/fields",
    "post:/dst/file/register", "post:/dst/file/manage", "post:/dst/file/submit",
}
# These two functions are called by the Java NiFi lifecycle synchronizer.
STANDALONE_NIFI_CALLBACKS = {"/common/ensureNifiCatalogAccessTask", "/common/nifiTaskState"}
NIFI_RELEASE = "2.9.0"
REQUIRED_NIFI_EXTENSIONS = {
    "haitong-mapping-nar": "com.linewell.dataelement",
    "datadevs-database-nar": "com.linewell.microservice",
}
LINUX_ARCHITECTURES = {"x86_64": 62, "aarch64": 183}


def validate_linux_assets(assets, architecture, redis_loader):
    """Inspect the binaries that the standalone launcher will actually use."""
    required = {
        "java21.tar.gz": ("bin/java", "lib/server/libjvm.so"),
        "mysql.tar.xz": ("bin/mysql", "bin/mysqld", "bin/mysqladmin"),
        "redis.tar.gz": (redis_loader, "usr/bin/valkey-server", "usr/lib/libssl.so.3", "usr/lib/libcrypto.so.3"),
        "caddy.tar.gz": ("caddy",),
        "mysql-libs.tar.gz": ("libaio.so.1", "libnuma.so.1", "libncurses.so.6", "libtinfo.so.6"),
    }
    for archive, files in required.items():
        pending = set(files)
        strip_root = archive in {"java21.tar.gz", "mysql.tar.xz"}
        with tarfile.open(assets[archive]) as source:
            for member in source:
                name = member.name.removeprefix("./")
                if strip_root:
                    name = name.partition("/")[2]
                if name not in pending:
                    continue
                with source.extractfile(member) as stream:
                    header = stream.read(20)
                if header[:6] != b"\x7fELF\x02\x01" or int.from_bytes(header[18:20], "little") != LINUX_ARCHITECTURES[architecture]:
                    raise ValueError(f"Expected a Linux {architecture} binary: {archive}/{member.name}")
                pending.remove(name)
                if not pending:
                    break
        if pending:
            raise ValueError(f"Missing Linux runtime files in {archive}: {', '.join(sorted(pending))}")


def nar_metadata(archive):
    text = archive.read("META-INF/MANIFEST.MF").decode("utf-8")
    return dict(line.split(": ", 1) for line in re.sub(r"\r?\n ", "", text).splitlines() if ": " in line)


def bundle_nifi(base_archive, extensions, mapping_version, destination):
    """Assemble NiFi with both project extensions, preserving the stock archive entries."""
    selected = {}
    for extension in extensions:
        with ZipFile(extension) as nar:
            meta = nar_metadata(nar)
            if nar.testzip() is not None:
                raise ValueError(f"Corrupt NiFi extension: {extension.name}")
        artifact = meta["Nar-Id"]
        version = meta["Nar-Version"]
        parent = (meta.get("Nar-Dependency-Group"), meta.get("Nar-Dependency-Id"), meta.get("Nar-Dependency-Version"))
        if (artifact not in REQUIRED_NIFI_EXTENSIONS or meta["Nar-Group"] != REQUIRED_NIFI_EXTENSIONS[artifact]
                or parent != ("org.apache.nifi", "nifi-standard-services-api-nar", NIFI_RELEASE)
                or extension.name != f"{artifact}-{version}.nar"
                or (artifact == "haitong-mapping-nar" and version != mapping_version)
                or (artifact == "datadevs-database-nar" and version != NIFI_RELEASE)):
            raise ValueError(f"Unsupported NiFi {NIFI_RELEASE} extension: {extension.name}")
        if artifact in selected:
            raise ValueError(f"Multiple versions of NiFi extension supplied: {artifact}")
        selected[artifact] = extension
    if missing := REQUIRED_NIFI_EXTENSIONS.keys() - selected.keys():
        raise ValueError("Missing required NiFi extensions: " + ", ".join(sorted(missing)))

    prefix = f"nifi-{NIFI_RELEASE}/"
    parent_path = prefix + f"lib/nifi-standard-services-api-nar-{NIFI_RELEASE}.nar"
    with ZipFile(base_archive) as archive:
        entries = archive.namelist()
        if (len(entries) != len(set(entries)) or not all(name.startswith(prefix) for name in entries)
                or prefix + "conf/nifi.properties" not in entries or parent_path not in entries):
            raise ValueError(f"Expected a complete stock NiFi {NIFI_RELEASE} ZIP")
        with ZipFile(BytesIO(archive.read(parent_path))) as nar:
            meta = nar_metadata(nar)
            if (meta["Nar-Group"], meta["Nar-Id"], meta["Nar-Version"]) != (
                    "org.apache.nifi", "nifi-standard-services-api-nar", NIFI_RELEASE):
                raise ValueError("NiFi ZIP contains a mismatched standard services API")
        for name in entries:
            if name.endswith(".nar") and any(Path(name).name.startswith(artifact + "-") for artifact in selected):
                raise ValueError(f"Stock archive already contains a project extension: {name}")

    shutil.copyfile(base_archive, destination)
    with ZipFile(destination, "a") as archive:
        for extension in selected.values():
            archive.write(extension, prefix + "extensions/" + extension.name, compress_type=ZIP_STORED)
    return destination


def literal(value):
    if isinstance(value, str):
        return "CONVERT(0x" + value.encode("utf-8").hex() + " USING utf8mb4)" if value else "''"
    return str(value) if value is not None else "NULL"


def ddl(canonical, database, requested):
    source = (ROOT / "db" / canonical / "init.sql").read_text(encoding="utf-8")
    tables = {name: statement for statement, name in re.findall(
        r"(?ms)^(CREATE TABLE `([^\x60]+)` \(.*?^\) ENGINE=[^\r\n]+;)", source
    )}
    selected = set(tables if requested is None else requested)
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


def magic_rows(standalone=False):
    root = ROOT / "magic"
    index = json.loads((root / ".manifest.json").read_text(encoding="utf-8"))
    records = {entry["path"]: entry for entry in index["resources"]}
    paths = MAGIC_PATHS | {entry["path"] for entry in records.values() if entry.get("metadataId") in MAGIC_IDS}
    if len(paths) != len(MAGIC_PATHS) + len(MAGIC_IDS):
        raise ValueError("A required Magic resource is missing from the canonical index")
    if standalone:
        routes = {}
        for path in records:
            if path.startswith(("function/", "api/")) and path.endswith(".ms"):
                parent = Path(path).parent
                groups = [group for group in (*reversed(parent.parents), parent) if len(group.parts) > 1]
                metadata = json.loads((root / path).read_text(encoding="utf-8").split("================================")[0])
                route = "".join(json.loads((root / group / "group.json").read_text(encoding="utf-8").split("================================")[0])["path"]
                                for group in groups) + metadata["path"]
                if path.startswith("api/"):
                    route = metadata["method"].lower() + ":" + route
                    if path.startswith(STANDALONE_API_GROUPS) or route in STANDALONE_API_ROUTES:
                        paths.add(path)
                routes[route] = path
        paths.update(routes[route] for route in STANDALONE_NIFI_CALLBACKS)
        pending = list(paths)
        while pending:
            source = (root / pending.pop()).read_text(encoding="utf-8")
            for route in re.findall(r"""(?m)^\s*import\s+['"]@((?:\w+:)?/[^'"]+)['"]""", source):
                dependency = routes.get(route)
                if dependency is None:
                    raise ValueError(f"Missing Magic dependency: {route}")
                if dependency not in paths:
                    paths.add(dependency)
                    pending.append(dependency)
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
        if entry.get("publicationBlocked"):
            raise ValueError(f"Cannot distribute blocked Magic resource: {path}")
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


def database_files(standalone=False):
    control, control_tables = ddl("baseline", "baseline", None if standalone else CONTROL_TABLES)
    tenant, tenant_tables = ddl("baseline_ga", "baseline_haitong", None if standalone else TENANT_TABLES)
    rows, magic_paths = magic_rows(standalone)
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
            if relative.endswith(".sh"):
                script = ZipInfo(relative)
                script.create_system = 3
                script.external_attr = 0o100755 << 16
                contents = source.read_text(encoding="utf-8").encode("utf-8") if isinstance(source, Path) else source
                archive.writestr(script, contents, compress_type=ZIP_DEFLATED)
            elif isinstance(source, Path):
                archive.write(source, relative, compress_type=ZIP_STORED if source.suffix in (".jar", ".gz", ".zip", ".xz") else ZIP_DEFLATED)
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
    parser.add_argument("--linux-runtime-dir", type=Path, help="Bundle Linux MySQL, Redis, NiFi, Caddy and Java 21")
    parser.add_argument("--linux-arch", choices=tuple(LINUX_ARCHITECTURES),
                        help="Architecture of the supplied Linux runtime files (default: x86_64)")
    parser.add_argument("--nifi-extension", type=Path, action="append", default=[],
                        help="Version-matched project NAR to embed in the Linux NiFi distribution (repeat for each NAR)")
    layout = parser.add_mutually_exclusive_group()
    layout.add_argument("--database-only", action="store_true")
    layout.add_argument("--bundle", action="store_true", help="Package the complete deployment in a single ZIP")
    args = parser.parse_args()
    if args.dm8_runtime_dir and (not args.bundle or args.nacos):
        parser.error("--dm8-runtime-dir requires --bundle with Nacos disabled")
    if args.linux_runtime_dir and (not args.bundle or args.nacos or args.dm8_runtime_dir):
        parser.error("--linux-runtime-dir requires the no-Nacos MySQL bundle")
    if args.linux_arch and not args.linux_runtime_dir:
        parser.error("--linux-arch requires the Linux runtime bundle")
    if args.nifi_extension and not args.linux_runtime_dir:
        parser.error("--nifi-extension requires the Linux runtime bundle")
    if args.linux_runtime_dir and len(args.nifi_extension) != len(REQUIRED_NIFI_EXTENSIONS):
        parser.error("The Linux runtime bundle requires both project NiFi NARs (--nifi-extension)")
    args.output.mkdir(parents=True, exist_ok=True)
    database = (dameng_database_files(ROOT, CONTROL_TABLES, TENANT_TABLES, TENANT, magic_rows(), seed_sql())
                if args.dm8_runtime_dir else database_files(bool(args.linux_runtime_dir)))
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
        if args.linux_runtime_dir:
            mapping_class = jar.read("BOOT-INF/classes/com/linewell/dataelement/mapping/core/MappingEngine.class")
            versions = set(re.findall(rb"\x01\x00[\x05-\x0c](\d+\.\d+\.\d+)", mapping_class))
            if len(versions) != 1:
                raise ValueError("Cannot determine the field mapping engine version from the backend JAR")
            mapping_version = versions.pop().decode("ascii")
            driver = next(name for name in names if re.fullmatch(r"BOOT-INF/lib/mysql-connector-j-\d[^/]*\.jar", name))
            mysql_driver = examples_build / Path(driver).name
            mysql_driver.write_bytes(jar.read(driver))
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
    if args.linux_runtime_dir:
        architecture = args.linux_arch or "x86_64"
        configuration = f"linux-{architecture}-no-nacos"
        linux = TEMPLATES / "linux-standalone"
        redis_loader = f"lib/ld-musl-{architecture}.so.1"
        assets = {name: args.linux_runtime_dir / name for name in (
            "java21.tar.gz", "mysql.tar.xz", "redis.tar.gz", "nifi.zip", "caddy.tar.gz", "mysql-libs.tar.gz")}
        for name, file in assets.items():
            if not file.is_file():
                raise ValueError(f"Missing verified Linux runtime asset: {name}")
        validate_linux_assets(assets, architecture, redis_loader)
        backend.update({f"vendor/{name}": file for name, file in assets.items()})
        backend["vendor/nifi.zip"] = bundle_nifi(backend["vendor/nifi.zip"], args.nifi_extension, mapping_version,
                                              examples_build / "nifi-with-haitong-extensions.zip")
        backend.update({f"config/{name}": linux / name for name in ("ports.json", "Caddyfile")})
        backend["config/runtime.json"] = json.dumps({
            "architecture": architecture, "redisLoader": redis_loader,
        }, indent=2).encode("ascii")
        config = runtime_config.read_text(encoding="utf-8")
        for setting in ("monitor-enabled", "provenance-sync-enabled"):
            config = config.replace(f"{setting}: false", f"{setting}: true")
        backend["config/application.yml"] = config.encode("utf-8")
        backend["tools/standalone.py"] = linux / "standalone.py"
        architecture_label = "aarch64（ARM64）" if architecture == "aarch64" else architecture
        backend["部署说明.md"] = (linux / "deployment.md").read_text(encoding="utf-8").replace(
            "@@LINUX_ARCH@@", architecture_label).encode("utf-8")
        frontend["www/haitong/deployment.js"] = (
            f"window.haitongDeployment = {{ tenantId: '{TENANT}', appId: '{APP}', nifiUiUrl: '' }};\n"
        ).encode()
        for command in ("start", "stop", "status", "init-db"):
            backend[f"{command}.sh"] = (
                f'#!/usr/bin/env bash\nset -euo pipefail\ncd -- "$(dirname -- "${{BASH_SOURCE[0]}}")"\n'
                f'exec python3 tools/standalone.py {command} "$@"\n'
            ).encode()
        backend[f"drivers/{mysql_driver.name}"] = mysql_driver
        backend[f"drivers/{mariadb.name}"] = mariadb
        del backend["start-backend.ps1"]
        del backend["start-backend.sh"]
        del frontend["config/nginx.conf"]
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

#!/usr/bin/env python3
"""Check/apply/rollback a Haitong standalone update, preserving site state."""
import argparse
import fcntl
import hashlib
import importlib.util
import json
import os
from pathlib import Path, PurePosixPath
import platform
import re
import shutil
import subprocess
import sys
import time
from zipfile import ZipFile

BUNDLE = Path(__file__).resolve().parent


def checksum(path):
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1048576), b""):
            value.update(block)
    return value.hexdigest()


def inside(root, relative):
    name = PurePosixPath(relative)
    if name.is_absolute() or ".." in name.parts or "\\" in relative:
        raise ValueError("Unsafe update path")
    result = root.joinpath(*name.parts).resolve()
    if root.resolve() not in result.parents:
        raise ValueError("Update path escapes its directory")
    return result


def literal(value):
    return "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4) COLLATE utf8mb4_general_ci" if value else "''"


def load_launcher(home):
    spec = importlib.util.spec_from_file_location("haitong_site_launcher", home / "tools/standalone.py")
    module = importlib.util.module_from_spec(spec)
    previous = sys.dont_write_bytecode
    sys.dont_write_bytecode = True
    try:
        spec.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = previous
    return module


class Database:
    def __init__(self, home, defaults, error_file=None):
        self.home, self.defaults, self.error_file = home, defaults, error_file
        self.env = dict(os.environ, LD_LIBRARY_PATH=str(home / "runtime/mysql-libs"))

    def run(self, database, script):
        command = [str(self.home / "runtime/mysql/bin/mysql"), "--defaults-extra-file=" + str(self.defaults),
                   "--default-character-set=utf8mb4", "--batch", "--raw", "--skip-column-names", database]
        result = subprocess.run(command, input=script.encode("utf-8"), stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, env=self.env, timeout=300)
        if result.returncode:
            if self.error_file:
                self.error_file.write_bytes(result.stderr)
                self.error_file.chmod(0o600)
            raise RuntimeError("数据库执行失败；没有输出可能包含运行数据的 SQL。请检查连接、结构或受限备份中的 database-error.txt")
        return result.stdout.decode("utf-8").splitlines()

    def dump(self, database, table, destination, where=None):
        # Two bounded reads, with the selection sent through stdin instead of a long command argument.
        if not re.fullmatch(r"[A-Za-z0-9_]+", table):
            raise ValueError("Invalid backup table")
        columns = [row.split("\t", 1)[0] for row in self.run(database, "SHOW COLUMNS FROM `" + table + "`;")]
        if not columns or any(not re.fullmatch(r"[A-Za-z0-9_]+", name) for name in columns):
            raise ValueError("Invalid backup columns")
        expressions = ["IF(`" + name + "` IS NULL,'N',CONCAT('H',HEX(CAST(`" + name + "` AS BINARY))))" for name in columns]
        rows = self.run(database, "SELECT " + ",".join(expressions) + " FROM `" + table + "`" + (" WHERE " + where if where else "") + " LIMIT 2001;")
        if len(rows) > 2000:
            raise ValueError("所选备份资源超过2000条，已停止")
        statements = ["SET NAMES utf8mb4;\n"]
        if rows:
            records = []
            for row in rows:
                values = row.split("\t")
                if len(values) != len(columns) or any(value != "N" and not re.fullmatch(r"H[a-fA-F0-9]*", value) for value in values):
                    raise ValueError("资源备份编码异常，未开始升级")
                records.append("(" + ",".join("NULL" if value == "N" else "''" if value == "H" else "CONVERT(0x" + value[1:] + " USING utf8mb4) COLLATE utf8mb4_general_ci" for value in values) + ")")
            statements.append("INSERT INTO `" + table + "` (" + ",".join("`" + name + "`" for name in columns) + ") VALUES\n" + ",\n".join(records) + ";\n")
        destination.write_bytes("".join(statements).encode("utf-8"))
        destination.chmod(0o600)


def legacy_paths(plans):
    paths = {}
    for item in plans:
        if item.get("legacyNullPath"):
            for path in (item["legacyNullPath"], item["path"]):
                if path in paths and paths[path] != item["id"]:
                    raise ValueError("原包空行ID的路径对应不唯一，已停止")
                paths[path] = item["id"]
    return paths


def resource_selection(plans):
    ids = ",".join(literal(item["id"]) for item in plans)
    paths = legacy_paths(plans)
    return "(tid IN (" + ids + ")" + (" OR (tid IS NULL AND file_path IN (" + ",".join(literal(path) for path in paths) + "))" if paths else "") + ")"


def resources(db, plans):
    paths = legacy_paths(plans)
    rows = db.run("baseline", "SELECT tid,SHA2(file_content,256),is_del,HEX(file_path) FROM api_file_t WHERE " + resource_selection(plans) + ";")
    current = {}
    for row in rows:
        tid, value, deleted, encoded = row.split("\t")
        key = paths[bytes.fromhex(encoded).decode("utf-8")] if tid == "NULL" else tid
        if key in current:
            raise RuntimeError("生产 Magic 存在重复资源行标识或空ID路径；未覆盖或自动删除")
        current[key] = (value, deleted)
    if len(current) != len(rows):
        raise RuntimeError("生产 Magic 存在重复资源行标识；未覆盖或自动删除")
    conflicts = []
    for item in plans:
        actual = current.get(item["id"])
        if actual and actual[0] not in (item["before"], item["after"]):
            conflicts.append(item["id"])
        if actual and item["action"] == "upsert" and actual[1] != "0":
            conflicts.append(item["id"])
        if actual is None and item["before"] and item["action"] == "upsert":
            conflicts.append(item["id"])
    if conflicts:
        raise RuntimeError("生产 Magic 与原包及本次版本都不同；已停止以保留现场修改。资源 ID: " + ",".join(conflicts[:12]))
    known = set(item["id"] for item in plans)
    wanted_paths = {item["path"] for item in plans if item["action"] == "upsert"}
    retired_groups = {item["metadataId"] for item in plans if item["action"] == "retire" and item.get("kind") == "group" and item.get("metadataId")}
    retired_directories = [item["path"] for item in plans if item["action"] == "retire" and item.get("kind") == "directory"]
    retired_symbols = [item["retiredSymbol"] for item in plans if item.get("retiredSymbol")]
    all_rows = db.run("baseline", "SELECT tid,HEX(file_path),HEX(file_content) FROM api_file_t WHERE is_del=0 LIMIT 2001;")
    if len(all_rows) > 2000:
        raise RuntimeError("生产控制库活动资源超过2000条，需要核对是否仍为原独立实例")
    for row in all_rows:
        tid, encoded, content_hex = row.split("\t")
        path = bytes.fromhex(encoded).decode("utf-8")
        if tid == "NULL" and path in paths:
            continue
        if tid not in known and path in wanted_paths:
            raise RuntimeError("生产存在本次路径的另一份 Magic 资源；已停止，未覆盖: " + tid)
        if tid not in known:
            content = "" if content_hex == "NULL" else bytes.fromhex(content_hex).decode("utf-8")
            try:
                metadata = json.loads(content.split("================================", 1)[0])
            except (ValueError, AttributeError):
                metadata = {}
            if metadata.get("groupId") in retired_groups or metadata.get("parentId") in retired_groups or any(path.startswith(prefix) for prefix in retired_directories) or any(symbol in content for symbol in retired_symbols):
                raise RuntimeError("现场自定义资源依赖将退役的旧资源，请先合并: " + tid)
    return current


def check_structure(db, plan, post=False):
    for scope, database in (("control", "baseline"), ("tenant", "baseline_haitong")):
        expected = plan[scope]
        all_columns = db.run(database, "SELECT table_name,column_name,column_type FROM information_schema.columns WHERE table_schema=DATABASE();")
        actual = {(x[0], x[1]): x[2].lower() for row in all_columns if len(x := row.split("\t")) == 3}
        existing_tables = {table for table, column in actual}
        indexes = {}
        for row in db.run(database, "SELECT table_name,index_name,column_name,non_unique FROM information_schema.statistics WHERE table_schema=DATABASE() ORDER BY table_name,index_name,seq_in_index;"):
            table, name, column, nonunique = row.split("\t")
            indexes.setdefault((table, name), []).append((column, nonunique))
        for item in expected:
            table = item["table"]
            if item["kind"] == "table":
                if post and table not in existing_tables:
                    raise RuntimeError("新增表未创建: " + table)
                if table in existing_tables:
                    for column, definition in item["columns"].items():
                        if (table, column) not in actual or actual[(table, column)] != definition.split(" ", 1)[0].lower():
                            raise RuntimeError("生产已存在的新增表结构不同: " + table + "." + column)
            elif table not in existing_tables:
                raise RuntimeError("原包应有的表缺失: " + table)
            elif item["kind"] in ("column", "widen"):
                column = item["column"]
                value = actual.get((table, column))
                permitted = {item["after"].split(" ", 1)[0].lower()}
                if item.get("before") and not post:
                    permitted.add(item["before"].split(" ", 1)[0].lower())
                if post and value is None:
                    raise RuntimeError("新增列未创建: " + table + "." + column)
                if value is not None and value not in permitted:
                    raise RuntimeError("生产字段类型不属于已核对版本: " + table + "." + column)
            elif item["kind"] in ("index", "indexretire"):
                value = indexes.get((table, item["name"]))
                definition = item["definition"]
                columns = re.findall(r"`([^`]+)`", definition)[1:]
                wanted = [(column, "0" if definition.startswith("UNIQUE") else "1") for column in columns]
                if value is not None and value != wanted:
                    raise RuntimeError("生产同名索引定义不同: " + table + "." + item["name"])
                if post and ((item["kind"] == "index" and value is None) or (item["kind"] == "indexretire" and value is not None)):
                    raise RuntimeError("索引升级未完成: " + table + "." + item["name"])


def verify_resources(db, plans):
    current = resources(db, plans)
    for item in plans:
        wanted = (item["after"], "0") if item["action"] == "upsert" else None
        if wanted and current.get(item["id"]) != wanted:
            raise RuntimeError("Magic 更新后摘要不一致: " + item["id"])
        if not wanted and current.get(item["id"], (None, "1"))[1] != "1":
            raise RuntimeError("旧 Magic 资源未退役: " + item["id"])


def codec_plan(launcher, url):
    root = launcher.http_json(url + "/flow/process-groups/root")["processGroupFlow"]["id"]
    existing = launcher.http_json(url + "/flow/process-groups/" + root + "/controller-services")["controllerServices"]
    matches = [row for row in existing if row["component"]["name"] == "PUBLIC-WRITER-JSON-TIMESTAMP9"]
    if len(matches) > 1:
        raise RuntimeError("NiFi 根服务有多个 PUBLIC-WRITER-JSON-TIMESTAMP9；未更改既有服务")
    properties = {"Schema Access Strategy": "inherit-record-schema", "Timestamp Format": "yyyy-MM-dd HH:mm:ss.SSSSSSSSS",
                  "Date Format": "yyyy-MM-dd", "Time Format": "HH:mm:ss", "Schema Write Strategy": "full-schema-attribute"}
    if matches:
        row = matches[0]
        if row["component"]["type"] != "org.apache.nifi.json.JsonRecordSetWriter" or any(
                row["component"].get("properties", {}).get(key) != properties[key]
                for key in ("Schema Access Strategy", "Timestamp Format")):
            raise RuntimeError("已有 TIMESTAMP9 服务配置不同，未修改公共服务")
    return root, matches[0] if matches else None, properties


def provision_codecs(launcher, url):
    root, row, properties = codec_plan(launcher, url)
    if row is None:
        kind = "org.apache.nifi.json.JsonRecordSetWriter"
        types = launcher.http_json(url + "/flow/controller-service-types")["controllerServiceTypes"]
        bundle = next(item["bundle"] for item in types if item["type"] == kind)
        row = launcher.http_json(url + "/process-groups/" + root + "/controller-services", {
            "revision": {"version": 0}, "component": {"name": "PUBLIC-WRITER-JSON-TIMESTAMP9", "type": kind,
                                                         "bundle": bundle, "properties": properties}})
    resource = url + "/controller-services/" + row["id"]
    if row["component"]["state"] == "DISABLED":
        launcher.http_json(resource + "/run-status", {"revision": row["revision"], "state": "ENABLED"}, "PUT")
    launcher.wait_for("nifi", lambda: launcher.http_json(resource)["component"]["state"] == "ENABLED", 90)


def node_drivers(db, home, manifest, jdbc, ports):
    # Change missing driver keys only on this package's own local NiFi node.
    rows = db.run("baseline_haitong", "SELECT tid,HEX(jdbc_driver_locations) FROM nifi_node_t WHERE tenant_id=" +
                  literal(manifest["tenantId"]) + " AND base_url=" + literal("http://127.0.0.1:" + str(ports["nifi"])) + " AND enabled=1;")
    base = home / "drivers" / manifest["releaseId"]
    for row in rows:
        tid, encoded = row.split("\t")
        text = "" if encoded == "NULL" else bytes.fromhex(encoded).decode("utf-8")
        lines = text.splitlines()
        compatibility = {"HIVE_OPEN_SOURCE": "haitong-hive-jdbc-compat-1.0.0.jar", "ORACLE_TYPED": "haitong-oracle-jdbc-compat-1.0.4.jar"}
        for position, line in enumerate(lines):
            if "=" not in line:
                continue
            key, value = line.split("=", 1)
            asset = compatibility.get(key.strip().upper())
            if asset and asset not in value:
                relative = next(path for path in jdbc["profiles"][key.strip().upper()]["files"] if path.endswith("/" + asset))
                lines[position] = line + ("," if value.strip() else "") + str(inside(base, relative))
        present = {line.split("=", 1)[0].strip().upper() for line in lines if "=" in line}
        for key, profile in sorted(jdbc["profiles"].items()):
            if key not in present:
                lines.append(key + "=" + ",".join(str(inside(base, item)) for item in profile["files"]))
        db.run("baseline_haitong", "UPDATE nifi_node_t SET jdbc_driver_locations=" + literal("\n".join(lines)) +
               " WHERE tid=" + literal(tid) + " AND tenant_id=" + literal(manifest["tenantId"]) + ";")


def start_application(launcher, ports, credentials):
    home = launcher.ROOT
    environment = dict(os.environ, HAITONG_BACKEND_PORT=str(ports["backend"]), HAITONG_REDIS_PORT=str(ports["redis"]),
                       HAITONG_REDIS_PASSWORD=credentials["redis"], HAITONG_DB_PASSWORD=credentials["mysql"],
                       HAITONG_DB_USER="haitong", HAITONG_CONTROL_JDBC_URL=launcher.mysql_tcp_url(ports, "baseline"),
                       HAITONG_TENANT_JDBC_URL=launcher.mysql_tcp_url(ports, "baseline_haitong"),
                       IDAAS_TRANSPORT_KEY=credentials["transport"], IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP="true")
    launcher.spawn("backend", [str(home / "runtime/java/bin/java"), "-Xms256m", "-Xmx1g", "-Dfile.encoding=UTF-8", "-jar",
                                "backend/data-element.jar", "--spring.config.location=file:./config/application.yml"], env=environment)
    launcher.wait_for("backend", lambda: launcher.http_json("http://127.0.0.1:" + str(ports["backend"]) + "/actuator/health")["status"] == "UP", 240)
    launcher.spawn("web", [str(home / "runtime/caddy/caddy"), "run", "--config", "config/Caddyfile", "--adapter", "caddyfile"],
                   env=dict(os.environ, HAITONG_HTTP_PORT=str(ports["web"]), HAITONG_HTTP_BIND=ports["webBind"], HAITONG_BACKEND_PORT=str(ports["backend"])))
    web_host = "127.0.0.1" if ports["webBind"] in ("0.0.0.0", "::") else ports["webBind"]
    launcher.wait_for("web", lambda: launcher.http_json("http://" + web_host + ":" + str(ports["web"]) + "/prod-api/actuator/health")["status"] == "UP", 90)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("check", "apply", "rollback"))
    parser.add_argument("home", type=Path)
    parser.add_argument("--mysql-defaults", type=Path, help="Existing restricted MySQL client configuration")
    args = parser.parse_args()
    os.umask(0o077)
    manifest = json.loads((BUNDLE / "manifest.json").read_text(encoding="utf-8"))
    home = args.home.resolve(strict=True)
    for relative, expected in manifest["files"].items():
        if checksum(inside(BUNDLE, relative)) != expected:
            raise ValueError("更新包文件损坏: " + relative)
    if platform.system() != "Linux" or platform.machine().lower() not in ("aarch64", "arm64"):
        raise ValueError("本更新仅用于原 Linux ARM/aarch64 独立包")
    if json.loads((home / "config/runtime.json").read_text())["architecture"] != "aarch64":
        raise ValueError("目标目录不是原 ARM 安装目录")
    launcher = load_launcher(home)
    backup = home / "data/local/upgrades" / manifest["releaseId"]
    defaults = (args.mysql_defaults or home / "data/local/mysql-root.cnf").resolve(strict=True)
    db = Database(home, defaults)
    ports = json.loads((home / "config/ports.json").read_text())
    credentials_file = home / "data/local/credentials.json"
    if not credentials_file.is_file():
        raise ValueError("缺少原实例凭据；更新不会生成新账号或密钥")
    if hasattr(os, "geteuid") and credentials_file.stat().st_uid != os.geteuid():
        raise ValueError("请使用创建原实例凭据的运行账号执行更新，避免改变服务与数据文件的属主")
    os.chdir(home)
    with (home / "data/local/launcher.lock").open("r+") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX if args.action != "check" else fcntl.LOCK_SH)
        plans = json.loads((BUNDLE / "db/resource-plan.json").read_text(encoding="utf-8"))
        if args.action == "rollback":
            state = json.loads((backup / "state.json").read_text())
            resources(db, plans)
            launcher.stop_service("web"); launcher.stop_service("backend")
            resources(db, plans)
            db.run("baseline", (backup / "rollback-resources.sql").read_text(encoding="utf-8"))
            if (backup / "rollback-node-drivers.sql").is_file():
                db.run("baseline_haitong", (backup / "rollback-node-drivers.sql").read_text(encoding="utf-8"))
            for relative in state["backedUpFiles"]:
                target = inside(home, relative)
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(inside(backup / "files", relative), target)
            start_application(launcher, ports, json.loads(credentials_file.read_text()))
            (backup / "rolled-back.json").write_text(json.dumps({"time": int(time.time())}), encoding="utf-8")
            if (backup / "completed.json").is_file():
                (backup / "completed.json").unlink()
            print("已恢复原前后端和所选 Magic；新增结构、模板、驱动和扩展保留，未清理流程或队列")
            return
        if checksum(home / "backend/data-element.jar") not in (manifest["baselineBackendSha256"], manifest["backendSha256"]):
            raise ValueError("生产后端不同于原包和本次版本；已停止，未覆盖现场版本")
        if not all(launcher.service_pid(name) for name in ("mysql", "redis", "nifi")):
            raise ValueError("请先按原启动方式运行本实例 MySQL、Redis、NiFi；本脚本不初始化或重启这些服务")
        structure = json.loads((BUNDLE / "db/structure-plan.json").read_text(encoding="utf-8"))
        tenants = db.run("baseline", "SELECT tid FROM sym_tenant_t WHERE is_del=0;")
        if tenants != [manifest["tenantId"]]:
            raise ValueError("目标控制库不是原包单一海通租户；未修改共享或其他行业环境")
        check_structure(db, structure)
        current = resources(db, plans)
        properties = (home / "runtime/nifi-2.9.0/conf/nifi.properties").read_text()
        settings = {line.split("=", 1)[0].strip(): line.split("=", 1)[1].strip() for line in properties.splitlines() if "=" in line and not line.lstrip().startswith("#")}
        autoload = settings.get("nifi.nar.library.autoload.directory", "./extensions")
        autoload_path = (home / "runtime/nifi-2.9.0" / autoload).resolve()
        if autoload_path != (home / "runtime/nifi-2.9.0/extensions").resolve():
            raise ValueError("NiFi 自动扩展目录不同于原包 extensions；请核对现场配置，未修改")
        nifi_url = "http://127.0.0.1:" + str(ports["nifi"]) + "/nifi-api"
        if launcher.http_json(nifi_url + "/flow/about").get("about", {}).get("version") != "2.9.0":
            raise ValueError("现场 NiFi 实际版本不是2.9.0，未安装其他 SDK 的扩展")
        codec_plan(launcher, nifi_url)
        for item in manifest["extensions"]:
            destination = home / "runtime/nifi-2.9.0/extensions" / Path(item["file"]).name
            if destination.exists() and checksum(destination) != manifest["files"][item["file"]]:
                raise ValueError("现场有同名不同内容的 NAR，未覆盖: " + destination.name)
        bundled = {(item.get("group"), item["artifact"], item["version"]) for item in manifest["extensions"]}
        for item in manifest["extensions"]:
            parent = item.get("parent")
            if not parent or tuple(parent[key] for key in ("group", "artifact", "version")) in bundled:
                continue
            path = home / "runtime/nifi-2.9.0/lib" / (parent["artifact"] + "-" + parent["version"] + ".nar")
            if not path.is_file():
                raise ValueError("原 NiFi 缺少扩展所需的父 NAR，未开始升级: " + path.name)
            with ZipFile(path) as nar:
                text = re.sub(r"\r?\n ", "", nar.read("META-INF/MANIFEST.MF").decode("utf-8"))
                metadata = dict(line.split(": ", 1) for line in text.splitlines() if ": " in line)
            if (metadata.get("Nar-Group"), metadata.get("Nar-Id"), metadata.get("Nar-Version")) != tuple(parent[key] for key in ("group", "artifact", "version")):
                raise ValueError("现场父 NAR 坐标不匹配，未开始升级: " + path.name)
        if not (backup / "completed.json").exists():
            estimated = sum((BUNDLE / path).stat().st_size for path in manifest["files"] if path.startswith(("payload/", "jdbc/", "nifi/")))
            estimated += (home / "backend/data-element.jar").stat().st_size + 256 * 1024 * 1024
            if shutil.disk_usage(home).free < estimated:
                raise ValueError("磁盘余量不足以保留旧包和写入增量，需要额外可用空间约 " + str((estimated + 1048575) // 1048576) + " MiB；未开始升级")
        print("预检通过：原 ARM 包、数据库结构、Magic 版本及运行依赖一致")
        print("本次包含 " + str(len(manifest["portableTemplates"])) + " 份可移植模板草稿，绑定生产目标后发布")
        if args.action == "check":
            return
        if (backup / "completed.json").exists():
            verify_resources(db, plans)
            print("本版本已经完成升级，校验通过；没有重复导入或重启")
            return
        if backup.exists():
            raise ValueError("检测到未完成的升级备份。先检查或执行 rollback，勿覆盖原备份")
        backup.mkdir(parents=True, mode=0o700)
        db.error_file = backup / "database-error.txt"
        # Finish the recoverable backup before causing any application downtime.
        selection = resource_selection(plans)
        db.dump("baseline", "api_file_t", backup / "resources.sql", selection)
        node_backup = []
        for row in db.run("baseline_haitong", "SELECT tid,HEX(jdbc_driver_locations) FROM nifi_node_t WHERE tenant_id=" + literal(manifest["tenantId"]) + " AND base_url=" + literal("http://127.0.0.1:" + str(ports["nifi"])) + " AND enabled=1;"):
            tid, encoded = row.split("\t")
            value = "NULL" if encoded == "NULL" else "CONVERT(0x" + encoded + " USING utf8mb4)" if encoded else "''"
            node_backup.append("UPDATE nifi_node_t SET jdbc_driver_locations=" + value + " WHERE tenant_id=" + literal(manifest["tenantId"]) + " AND tid=" + literal(tid) + ";")
        (backup / "rollback-node-drivers.sql").write_text("\n".join(node_backup), encoding="utf-8")
        rollback_sql = "START TRANSACTION;\nSELECT tid FROM api_file_t FOR UPDATE;\nDELETE FROM api_file_t WHERE " + selection + ";\n"
        rollback_sql += (backup / "resources.sql").read_text(encoding="utf-8") + "\nCOMMIT;\n"
        (backup / "rollback-resources.sql").write_text(rollback_sql, encoding="utf-8")
        payload_files = [path.relative_to(BUNDLE / "payload").as_posix() for path in (BUNDLE / "payload").rglob("*") if path.is_file()]
        # Save the old entry/assets together, including the deployment-specific JS configuration.
        originals = {"backend/data-element.jar", "www/haitong/deployment.js"} | {
            path.relative_to(home).as_posix() for path in (home / "www/haitong").rglob("*") if path.is_file()}
        saved = []
        for relative in sorted(originals):
            source = inside(home, relative)
            if source.is_file():
                destination = inside(backup / "files", relative); destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(source, destination); saved.append(relative)
        (backup / "state.json").write_text(json.dumps({"backedUpFiles": saved}), encoding="utf-8")
        try:
            launcher.stop_service("web"); launcher.stop_service("backend")
            # Recheck after stopping all package-owned API writers; SQL also locks and checks the full selected set.
            resources(db, plans)
            for scope, database in (("control", "baseline"), ("tenant", "baseline_haitong")):
                script = BUNDLE / ("db/01-" + scope + "-structure.mysql.sql")
                if script.is_file():
                    db.run(database, script.read_text(encoding="utf-8"))
            check_structure(db, structure, post=True)
            db.run("baseline", (BUNDLE / "db/02-magic-resources.mysql.sql").read_text(encoding="utf-8"))
            db.run("baseline_haitong", (BUNDLE / "db/03-portable-templates.mysql.sql").read_text(encoding="utf-8"))
            verify_resources(db, plans)
            jdbc = json.loads((BUNDLE / "jdbc/manifest.json").read_text(encoding="utf-8"))
            for relative in jdbc["files"]:
                destination = inside(home / "drivers" / manifest["releaseId"], relative)
                destination.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(inside(BUNDLE / "jdbc", relative), destination); destination.chmod(0o644)
            node_drivers(db, home, manifest, jdbc, ports)
            for item in manifest["extensions"]:
                destination = home / "runtime/nifi-2.9.0/extensions" / Path(item["file"]).name
                if destination.exists() and checksum(destination) != manifest["files"][item["file"]]:
                    raise ValueError("现场有同名不同内容的 NAR，未覆盖: " + destination.name)
                if not destination.exists():
                    temporary = destination.with_suffix(".uploading")
                    shutil.copy2(BUNDLE / item["file"], temporary); temporary.chmod(0o644); temporary.replace(destination)
            url = "http://127.0.0.1:" + str(ports["nifi"]) + "/nifi-api"
            def ready():
                types = launcher.http_json(url + "/flow/processor-types")["processorTypes"]
                types += launcher.http_json(url + "/flow/controller-service-types")["controllerServiceTypes"]
                available = {(row["bundle"]["artifact"], row["bundle"]["version"], row["type"]) for row in types}
                return all((item["artifact"], item["version"], component) in available for item in manifest["extensions"] for component in item["types"])
            launcher.wait_for("nifi", ready, 240)
            provision_codecs(launcher, url)
            for relative in payload_files:
                target = inside(home, relative); target.parent.mkdir(parents=True, exist_ok=True)
                temporary = target.with_suffix(target.suffix + ".updating")
                shutil.copy2(inside(BUNDLE / "payload", relative), temporary); temporary.chmod(0o644); temporary.replace(target)
            deployment = home / "www/haitong/deployment.js"
            text = deployment.read_text(encoding="utf-8")
            marker = "// Haitong update: template management route"
            if marker not in text:
                text += "\n" + marker + "\nif (Array.isArray(window.haitongDeployment?.enabledRoutes) && !window.haitongDeployment.enabledRoutes.includes('/development/templates')) window.haitongDeployment.enabledRoutes.push('/development/templates');\n"
                deployment.write_text(text, encoding="utf-8")
            start_application(launcher, ports, json.loads(credentials_file.read_text()))
            (backup / "completed.json").write_text(json.dumps({"releaseId": manifest["releaseId"], "time": int(time.time())}), encoding="utf-8")
            print("升级完成。端口与原地址保持一致；请强制刷新浏览器，并在模板管理绑定目标后发布模板")
            print("受限备份目录: " + str(backup))
        except Exception:
            print("更新未完成；备份已保留，可执行 rollback 恢复原应用和 Magic。没有停止 NiFi 或清理队列", file=sys.stderr)
            raise


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)

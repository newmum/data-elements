"""Build an existing-installation Haitong ARM/MySQL update without runtime credentials."""
import argparse
import hashlib
import importlib.util
from io import BytesIO
import json
from pathlib import Path
import re
import shutil
import sys
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
spec = importlib.util.spec_from_file_location("haitong_deployment", ROOT / "tools/package-haitong-deployment.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)
TENANT = release.TENANT


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_digest(path):
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1048576), b""):
            value.update(block)
    return value.hexdigest()


def sql(value):
    return "NULL" if value is None else "CONVERT(0x" + str(value).encode("utf-8").hex() + " USING utf8mb4) COLLATE utf8mb4_general_ci" if value != "" else "''"


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(value if isinstance(value, bytes) else value.encode("utf-8"))


def tables(text):
    found = {}
    for ddl, name in re.findall(r"(?ms)^(CREATE TABLE `([^`]+)` \(.*?^\) ENGINE=[^\r\n]+;)", text):
        columns, constraints = {}, []
        for line in ddl.splitlines()[1:-1]:
            column = re.match(r"\s*`([^`]+)` (.*)", line)
            if column:
                definition = column[2].rstrip(",")
                if column[1] == "tenant_id":
                    definition = re.sub(r"DEFAULT '[^']*'", "DEFAULT '" + TENANT + "'", definition)
                columns[column[1]] = definition
            else:
                constraints.append(line.strip().rstrip(","))
        found[name] = {"ddl": ddl, "columns": columns, "constraints": constraints}
    return found


def old_resources(text):
    marker = "INSERT INTO api_file_t (tid,file_path,file_content,is_del) VALUES\n"
    payload = text.split(marker, 1)[1].split(";", 1)[0]
    pattern = r"\((NULL|CONVERT\(0x[a-f0-9]+ USING utf8mb4\)),CONVERT\(0x([a-f0-9]+) USING utf8mb4\),CONVERT\(0x([a-f0-9]+) USING utf8mb4\),0\)"
    result = {}
    for match in re.finditer(pattern, payload):
        identity, encoded_path, encoded_content = match.groups()
        tid = None if identity == "NULL" else bytes.fromhex(re.search(r"0x([a-f0-9]+)", identity)[1]).decode("utf-8")
        path, content = (bytes.fromhex(value).decode("utf-8") for value in (encoded_path, encoded_content))
        key = tid or "legacy-null:" + hashlib.md5(path.encode()).hexdigest()
        if key in result:
            raise ValueError("Ambiguous duplicate resource row in baseline: " + path)
        result[key] = {"id": tid, "path": path, "content": content, "sha256": digest(content.encode())}
    if len(result) < 30:
        raise ValueError("Cannot identify the original packaged Magic rows")
    return result


def resource_identity(content):
    try:
        return json.loads(content.split("================================", 1)[0]).get("id")
    except (ValueError, AttributeError):
        return None


def reconcile_resources(rows, previous):
    indexed = [dict(item, baselineKey=key) for key, item in previous.items()]
    by_path = {item["path"]: item for item in indexed}
    by_identity = {}
    for item in indexed:
        identity = resource_identity(item["content"])
        if identity:
            if identity in by_identity:
                raise ValueError("Duplicate resource metadata identity in baseline: " + identity)
            by_identity[identity] = item
    result = {}
    for source_id, path, content in rows:
        identity = resource_identity(content)
        old = by_identity.get(identity) if identity else by_path.get(path)
        tid = old["id"] if old and old["id"] else (None if old else source_id) or hashlib.md5(("haitong-resource:" + (identity or path)).encode()).hexdigest()
        if tid in result:
            raise ValueError("Duplicate target resource row identity: " + tid)
        result[tid] = {"id": tid, "path": path, "content": content, "sha256": digest(content.encode()), "baselineKey": old["baselineKey"] if old else None}
    return result


def retired_metadata(item, previous):
    details = {"metadataId": resource_identity(item["content"]), "kind": "directory" if item["path"].endswith("/") else "group" if item["path"].endswith("/group.json") else "script"}
    try:
        meta = json.loads(item["content"].split("================================", 1)[0])
        groups = {resource_identity(row["content"]): json.loads(row["content"].split("================================", 1)[0])
                  for row in previous.values() if row["path"].endswith("/group.json")}
        segments = [meta.get("path", "")]
        parent = meta.get("groupId") or meta.get("parentId")
        seen = set()
        while parent and parent != "0" and parent in groups:
            if parent in seen:
                raise ValueError("Cycle in baseline Magic groups")
            seen.add(parent)
            group = groups[parent]
            segments.insert(0, group.get("path", ""))
            parent = group.get("parentId")
        route = "".join(segments)
        if details["kind"] == "script" and len(route) > 1:
            details["retiredSymbol"] = route
    except (ValueError, AttributeError):
        pass
    return details


def order_extensions(records):
    pending = {(item["group"], item["artifact"], item["version"]): item for item in records}
    if len(pending) != len(records):
        raise ValueError("Duplicate extension coordinates")
    ordered = []
    while pending:
        ready = [key for key, item in pending.items() if not item.get("parent") or
                 tuple(item["parent"][part] for part in ("group", "artifact", "version")) not in pending]
        if not ready:
            raise ValueError("Cycle in extension parent dependencies")
        for key in sorted(ready):
            ordered.append(pending.pop(key))
    return ordered


def conditional(condition, statement):
    return ("SET @haitong_ddl=IF(" + condition + "," + sql(statement) + ",'SELECT 1');\n"
            "PREPARE haitong_stmt FROM @haitong_ddl; EXECUTE haitong_stmt; DEALLOCATE PREPARE haitong_stmt;\n")


def portable_templates(config_path, source_tenant):
    """Read published definitions only; export logical slots, never live connections."""
    import pymysql
    export_spec = importlib.util.spec_from_file_location("schema_export", ROOT / "tools/export-db.py")
    export = importlib.util.module_from_spec(export_spec)
    export_spec.loader.exec_module(export)
    config = json.loads(config_path.read_text(encoding="utf-8-sig"))
    candidates = [entry for entry in export.connections(config) if entry[0] == "public-security"]
    if len(candidates) != 1 or candidates[0][1] != "mysql":
        raise ValueError("Expected the explicitly selected MySQL template source")
    options = dict(candidates[0][3])
    options.update(cursorclass=pymysql.cursors.DictCursor, autocommit=False, connect_timeout=10, read_timeout=30)
    with pymysql.connect(**options) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT tid,family_id,revision_no,display_name,definition FROM nifi_access_template_t WHERE tenant_id=%s AND status='PUBLISHED' ORDER BY family_id,revision_no", (source_tenant,))
            rows = cursor.fetchall()
        connection.rollback()
    latest = {row["family_id"]: row for row in rows}
    spec_keys = {"topology", "sourceType", "targets", "intervalSeconds", "fetchSize", "sourceSchedule", "engine", "hiveMergeMaxAgeSeconds", "flow"}
    config_keys = {"templateBinding", "batchSize", "schedulingPeriod", "manualBackfill", "useRegisteredMappings", "executionMode", "sharedGovernance",
                   "hiveWriteMode", "hiveHdfsMerge", "statementType", "mergeMaxBinAge", "hiveTimestampFormat", "writerType", "insertThenMerge", "dbType",
                   "hiveConnectionMode", "metadataAccessMode", "hiveProfile", "authMode", "backfillSource", "enabled", "scheduledState", "syncMode"}
    result = []
    for row in latest.values():
        original = json.loads(row["definition"])
        definition = {key: value for key, value in original.items() if key in spec_keys}
        definition["targetDatasourceIds"] = []
        if "flow" in definition:
            flow = definition["flow"]
            definition["flow"] = {"version": flow.get("version", 1), "nodes": [], "edges": []}
            for node in flow.get("nodes", []):
                clean = {key: node[key] for key in ("id", "manifestKey", "label", "category", "x", "y") if key in node}
                clean["config"] = {key: value for key, value in node.get("config", {}).items() if key in config_keys}
                if clean["config"].get("backfillSource"):
                    clean["config"].update(enabled=False, scheduledState="DISABLED", customWherePredicate="1=0")
                definition["flow"]["nodes"].append(clean)
            for edge in flow.get("edges", []):
                definition["flow"]["edges"].append({key: edge[key] for key in ("id", "source", "target", "sourcePort", "targetPort", "label") if key in edge})
        family = hashlib.md5(("haitong-portable-template:" + row["family_id"]).encode()).hexdigest()
        identity = hashlib.md5((family + ":" + str(row["revision_no"])).encode()).hexdigest()
        result.append({"id": identity, "familyId": family, "revision": row["revision_no"], "name": row["display_name"],
                       "definition": definition, "requiresProductionBinding": True})
    return sorted(result, key=lambda item: item["name"])


def structure(before, after):
    script = ["-- Existing installation only. Additive upgrade; never run full initialization SQL.\nSET NAMES utf8mb4;\n"]
    operations = []
    for name, target in after.items():
        old = before.get(name)
        if old is None:
            statement = target["ddl"]
            # A standalone tenant keeps its own tenant defaults.
            statement = re.sub(r"(`tenant_id`[^\r\n]*?DEFAULT )'[^']*'", r"\g<1>'" + TENANT + "'", statement)
            script.append(conditional("NOT EXISTS(SELECT 1 FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=" + sql(name) + ")", statement))
            operations.append({"kind": "table", "table": name, "columns": target["columns"]})
            continue
        for column, definition in target["columns"].items():
            if column not in old["columns"]:
                script.append(conditional("NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=" + sql(name) + " AND column_name=" + sql(column) + ")",
                                          "ALTER TABLE `" + name + "` ADD COLUMN `" + column + "` " + definition))
                operations.append({"kind": "column", "table": name, "column": column, "after": definition})
            elif definition != old["columns"][column]:
                if name != "da_catalog_item_t" or column != "col_comment" or not definition.startswith("longtext "):
                    raise ValueError("Unreviewed existing-column change: " + name + "." + column)
                script.append(conditional("EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=" + sql(name) + " AND column_name=" + sql(column) + " AND data_type<>'longtext')",
                                          "ALTER TABLE `" + name + "` MODIFY COLUMN `" + column + "` " + definition))
                operations.append({"kind": "widen", "table": name, "column": column, "before": old["columns"][column], "after": definition})
        added = set(target["constraints"]) - set(old["constraints"])
        removed = set(old["constraints"]) - set(target["constraints"])
        for constraint in sorted(added):
            match = re.match(r"(?:UNIQUE )?KEY `([^`]+)`", constraint)
            if not match:
                raise ValueError("Unreviewed constraint addition: " + name)
            script.append(conditional("NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=" + sql(name) + " AND index_name=" + sql(match[1]) + ")",
                                      "ALTER TABLE `" + name + "` ADD " + constraint))
            operations.append({"kind": "index", "table": name, "name": match[1], "definition": constraint})
        for constraint in sorted(removed):
            match = re.match(r"UNIQUE KEY `(uk_lake_zone_object)`", constraint)
            if name != "lake_zone_object_t" or not match:
                raise ValueError("Unreviewed constraint retirement: " + name)
            script.append(conditional("EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=" + sql(name) + " AND index_name=" + sql(match[1]) + ")",
                                      "ALTER TABLE `" + name + "` DROP INDEX `" + match[1] + "`"))
            operations.append({"kind": "indexretire", "table": name, "name": match[1], "definition": constraint})
    return "".join(script), operations


def main():
    p = argparse.ArgumentParser(description=__doc__)
    for name in ("baseline", "jar", "frontend", "mapping-nar", "templates", "jdbc", "output"):
        p.add_argument("--" + name, type=Path, required=True)
    p.add_argument("--extension", type=Path, action="append", default=[])
    p.add_argument("--database-only", action="store_true", help="Generate the reviewed database delta and plans without binary payloads")
    p.add_argument("--validation-report", type=Path)
    p.add_argument("--release-id", default="haitong-linux-aarch64-update-20261126")
    a = p.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]+", a.release_id):
        raise ValueError("release-id must be one directory name")
    stage = a.output / a.release_id
    if stage.exists():
        raise ValueError("Choose a fresh output directory")
    stage.mkdir(parents=True)
    with ZipFile(a.baseline) as old:
        original_jar_hash = digest(old.read("backend/data-element.jar"))
        before_control = old.read("db/01-control.sql").decode("utf-8")
        before_tenant = old.read("db/02-tenant.sql").decode("utf-8")
        if json.loads(old.read("config/runtime.json"))["architecture"] != "aarch64":
            raise ValueError("Expected original ARM standalone package")
        old_rows = old_resources(before_control)
    with ZipFile(a.jar) as backend:
        if any("nacos" in name.lower() for name in backend.namelist() if name.startswith("BOOT-INF/lib/")):
            raise ValueError("Backend must be the no-Nacos build")
        engine = backend.read("BOOT-INF/classes/com/linewell/dataelement/mapping/core/MappingEngine.class")
    with ZipFile(a.mapping_nar) as nar:
        meta = release.nar_metadata(nar)
        if meta.get("Nar-Dependency-Version") != "2.9.0" or meta["Nar-Version"].encode() not in engine:
            raise ValueError("NiFi SDK or mapping engine mismatch")
        core = next(name for name in nar.namelist() if "/haitong-mapping-core-" in name and name.endswith(".jar"))
        with ZipFile(BytesIO(nar.read(core))) as shared, ZipFile(a.jar) as backend:
            shared_classes = [name for name in shared.namelist() if name.startswith("com/linewell/dataelement/mapping/") and name.endswith(".class")]
            if not shared_classes or any(shared.read(name) != backend.read("BOOT-INF/classes/" + name) for name in shared_classes):
                raise ValueError("Backend and NiFi shared mapping bytecode differs")
    for scope, original, canonical in [("control", before_control, "baseline"), ("tenant", before_tenant, "baseline_ga")]:
        text, operations = structure(tables(original), tables((ROOT / "db" / canonical / "init.sql").read_text(encoding="utf-8")))
        if operations:
            write(stage / ("db/01-" + scope + "-structure.mysql.sql"), text)
        if scope == "control":
            control_operations = operations
        else:
            tenant_operations = operations
    rows, resource_paths = release.magic_rows(True)
    new_rows = reconcile_resources(rows, old_rows)
    if None in new_rows or len(new_rows) != len(rows):
        raise ValueError("Missing or duplicate resource row identity")
    changes = []
    for tid, item in sorted(new_rows.items()):
        old = old_rows.get(item["baselineKey"])
        changes.append({"id": tid, "path": item["path"], "before": old["sha256"] if old else None, "after": item["sha256"], "action": "upsert", "legacyNullPath": old["path"] if old and old["id"] is None else None})
    matched = {item["baselineKey"] for item in new_rows.values() if item["baselineKey"]}
    for key, item in sorted(old_rows.items()):
        if key not in matched:
            tid = item["id"] or hashlib.md5(("haitong-retired-null:" + item["path"]).encode()).hexdigest()
            changes.append({"id": tid, "path": item["path"], "before": item["sha256"], "after": None, "action": "retire", "legacyNullPath": item["path"] if item["id"] is None else None,
                            **retired_metadata(item, old_rows)})
    guards = ",\n".join("(" + ",".join([sql(item["id"]), sql(item["before"]), sql(item["after"]), sql(item["action"]), sql(item["path"]),
                                          sql(new_rows[item["id"]]["content"]) if item["action"] == "upsert" else "NULL", sql(item["legacyNullPath"])]) + ")" for item in changes)
    join = "(r.tid=e.tid OR (r.tid IS NULL AND e.legacy_path IS NOT NULL AND (r.file_path=e.legacy_path OR r.file_path=e.file_path)))"
    header = ("SET NAMES utf8mb4;\n"
              "CREATE TEMPORARY TABLE haitong_expected_resource (tid varchar(64) PRIMARY KEY,before_hash varchar(64),after_hash varchar(64),operation varchar(16),file_path varchar(1000),file_content longtext,legacy_path varchar(1000)) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;\n"
              "CREATE TEMPORARY TABLE haitong_update_assertion (ok int NOT NULL,CHECK(ok=1));\n"
              "INSERT INTO haitong_expected_resource VALUES\n" + guards + ";\nSTART TRANSACTION;\n"
              "SELECT tid FROM api_file_t FOR UPDATE;\n"
              "INSERT INTO haitong_update_assertion SELECT IF(EXISTS(SELECT tid FROM api_file_t WHERE tid IS NOT NULL GROUP BY tid HAVING COUNT(*)>1),0,1);\n"
              "INSERT INTO haitong_update_assertion SELECT IF(EXISTS(SELECT file_path FROM api_file_t WHERE tid IS NULL GROUP BY file_path HAVING COUNT(*)>1),0,1);\n"
              "INSERT INTO haitong_update_assertion SELECT IF(EXISTS(SELECT 1 FROM haitong_expected_resource e LEFT JOIN api_file_t r ON " + join + " WHERE "
              "(r.file_path IS NULL AND e.before_hash IS NOT NULL AND e.operation='upsert') OR "
              "(r.file_path IS NOT NULL AND NOT COALESCE((e.before_hash IS NOT NULL AND SHA2(r.file_content,256)=e.before_hash) OR (e.after_hash IS NOT NULL AND SHA2(r.file_content,256)=e.after_hash),FALSE)) OR "
              "(r.file_path IS NOT NULL AND e.operation='upsert' AND COALESCE(r.is_del,-1)<>0)),0,1);\n")
    bulk = ("UPDATE api_file_t r JOIN haitong_expected_resource e ON " + join + " SET r.file_path=e.file_path,r.file_content=e.file_content,r.is_del=0,r.updated_time=NOW(6) WHERE e.operation='upsert';\n"
            "INSERT INTO api_file_t (tid,file_path,file_content,is_del,created_time,updated_time) SELECT e.tid,e.file_path,e.file_content,0,NOW(6),NOW(6) FROM haitong_expected_resource e LEFT JOIN api_file_t r ON " + join + " WHERE e.operation='upsert' AND r.file_path IS NULL;\n"
            "UPDATE api_file_t r JOIN haitong_expected_resource e ON " + join + " SET r.is_del=1,r.updated_time=NOW(6) WHERE e.operation='retire';\n")
    write(stage / "db/02-magic-resources.mysql.sql", header + bulk + "COMMIT;\nDROP TEMPORARY TABLE haitong_update_assertion;\nDROP TEMPORARY TABLE haitong_expected_resource;\n")
    write(stage / "db/resource-plan.json", json.dumps(changes, ensure_ascii=False, indent=2))
    templates = json.loads(a.templates.read_text(encoding="utf-8"))
    template_sql = ["-- Portable template drafts: bind production targets and publish in Template Management.\nSET NAMES utf8mb4;\nSTART TRANSACTION;\n"]
    for item in templates:
        definition = json.dumps(item["definition"], ensure_ascii=False, separators=(",", ":"))
        fields = ["tid", "tenant_id", "family_id", "revision_no", "display_name", "status", "definition", "created_by", "created_at", "updated_at", "default_tenant_id"]
        values = [sql(item["id"]), sql(TENANT), sql(item["familyId"]), str(item["revision"]), sql(item["name"]), "'DRAFT'", sql(definition), "'deployment'", "UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000", "UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000", "NULL"]
        template_sql.append("INSERT INTO nifi_access_template_t (" + ",".join(fields) + ") SELECT " + ",".join(values) + " WHERE NOT EXISTS (SELECT 1 FROM nifi_access_template_t WHERE tenant_id=" + sql(TENANT) + " AND (tid=" + sql(item["id"]) + " OR (family_id=" + sql(item["familyId"]) + " AND revision_no=" + str(item["revision"]) + ")));\n")
    template_sql.append("COMMIT;\n")
    write(stage / "db/03-portable-templates.mysql.sql", "".join(template_sql))
    write(stage / "db/templates.json", json.dumps(templates, ensure_ascii=False, indent=2))
    write(stage / "db/structure-plan.json", json.dumps({"control": control_operations, "tenant": tenant_operations}, ensure_ascii=False, indent=2))
    if a.database_only:
        print(json.dumps({"path": str(stage), "magicResources": len(new_rows), "legacyNullRows": sum(item.get("legacyNullPath") is not None for item in changes),
                          "retiredMagicResources": sum(item["action"] == "retire" for item in changes)}))
        return
    if "/haitong/assets/" not in (a.frontend / "index.html").read_text(encoding="utf-8"):
        raise ValueError("Expected the full Haitong production build")
    write(stage / "payload/backend/data-element.jar", a.jar.read_bytes())
    for file in a.frontend.rglob("*"):
        if file.is_file() and file.name != "deployment.js":
            write(stage / "payload/www/haitong" / file.relative_to(a.frontend), file.read_bytes())
    extensions = [a.mapping_nar] + a.extension
    extension_records = []
    for extension in extensions:
        with ZipFile(extension) as nar:
            metadata = release.nar_metadata(nar)
            if nar.testzip() is not None or metadata.get("Nar-Dependency-Version") not in (None, "2.9.0"):
                raise ValueError("Invalid NiFi 2.9 extension: " + extension.name)
            registrations = set()
            for library in nar.namelist():
                if library.startswith("META-INF/bundled-dependencies/") and library.endswith(".jar"):
                    with ZipFile(BytesIO(nar.read(library))) as jar:
                        for service in ("org.apache.nifi.processor.Processor", "org.apache.nifi.controller.ControllerService"):
                            provider = "META-INF/services/" + service
                            if provider in jar.namelist():
                                registrations.update(line.split("#", 1)[0].strip() for line in jar.read(provider).decode().splitlines() if line.split("#", 1)[0].strip())
        relative = "nifi/extensions/" + extension.name
        write(stage / relative, extension.read_bytes())
        parent = {key: metadata.get("Nar-Dependency-" + field) for key, field in (("group", "Group"), ("artifact", "Id"), ("version", "Version"))}
        if any(parent.values()) and not all(parent.values()):
            raise ValueError("Incomplete extension parent coordinates")
        extension_records.append({"file": relative, "group": metadata["Nar-Group"], "artifact": metadata["Nar-Id"], "version": metadata["Nar-Version"], "types": sorted(registrations),
                                  "parent": parent if all(parent.values()) else None})
    extension_records = order_extensions(extension_records)
    jdbc = json.loads((a.jdbc / "manifest.json").read_text(encoding="utf-8"))
    # Compiler keys are distinct from the optional standalone driver-class examples.
    jdbc["profiles"]["HIVE_OPEN_SOURCE_NATIVE"] = dict(jdbc["profiles"]["HIVE_OPEN_SOURCE"])
    jdbc["profiles"]["HIVE_OPEN_SOURCE"] = dict(jdbc["profiles"]["HIVE_COMPAT"])
    jdbc["profiles"]["ORACLE_TYPED"] = dict(jdbc["profiles"]["ORACLE_COMPAT"])
    for path, item in jdbc["files"].items():
        data = (a.jdbc / path).read_bytes()
        if digest(data) != item["sha256"]:
            raise ValueError("JDBC digest mismatch")
        write(stage / "jdbc" / path, data)
    write(stage / "jdbc/manifest.json", json.dumps(jdbc, ensure_ascii=False, indent=2))
    for name in ("upgrade.py", "upgrade.sh", "upgrade-instructions.txt"):
        write(stage / name, (ROOT / "tools/haitong-deployment/increment" / name).read_bytes())
    if a.validation_report:
        write(stage / "validation-summary.json", a.validation_report.read_bytes())
    files = {path.relative_to(stage).as_posix(): file_digest(path) for path in stage.rglob("*") if path.is_file()}
    manifest = {"releaseId": a.release_id, "architecture": "aarch64", "tenantId": TENANT,
                "baselineBackendSha256": original_jar_hash, "backendSha256": digest(a.jar.read_bytes()),
                "mappingVersion": meta["Nar-Version"], "extensions": extension_records, "files": files,
                "matchingMappingClasses": len(shared_classes),
                "magicResources": len(new_rows), "retiredMagicResources": sum(item["action"] == "retire" for item in changes),
                "portableTemplates": [{"name": item["name"], "id": item["id"], "status": "DRAFT"} for item in templates],
                "structureOperationCount": len(control_operations) + len(tenant_operations),
                "preserved": ["config", "data", "runtime repositories", "production workflows", "accounts", "template bindings"]}
    write(stage / "manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2))
    archive = a.output / (a.release_id + ".zip")
    with ZipFile(archive, "w", ZIP_DEFLATED) as target:
        for path in sorted(stage.rglob("*")):
            if path.is_file():
                target.write(path, a.release_id + "/" + path.relative_to(stage).as_posix(), compress_type=ZIP_STORED if path.suffix in (".jar", ".nar") else ZIP_DEFLATED)
    with ZipFile(archive) as target:
        if target.testzip() is not None:
            raise ValueError("ZIP checksum validation failed")
    archive_hash = file_digest(archive)
    write(a.output / "SHA256SUMS.txt", archive_hash + "  " + archive.name + "\n")
    print(json.dumps({k: manifest[k] for k in ("releaseId", "magicResources", "retiredMagicResources", "structureOperationCount")}))
    print(str(archive), archive.stat().st_size, archive_hash)


if __name__ == "__main__":
    main()

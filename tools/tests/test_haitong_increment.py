"""Existing-installation safety; SQL is also rehearsed against isolated MySQL schemas."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import types
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
fcntl_stub = types.SimpleNamespace(LOCK_EX=1, LOCK_SH=2, flock=lambda *args: None)
with mock.patch.dict(sys.modules, {"fcntl": fcntl_stub}):
    spec = importlib.util.spec_from_file_location("haitong_upgrade", ROOT / "tools/haitong-deployment/increment/upgrade.py")
    upgrade = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(upgrade)
original_resources = upgrade.resources
spec = importlib.util.spec_from_file_location("haitong_increment_package", ROOT / "tools/package-haitong-increment.py")
package = importlib.util.module_from_spec(spec)
spec.loader.exec_module(package)


class FakeDatabase:
    def __init__(self, *args, **kwargs):
        pass

    def run(self, database, statement):
        return ["validation-tenant"] if "SELECT tid FROM sym_tenant_t" in statement else []

    def dump(self, database, table, destination, where=None):
        destination.write_text("-- isolated validation restore\n", encoding="utf-8")


class FakeLauncher:
    def __init__(self, home):
        self.ROOT = home
        self.stopped = []
        self.spawned = []

    def service_pid(self, service):
        return 123

    def stop_service(self, service):
        self.stopped.append(service)

    def spawn(self, service, *args, **kwargs):
        self.spawned.append(service)

    def mysql_tcp_url(self, ports, database):
        return "jdbc:mysql://localhost/" + database

    def http_json(self, url):
        if url.endswith("/flow/about"):
            return {"about":{"version":"2.9.0"}}
        if url.endswith("/flow/processor-types"):
            return {"processorTypes": []}
        if url.endswith("/flow/controller-service-types"):
            return {"controllerServiceTypes": []}
        return {"status": "UP"}

    def wait_for(self, service, callback, seconds=120):
        if not callback():
            raise RuntimeError("Validation callback was not ready")


class ExistingInstallationTest(unittest.TestCase):
    def setUp(self):
        workspace = ROOT / "logs/haitong-arm-increment-tests"
        workspace.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=workspace)
        self.addCleanup(self.temp.cleanup)
        self.original_cwd = Path.cwd()
        self.original_umask = os.umask(0o077)
        self.addCleanup(lambda: os.umask(self.original_umask))
        self.addCleanup(lambda: os.chdir(self.original_cwd))
        base = Path(self.temp.name)
        self.home, self.bundle = base / "site", base / "update"
        self.old_jar, self.new_jar = b"validation original backend", b"validation new backend"
        originals = {"backend/data-element.jar": self.old_jar, "www/haitong/index.html": b"validation original page",
                     "www/haitong/deployment.js": b"window.haitongDeployment={enabledRoutes:['/development/nifi-flows'],site:'validation'};",
                     "config/runtime.json": b'{"architecture":"aarch64"}', "config/application.yml": b"validation: existing-site-config\n",
                     "config/Caddyfile": b"validation existing proxy", "config/ports.json": json.dumps({"web": 8090,"webBind":"0.0.0.0","backend":18088,"redis":16379,"nifi":18080}).encode(),
                     "data/local/credentials.json": json.dumps(dict(redis="validation",mysql="validation",transport="validation")).encode(),
                     "data/local/mysql-root.cnf": b"# validation existing restricted client configuration",
                     "data/local/launcher.lock": b"", "runtime/nifi-2.9.0/conf/nifi.properties": b"nifi.nar.library.autoload.directory=./extensions\n",
                     "runtime/nifi-2.9.0/flowfile_repository/validation-marker": b"validation queue marker"}
        for name, data in originals.items():
            path = self.home / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        self.originals = originals
        payload = {"payload/backend/data-element.jar": self.new_jar, "payload/www/haitong/index.html": b"validation updated page",
                   "db/resource-plan.json": b"[]", "db/structure-plan.json": b'{"control":[],"tenant":[]}',
                   "db/02-magic-resources.mysql.sql": b"-- validation resources", "db/03-portable-templates.mysql.sql": b"-- validation templates",
                   "jdbc/manifest.json": b'{"files":{},"profiles":{}}'}
        for name, data in payload.items():
            path = self.bundle / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
        manifest = {"releaseId":"validation-update", "architecture":"aarch64","tenantId":"validation-tenant", "portableTemplates":[],
                    "baselineBackendSha256":hashlib.sha256(self.old_jar).hexdigest(),"backendSha256":hashlib.sha256(self.new_jar).hexdigest(),
                    "files":{name:hashlib.sha256(data).hexdigest() for name,data in payload.items()},"extensions":[],"mappingVersion":"1.1.54"}
        (self.bundle / "manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
        self.launcher = FakeLauncher(self.home)
        for key, value in {"BUNDLE":self.bundle,"Database":FakeDatabase,"load_launcher":lambda home:self.launcher,
                           "resources":lambda *args:{},"check_structure":lambda *args,**kwargs:None,
                           "verify_resources":lambda *args:None,"node_drivers":lambda *args:None,"provision_codecs":lambda *args:None,"codec_plan":lambda *args:None}.items():
            patcher = mock.patch.object(upgrade, key, value); patcher.start(); self.addCleanup(patcher.stop)
        for name, value in (("system","Linux"),("machine","aarch64")):
            patcher = mock.patch.object(upgrade.platform, name, return_value=value); patcher.start(); self.addCleanup(patcher.stop)

    def invoke(self, action):
        with mock.patch.object(sys, "argv", ["upgrade.py", action, str(self.home)]):
            upgrade.main()

    def test_check_does_not_stop_processes_or_write_backup(self):
        self.invoke("check")
        self.assertEqual(self.launcher.stopped, [])
        self.assertFalse((self.home / "data/local/upgrades").exists())
        self.assertEqual((self.home / "backend/data-element.jar").read_bytes(), self.old_jar)

    def test_apply_repeat_and_rollback_preserve_site_state_and_nifi_queue(self):
        self.invoke("apply")
        self.assertEqual((self.home / "backend/data-element.jar").read_bytes(), self.new_jar)
        self.assertEqual(self.launcher.stopped, ["web", "backend"])
        self.assertIn("/development/templates", (self.home / "www/haitong/deployment.js").read_text())
        self.invoke("apply")
        self.assertEqual(self.launcher.stopped, ["web", "backend"])
        for name in ("config/application.yml","config/Caddyfile","config/ports.json","data/local/credentials.json","runtime/nifi-2.9.0/flowfile_repository/validation-marker"):
            self.assertEqual((self.home / name).read_bytes(), self.originals[name])
        self.invoke("rollback")
        for name in ("backend/data-element.jar","www/haitong/index.html","www/haitong/deployment.js"):
            self.assertEqual((self.home / name).read_bytes(), self.originals[name])
        self.assertNotIn("nifi", self.launcher.stopped)

    def test_custom_backend_and_resource_conflicts_fail_before_downtime(self):
        (self.home / "backend/data-element.jar").write_bytes(b"validation operator build")
        with self.assertRaisesRegex(ValueError, "生产后端不同"):
            self.invoke("apply")
        (self.home / "backend/data-element.jar").write_bytes(self.old_jar)
        with mock.patch.object(upgrade, "resources", side_effect=RuntimeError("validation conflict")):
            with self.assertRaisesRegex(RuntimeError, "validation conflict"):
                self.invoke("apply")
        self.assertEqual(self.launcher.stopped, [])

    def test_corrupt_payload_and_path_escape_are_rejected(self):
        with self.assertRaises(ValueError):
            upgrade.inside(self.home, "../validation-escape")
        (self.bundle / "payload/backend/data-element.jar").write_bytes(b"validation corrupted")
        with self.assertRaisesRegex(ValueError, "更新包文件损坏"):
            self.invoke("apply")
        self.assertEqual(self.launcher.stopped, [])

    def test_resource_moves_keep_old_row_identity_and_new_directories_are_stable(self):
        old = {"validation-old-row":{"id":"validation-old-row","path":"/old.ms","content":'{"id":"validation-resource"}\n================================\nreturn 1'}}
        rows = [("validation-new-source-row","/new.ms",'{"id":"validation-resource"}\n================================\nreturn 2'),(None,"/new/","this is directory")]
        first = package.reconcile_resources(rows, old)
        self.assertIn("validation-old-row", first)
        self.assertEqual(first, package.reconcile_resources(rows, old))
        self.assertEqual(len(first), 2)

    def test_original_null_row_id_is_identified_by_path_before_and_after_a_move(self):
        content = '{"id":"validation-null-id-resource"}\n================================\nreturn 1'
        previous = {"legacy-null:validation":{"id":None,"path":"/old.ms","content":content}}
        result = package.reconcile_resources([(None,"/new.ms",content)], previous)
        key = next(iter(result))
        plans = [{"id":key,"path":"/new.ms","legacyNullPath":"/old.ms","before":hashlib.sha256(content.encode()).hexdigest(),"after":hashlib.sha256(content.encode()).hexdigest(),"action":"upsert"}]
        for path in ("/old.ms", "/new.ms"):
            database = mock.Mock()
            database.run.side_effect = [["\t".join(["NULL",plans[0]["before"],"0",path.encode().hex()])],
                                        ["\t".join(["NULL",path.encode().hex(),content.encode().hex()])]]
            # Use the original implementation instead of the filesystem-test patch.
            with mock.patch.object(upgrade, "resources", original_resources):
                self.assertEqual(upgrade.resources(database,plans),{key:(plans[0]["before"],"0")})
        self.assertIn("tid IS NULL",upgrade.resource_selection(plans))

    def test_parent_nars_are_installed_before_children_and_cycles_are_rejected(self):
        parent = {"group":"validation","artifact":"parent","version":"1","parent":None}
        child = {"group":"validation","artifact":"child","version":"1","parent":{"group":"validation","artifact":"parent","version":"1"}}
        self.assertEqual(package.order_extensions([child,parent]),[parent,child])
        parent["parent"] = {"group":"validation","artifact":"child","version":"1"}
        with self.assertRaisesRegex(ValueError,"Cycle"):
            package.order_extensions([child,parent])


if __name__ == "__main__":
    unittest.main()

import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("export_db_projects", Path(__file__).resolve().parents[1] / "export-db.py")
exporter = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(exporter)


class IndependentSchemaExportTests(unittest.TestCase):
    def test_project_is_not_added_to_industry_connections(self):
        config = {"control": {"database": "baseline"},
                  "nacos_tenant_database": {"tenant-bindings": {}, "data-sources": {}},
                  "project_databases": {"haitong-data-pull": {"database": "haitong", "password": "${SCHEMA_TEST_PASSWORD}"}}}
        with patch.dict("os.environ", {"SCHEMA_TEST_PASSWORD": "test-only"}):
            self.assertEqual([row[2] for row in exporter.connections(config)], ["baseline"])
            self.assertEqual(list(exporter.project_connections(config))[0][2:],
                             ("haitong", {"database": "haitong", "password": "test-only"}))

    def run_export(self, directory, config, export):
        config_path = directory / "config.json"
        config_path.write_text(json.dumps(config), encoding="utf-8")
        args = ["export-db.py", "--config", str(config_path), "--output", str(directory / "db"), "--project", "haitong-data-pull"]
        with patch("sys.argv", args), patch.object(exporter, "mysql_export", side_effect=export):
            exporter.main()

    def test_selected_project_preserves_other_schema_and_manifest(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            baseline = root / "db/baseline/init.sql"
            baseline.parent.mkdir(parents=True)
            baseline.write_text("preserve this schema", encoding="utf-8")
            retained = {"dataSourceKey": "control", "schema": "baseline", "file": "baseline/init.sql", "sha256": "retained"}
            manifest = root / "db/schema-manifest.json"
            manifest.write_text(json.dumps({"formatVersion": 1, "scope": "structure-only", "schemas": [retained]}), encoding="utf-8")
            sql = "CREATE TABLE example(id BIGINT PRIMARY KEY);\n"
            self.run_export(root, {"project_databases": {"haitong-data-pull": {"database": "haitong"}}},
                            lambda *args: ("haitong", sql, {"tables": 1, "views": 0}, {}))
            result = json.loads(manifest.read_text(encoding="utf-8"))
            self.assertEqual(result["schemas"][0], retained)
            self.assertEqual(baseline.read_text(encoding="utf-8"), "preserve this schema")
            self.assertEqual(result["schemas"][1]["sha256"], hashlib.sha256(sql.encode()).hexdigest())

    def test_failed_or_unconfigured_export_does_not_replace_manifest(self):
        for config in [{}, {"project_databases": {"haitong-data-pull": {"database": "haitong"}}}]:
            with self.subTest(config=config), tempfile.TemporaryDirectory() as name:
                root = Path(name)
                manifest = root / "db/schema-manifest.json"
                manifest.parent.mkdir()
                original = '{"schemas": []}\n'
                manifest.write_text(original, encoding="utf-8")
                with self.assertRaises(SystemExit):
                    self.run_export(root, config, RuntimeError("private error must not be printed"))
                self.assertEqual(manifest.read_text(encoding="utf-8"), original)
                self.assertFalse((root / "db/haitong/init.sql").exists())

    def test_project_cannot_replace_another_datasource_path(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            manifest = root / "db/schema-manifest.json"
            manifest.parent.mkdir()
            original = json.dumps({"schemas": [{"dataSourceKey": "control", "file": "haitong/init.sql"}]})
            manifest.write_text(original, encoding="utf-8")
            with self.assertRaisesRegex(SystemExit, "another datasource"):
                self.run_export(root, {"project_databases": {"haitong-data-pull": {"database": "haitong"}}}, None)
            self.assertEqual(manifest.read_text(encoding="utf-8"), original)

    def test_platform_only_export_preserves_unconfigured_project_baseline(self):
        with tempfile.TemporaryDirectory() as name:
            root = Path(name)
            project = root / "db/haitong/init.sql"
            project.parent.mkdir(parents=True)
            project.write_text("preserved independent schema", encoding="utf-8")
            manifest = root / "db/schema-manifest.json"
            retained = {"dataSourceKey": "haitong-data-pull", "project": "haitong-data-pull", "file": "haitong/init.sql"}
            manifest.write_text(json.dumps({"schemas": [retained]}), encoding="utf-8")
            config = root / "config.json"
            config.write_text(json.dumps({"control": {"database": "baseline"}, "nacos_tenant_database": {"tenant-bindings": {}, "data-sources": {}}}), encoding="utf-8")
            with patch("sys.argv", ["export-db.py", "--config", str(config), "--output", str(root / "db")]), patch.object(exporter, "mysql_export", return_value=("baseline", "platform schema\n", {"tables": 1, "views": 0}, {})):
                exporter.main()
            self.assertEqual(json.loads(manifest.read_text(encoding="utf-8"))["schemas"][0], retained)
            self.assertEqual(project.read_text(encoding="utf-8"), "preserved independent schema")


if __name__ == "__main__":
    unittest.main()

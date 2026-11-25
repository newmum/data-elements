"""Installer integrity and preservation tests; all writes use isolated logs fixtures."""
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
INSTALL = ROOT / "tools/nifi-deployment/jdbc/install-jdbc.py"


class JdbcInstallTest(unittest.TestCase):
    def setUp(self):
        workspace = ROOT / "logs/nifi-jdbc-bundle/tests"
        workspace.mkdir(parents=True, exist_ok=True)
        self.temp = tempfile.TemporaryDirectory(dir=workspace)
        self.root = Path(self.temp.name)
        self.source = self.root / "bundle"
        self.source.mkdir()
        shutil.copyfile(INSTALL, self.source / "install-jdbc.py")
        self.jar = self.source / "drivers/validation.jar"
        self.jar.parent.mkdir()
        self.jar.write_bytes(b"validation artifact")
        self.manifest = {"bundleId": "validation-jdbc", "files": {
            "drivers/validation.jar": {"sha256": hashlib.sha256(self.jar.read_bytes()).hexdigest()}
        }, "profiles": {"VALIDATION": {"class": "validation.Driver", "files": ["drivers/validation.jar"]}}}
        self.save()
        self.home = self.root / "nifi home"
        (self.home / "conf").mkdir(parents=True)
        (self.home / "conf/nifi.properties").write_text("validation=true\n", encoding="utf-8")
        (self.home / "drivers").mkdir()
        self.existing = self.home / "drivers/existing.jar"
        self.existing.write_bytes(b"existing driver")

    def tearDown(self):
        self.temp.cleanup()

    def save(self):
        (self.source / "manifest.json").write_text(json.dumps(self.manifest), encoding="utf-8")

    def run_install(self):
        return subprocess.run([sys.executable, str(self.source / "install-jdbc.py"), str(self.home)],
                              capture_output=True, text=True, timeout=30)

    def test_install_and_repeat_preserve_existing_driver_and_config(self):
        for _ in range(2):
            result = self.run_install()
            self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(self.existing.read_bytes(), b"existing driver")
        self.assertEqual((self.home / "conf/nifi.properties").read_text(), "validation=true\n")
        installed = self.home / "drivers/validation-jdbc"
        self.assertEqual((installed / "drivers/validation.jar").read_bytes(), self.jar.read_bytes())
        mappings = (installed / "jdbc-driver-locations.conf").read_text()
        self.assertIn(str(installed / "drivers/validation.jar"), mappings)
        self.assertNotIn(".jdbc-stage-", mappings)

    def test_corrupt_source_is_rejected_before_any_install(self):
        self.jar.write_bytes(b"changed artifact")
        self.assertNotEqual(self.run_install().returncode, 0)
        self.assertEqual(list((self.home / "drivers").iterdir()), [self.existing])

    def test_installed_changes_are_not_silently_overwritten(self):
        self.assertEqual(self.run_install().returncode, 0)
        installed = self.home / "drivers/validation-jdbc/drivers/validation.jar"
        installed.write_bytes(b"operator change")
        self.assertNotEqual(self.run_install().returncode, 0)
        self.assertEqual(installed.read_bytes(), b"operator change")

    def test_manifest_paths_cannot_escape_and_bundle_id_is_one_segment(self):
        self.manifest["bundleId"] = "../escape"
        self.save()
        self.assertNotEqual(self.run_install().returncode, 0)
        self.manifest["bundleId"] = "validation-jdbc"
        self.manifest["files"]["../escape.jar"] = next(iter(self.manifest["files"].values()))
        self.save()
        self.assertNotEqual(self.run_install().returncode, 0)
        self.assertEqual(list((self.home / "drivers").iterdir()), [self.existing])


if __name__ == "__main__":
    unittest.main()

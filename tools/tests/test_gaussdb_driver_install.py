import importlib.util
import io
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('gauss_install', Path(__file__).parents[1] / 'install-gaussdb-drivers.py')
installer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(installer)


class GaussDbDriverInstallTest(unittest.TestCase):
    def test_maven_install_verifies_both_payloads_before_invocation_and_uses_vendor_coordinates(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive, approved, contents = self.fixture(root)
            jdbc = root / 'jdbc'
            installer.install(archive, jdbc, True, root / 'backup', approved)
            calls = []
            def run(command, **kwargs):
                calls.append(command)
                self.assertNotIn('-Dmaven.repo.local', ' '.join(command))
                self.assertEqual((root / 'reports').resolve(), kwargs['cwd'])
                return SimpleNamespace(returncode=0)
            with patch.object(installer, 'APPROVED', approved), patch.object(installer.shutil, 'which', return_value='mvn'):
                installed = installer.install_to_maven(jdbc, root / 'reports', run)
                self.assertEqual(2, len(calls))
                self.assertIn('com.huawei.gaussdb:gaussdbjdbc:' + installer.MAVEN_VERSION, installed)
                self.assertIn('org.opengauss:opengauss-jdbc:' + installer.MAVEN_VERSION, installed)
                (jdbc / 'opengaussjdbc.jar').write_bytes(b'unreviewed')
                with self.assertRaisesRegex(ValueError, 'reviewed attachment'):
                    installer.install_to_maven(jdbc, root / 'reports', run)
                self.assertEqual(2, len(calls))
                (jdbc / 'opengaussjdbc.jar').write_bytes(contents['opengaussjdbc.jar'])
                with self.assertRaisesRegex(RuntimeError, 'Maven vendor installation failed'):
                    installer.install_to_maven(jdbc, root / 'reports', lambda *a, **kw: SimpleNamespace(returncode=1))

    def fixture(self, root):
        archive = root / 'approved.zip'
        contents, approved = {}, {}
        for name, rule in installer.APPROVED.items():
            memory = io.BytesIO()
            with zipfile.ZipFile(memory, 'w') as jar:
                jar.writestr(rule['driver'].replace('.', '/') + '.class', b'validation-driver')
                jar.writestr('META-INF/services/java.sql.Driver', rule['driver'])
            contents[name] = memory.getvalue()
            approved[name] = {**rule, 'sha256': installer.digest(contents[name])}
        with zipfile.ZipFile(archive, 'w') as source:
            for name, data in contents.items():
                source.writestr('validation/' + name, data)
        return archive, approved, contents

    def test_default_plan_does_not_create_or_change_the_target(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive, approved, _ = self.fixture(root)
            result = installer.install(archive, root / 'jdbc', approved=approved)
            self.assertEqual('dry-run', result['status'])
            self.assertFalse((root / 'jdbc').exists())

    def test_replaces_exactly_two_drivers_preserves_other_products_and_backs_up(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive, approved, contents = self.fixture(root)
            jdbc, backups = root / 'jdbc', root / 'backup'
            jdbc.mkdir()
            for name in contents:
                (jdbc / name).write_bytes(b'previous-driver')
            (jdbc / 'opengauss-jdbc.jar').write_bytes(b'open-source-driver')
            (jdbc / 'postgresql.jar').write_bytes(b'postgres-driver')
            result = installer.install(archive, jdbc, True, backups, approved)
            self.assertEqual('installed-and-verified', result['status'])
            for name, data in contents.items():
                self.assertEqual(data, (jdbc / name).read_bytes())
            self.assertEqual(b'open-source-driver', (jdbc / 'opengauss-jdbc.jar').read_bytes())
            self.assertEqual(b'postgres-driver', (jdbc / 'postgresql.jar').read_bytes())
            self.assertEqual(2, len(list(backups.iterdir())))

    def test_unapproved_checksum_or_active_directory_backup_is_rejected_before_replacement(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive, approved, _ = self.fixture(root)
            jdbc = root / 'jdbc'
            jdbc.mkdir()
            (jdbc / 'gaussdbjdbc.jar').write_bytes(b'unchanged')
            with self.assertRaises(ValueError):
                installer.install(archive, jdbc, True, root / 'backup')
            with self.assertRaises(ValueError):
                installer.install(archive, jdbc, True, jdbc / 'backup', approved)
            self.assertEqual(b'unchanged', (jdbc / 'gaussdbjdbc.jar').read_bytes())

    def test_second_write_failure_restores_first_driver(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            archive, approved, contents = self.fixture(root)
            jdbc = root / 'jdbc'
            jdbc.mkdir()
            for name in contents:
                (jdbc / name).write_bytes(b'original')
            original_write = installer.atomic_write
            def fail_second(path, data):
                if path.name == 'opengaussjdbc.jar' and data != b'original':
                    raise OSError('validation write failure')
                original_write(path, data)
            with patch.object(installer, 'atomic_write', side_effect=fail_second):
                with self.assertRaises(OSError):
                    installer.install(archive, jdbc, True, root / 'backup', approved)
            self.assertTrue(all((jdbc / name).read_bytes() == b'original' for name in contents))


if __name__ == '__main__':
    unittest.main()

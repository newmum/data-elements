import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location('gauss_package', Path(__file__).parents[1] / 'package-backend-with-gaussdb.py')
packager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packager)


class GaussDbBackendPackageTest(unittest.TestCase):
    def fixture(self, root):
        approved, payload = {}, {}
        for name, rule in packager.installer.APPROVED.items():
            content = io.BytesIO()
            with zipfile.ZipFile(content, 'w') as jar:
                jar.writestr(rule['driver'].replace('.', '/') + '.class', b'validation-driver')
                jar.writestr('META-INF/services/java.sql.Driver', rule['driver'])
            payload[name] = content.getvalue()
            approved[name] = {**rule, 'sha256': packager.installer.digest(payload[name])}
        archive = root / 'drivers.zip'
        with zipfile.ZipFile(archive, 'w') as z:
            for name, data in payload.items():
                z.writestr(name, data)
        base = root / 'backend.jar'
        with zipfile.ZipFile(base, 'w') as z:
            z.writestr('org/springframework/boot/loader/launch/JarLauncher.class', b'validation-loader')
            z.writestr('BOOT-INF/classes/Existing.class', b'keep-application')
            z.writestr('BOOT-INF/classes/application.yml', b'keep-config')
        return base, archive, approved, payload

    def test_native_pair_is_loadable_as_boot_libraries_and_application_content_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, archive, approved, payload = self.fixture(root)
            output = root / 'release.jar'
            result = packager.package(base, archive, output, approved)
            self.assertTrue(result['allEntryContentsVerified'])
            with zipfile.ZipFile(output) as z:
                index = z.read('BOOT-INF/classpath.idx').decode('utf-8')
                for name, data in payload.items():
                    entry = 'BOOT-INF/lib/' + name
                    self.assertEqual(data, z.read(entry))
                    self.assertEqual(zipfile.ZIP_STORED, z.getinfo(entry).compress_type)
                    self.assertIn('- "' + entry + '"', index)
                self.assertEqual(b'keep-application', z.read('BOOT-INF/classes/Existing.class'))
                self.assertEqual(b'keep-config', z.read('BOOT-INF/classes/application.yml'))
            with self.assertRaises(ValueError):
                packager.package(base, archive, base, approved)
            with self.assertRaises(ValueError):
                packager.package(base, archive, output, approved)

    def test_an_unreviewed_payload_or_a_shadow_driver_is_rejected_before_output(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, archive, approved, payload = self.fixture(root)
            output = root / 'release.jar'
            with self.assertRaises(ValueError):
                packager.package(base, archive, output)
            with zipfile.ZipFile(base, 'a') as z:
                z.writestr('BOOT-INF/lib/old-renamed-driver.jar', payload['gaussdbjdbc.jar'] + b'unreviewed')
            with self.assertRaisesRegex(ValueError, 'shadow'):
                packager.package(base, archive, output, approved)
            self.assertFalse(output.exists())

    def test_maven_versioned_libraries_are_retained_without_duplicate_driver_classes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            base, archive, approved, payload = self.fixture(root)
            with zipfile.ZipFile(base, 'a') as z:
                for filename, data in payload.items():
                    z.writestr('BOOT-INF/lib/' + filename[:-4] + '-vendor-version.jar', data)
            output = root / 'release.jar'
            packager.package(base, archive, output, approved)
            with zipfile.ZipFile(output) as z:
                self.assertEqual(2, len([name for name in z.namelist() if name.startswith('BOOT-INF/lib/')]))
                for filename, data in payload.items():
                    self.assertEqual(data, z.read('BOOT-INF/lib/' + filename[:-4] + '-vendor-version.jar'))


if __name__ == '__main__':
    unittest.main()

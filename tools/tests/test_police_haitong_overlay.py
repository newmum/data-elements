"""Safety checks for applying the HaiTong frontend delta to an existing deployment."""
import ast
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest import mock

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('haitong_overlay_installer', ROOT / 'tools/police-deployment/mysql-haitong/install.py')
installer = importlib.util.module_from_spec(spec); spec.loader.exec_module(installer)


class HaiTongOverlayTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(); self.addCleanup(self.temporary.cleanup)
        self.base = Path(self.temporary.name)
        self.target = self.base / 'platform'; self.package = self.base / 'update'
        self.original = {'config/Caddyfile': b'original gateway', 'www/static/centers.js': b'original centers'}
        payload = {'config/Caddyfile': b'haitong gateway', 'www/static/centers.js': b'prefixed centers',
                   'www-haitong/index.html': b'new haitong', 'tools/standalone.py': b'external only'}
        for name, value in dict(self.original, **{'backend/data-element.jar': b'untouched jar',
                                                 'www/index.html': b'untouched source platform',
                                                 'config/external-services.json': b'validation private config',
                                                 'data/local/credentials.json': b'validation existing identity'}).items():
            path = self.target / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(value)
        for name, value in payload.items():
            path = self.package / 'payload' / name; path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(value)
        manifest = {'expectedOriginals': {n: hashlib.sha256(v).hexdigest() for n, v in self.original.items()},
                    'files': {n: hashlib.sha256(v).hexdigest() for n, v in payload.items()}}
        (self.package / 'manifest.json').write_text(json.dumps(manifest))

    def test_install_preserves_existing_jar_credentials_and_private_connections_and_can_reapply(self):
        preserved = {n: (self.target / n).read_bytes() for n in ('backend/data-element.jar',
                     'www/index.html', 'config/external-services.json', 'data/local/credentials.json')}
        with mock.patch.object(installer, 'stop_platform') as stop:
            installer.install(self.target, self.package)
            installer.install(self.target, self.package)
            self.assertEqual(2, stop.call_count)
        for name, content in preserved.items(): self.assertEqual(content, (self.target / name).read_bytes())
        self.assertEqual(b'new haitong', (self.target / 'www-haitong/index.html').read_bytes())
        self.assertEqual(2, len(list((self.target / 'logs').glob('haitong-overlay-backup-*'))))

    def test_custom_gateway_is_rejected_before_stop_or_write(self):
        (self.target / 'config/Caddyfile').write_bytes(b'custom production gateway')
        with mock.patch.object(installer, 'stop_platform') as stop:
            with self.assertRaisesRegex(ValueError, '自定义'):
                installer.install(self.target, self.package)
            stop.assert_not_called()
        self.assertFalse((self.target / 'www-haitong').exists())

    def test_tampered_payload_is_rejected_before_stop_or_write(self):
        (self.package / 'payload/www-haitong/index.html').write_bytes(b'tampered')
        with mock.patch.object(installer, 'stop_platform') as stop:
            with self.assertRaisesRegex(ValueError, '校验失败'):
                installer.install(self.target, self.package)
            stop.assert_not_called()
        self.assertEqual(self.original['config/Caddyfile'], (self.target / 'config/Caddyfile').read_bytes())

    def test_copy_failure_restores_old_gateway_and_navigation(self):
        original_copy = installer.shutil.copyfile
        failed = False
        def copy(source, target, *args, **kwargs):
            nonlocal failed
            if str(target).endswith('www-haitong\\index.html') or str(target).endswith('www-haitong/index.html'):
                if not failed:
                    failed = True
                    raise OSError('validation disk failure')
            return original_copy(source, target, *args, **kwargs)
        with mock.patch.object(installer, 'stop_platform'), mock.patch.object(installer.shutil, 'copyfile', side_effect=copy):
            with self.assertRaises(OSError): installer.install(self.target, self.package)
        for name, data in self.original.items(): self.assertEqual(data, (self.target / name).read_bytes())
        self.assertFalse((self.target / 'www-haitong/index.html').exists())

    def test_linux_installer_keeps_python36_compatibility(self):
        ast.parse((ROOT / 'tools/police-deployment/mysql-haitong/install.py').read_text(encoding='utf8'), feature_version=(3, 6))

    def test_resource_conflict_is_rejected_before_stopping_the_platform(self):
        with mock.patch.object(installer, 'prepare_component', side_effect=RuntimeError('validation concurrent edit')), \
                mock.patch.object(installer, 'stop_platform') as stop:
            with self.assertRaises(RuntimeError): installer.install(self.target, self.package)
            stop.assert_not_called()
        self.assertEqual(self.original['config/Caddyfile'], (self.target / 'config/Caddyfile').read_bytes())

    def test_component_failure_restores_component_and_files_as_one_selected_update(self):
        actions = []
        def invoke(package, context, action):
            actions.append(action)
            if action == 'apply': raise RuntimeError('validation database failure')
        with mock.patch.object(installer, 'prepare_component', return_value='validation context'), \
                mock.patch.object(installer, 'stop_platform'), mock.patch.object(installer, 'run_component', side_effect=invoke):
            with self.assertRaises(RuntimeError): installer.install(self.target, self.package)
        self.assertEqual(['apply', 'restore'], actions)
        for name, content in self.original.items(): self.assertEqual(content, (self.target / name).read_bytes())
        self.assertFalse((self.target / 'www-haitong/index.html').exists())

    def test_preceding_haitong_overlay_is_an_explicitly_accepted_upgrade_source(self):
        alternative = b'previous reviewed haitong centers'
        path = self.package / 'manifest.json'; data = json.loads(path.read_text())
        data['acceptedOriginals'] = {'www/static/centers.js': [hashlib.sha256(alternative).hexdigest()]}
        path.write_text(json.dumps(data)); (self.target / 'www/static/centers.js').write_bytes(alternative)
        with mock.patch.object(installer, 'stop_platform'):
            installer.install(self.target, self.package)
        self.assertEqual(b'prefixed centers', (self.target / 'www/static/centers.js').read_bytes())


if __name__ == '__main__': unittest.main()

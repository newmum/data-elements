"""Verify the external overlay never manages middleware or changes existing identity/data."""
import ast
import base64
import copy
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    with mock.patch.dict(sys.modules, {'fcntl': SimpleNamespace(flock=lambda *a: None, LOCK_EX=1),
                                       'pwd': SimpleNamespace()}):
        spec.loader.exec_module(module)
    return module


launcher = load('police_external_launcher', ROOT / 'tools/police-deployment/mysql-external/standalone.py')
builder = load('police_external_builder', ROOT / 'tools/package-police-external-overlay.py')


class ExternalOverlayTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        for folder in ('config', 'data/local', 'backend', 'logs'):
            (self.root / folder).mkdir(parents=True, exist_ok=True)
        self.key = base64.b64encode(bytes(range(32))).decode()
        self.credentials = self.root / 'data/local/credentials.json'
        self.credentials.write_text(json.dumps({'transport': self.key, 'mysql': 'legacy-unused'}))
        self.data = {
            'control': {'jdbcUrl': 'jdbc:mysql://control.internal:3307/baseline?useSSL=true',
                        'username': 'control-user', 'password': 'validation-control'},
            'tenant': {'jdbcUrl': 'jdbc:mysql://tenant.internal:3308/police',
                       'username': 'tenant-user', 'password': 'validation-tenant'},
            'redis': {'host': 'redis.internal', 'port': 6379, 'database': 2,
                      'username': '', 'password': 'validation-cache'},
            'elasticsearch': {'hosts': ['es1.internal:9200', 'es2.internal:9201'],
                              'username': 'search-user', 'password': 'validation-search'},
            'identity': {'transportKey': ''},
        }
        self.ports = {'web': 18291, 'backend': 18288, 'webBind': '127.0.0.1',
                      'mysql': 13316, 'redis': 18279, 'search': 19210}
        for key, value in [('ROOT', self.root), ('STATE', self.root / 'data/local'),
                           ('LOGS', self.root / 'logs'), ('RUNTIME', self.root / 'runtime')]:
            patch = mock.patch.object(launcher, key, value)
            patch.start()
            self.addCleanup(patch.stop)

    def write_config(self, data=None):
        (self.root / 'config/external-services.json').write_text(json.dumps(data or self.data))

    def test_external_addresses_and_separate_credentials_override_all_legacy_bindings(self):
        self.write_config()
        data, endpoints = launcher.configuration()
        self.assertEqual([('control', 'control.internal', 3307), ('tenant', 'tenant.internal', 3308),
                          ('redis', 'redis.internal', 6379), ('elasticsearch', 'es1.internal', 9200),
                          ('elasticsearch', 'es2.internal', 9201)], endpoints)
        with mock.patch.dict(launcher.os.environ, {'SPRING_APPLICATION_JSON': '{"spring.datasource.url":"wrong"}',
                                                  'IDAAS_TRANSPORT_KEY': 'wrong', 'JAVA_TOOL_OPTIONS': '-Dbad=true'}):
            env = launcher.environment(data, self.ports)
        actual = json.loads(env['SPRING_APPLICATION_JSON'])
        self.assertEqual(data['control']['jdbcUrl'], actual['spring.datasource.url'])
        prefix = 'data-element.tenant.database.data-sources.police'
        self.assertEqual('tenant-user', actual[prefix + '.username'])
        self.assertEqual('validation-tenant', actual[prefix + '.password'])
        self.assertEqual(2, actual['spring.redis.database'])
        self.assertEqual('es1.internal:9200,es2.internal:9201', actual['elasticsearch.host'])
        self.assertEqual(['http://es1.internal:9200', 'http://es2.internal:9201'], actual['spring.elasticsearch.uris'])
        self.assertNotIn('JAVA_TOOL_OPTIONS', env)
        self.assertFalse(actual['spring.cloud.nacos.config.enabled'])
        self.assertEqual(self.key, env['IDAAS_TRANSPORT_KEY'])
        self.assertNotIn('IDAAS_TRANSPORT_KEY', launcher.clean_web_environment(self.ports))

    def test_legacy_identity_is_read_without_mutation_and_never_generated(self):
        before = self.credentials.read_bytes()
        self.assertEqual(self.key, launcher.transport_key(self.data))
        self.assertEqual(before, self.credentials.read_bytes())
        self.credentials.unlink()
        with self.assertRaisesRegex(ValueError, '不会生成新密钥'):
            launcher.transport_key(self.data)
        self.assertFalse(self.credentials.exists())

    def test_bad_connections_are_rejected_without_process_or_database_operations(self):
        bad = []
        for key, value in [('hosts', ['https://es.internal:9200']), ('hosts', ['es.internal:0']),
                           ('hosts', ['[::1]:9200']), ('username', '')]:
            data = copy.deepcopy(self.data); data['elasticsearch'][key] = value; bad.append(data)
        data = copy.deepcopy(self.data); data['tenant'] = copy.deepcopy(data['control']); bad.append(data)
        data = copy.deepcopy(self.data); data['control']['jdbcUrl'] += '&password=validation-secret'; bad.append(data)
        data = copy.deepcopy(self.data); data['redis']['port'] = True; bad.append(data)
        data = copy.deepcopy(self.data); data['redis']['username'] = 'named-acl-user'; bad.append(data)
        with mock.patch.object(launcher.subprocess, 'Popen') as popen, mock.patch.object(launcher.subprocess, 'run') as run:
            for data in bad:
                self.write_config(data)
                with self.assertRaises(ValueError):
                    launcher.configuration()
            popen.assert_not_called(); run.assert_not_called()

    def test_stop_and_status_work_with_no_external_config_and_never_manage_middleware(self):
        (self.root / 'backend/data-element.jar').touch()
        with mock.patch.object(launcher.platform, 'system', return_value='Linux'), \
                mock.patch.object(launcher.platform, 'machine', return_value='x86_64'), \
                mock.patch.object(launcher, 'service_user'), mock.patch.object(launcher.os, 'chdir'), \
                mock.patch.object(launcher.os, 'umask'), mock.patch.object(launcher, 'configuration') as config, \
                mock.patch.object(launcher, 'stop') as stop, mock.patch.object(launcher, 'pid', return_value=None) as pid:
            with mock.patch.object(sys, 'argv', ['standalone.py', 'stop']):
                launcher.main()
            self.assertEqual([mock.call('web'), mock.call('backend')], stop.call_args_list)
            with mock.patch.object(sys, 'argv', ['standalone.py', 'status']):
                launcher.main()
            self.assertEqual([mock.call('web'), mock.call('backend')], pid.call_args_list)
            config.assert_not_called()

    def test_start_and_failure_cleanup_are_limited_to_platform(self):
        for failing in (False, True):
            with mock.patch.object(launcher, 'pid', return_value=None), mock.patch.object(launcher, 'provision'), \
                    mock.patch.object(launcher.socket, 'socket'), mock.patch.object(launcher, 'spawn') as spawn, \
                    mock.patch.object(launcher, 'stop') as stop, mock.patch.object(launcher, 'wait') as wait:
                if failing:
                    wait.side_effect = RuntimeError('validation backend failure')
                    with self.assertRaises(RuntimeError):
                        launcher.start(self.data, self.ports)
                    self.assertEqual([mock.call('web'), mock.call('backend')], stop.call_args_list)
                    self.assertEqual(['backend'], [c.args[0] for c in spawn.call_args_list])
                else:
                    launcher.start(self.data, self.ports)
                    self.assertEqual(['backend', 'web'], [c.args[0] for c in spawn.call_args_list])
                    stop.assert_not_called()
                for call in spawn.call_args_list:
                    self.assertNotIn('validation-control', ' '.join(call.args[1]))

    def test_existing_backend_requires_restart_and_is_not_implicitly_reused(self):
        with mock.patch.object(launcher, 'pid', side_effect=lambda name: 123 if name == 'backend' else None), \
                mock.patch.object(launcher, 'spawn') as spawn, mock.patch.object(launcher, 'stop') as stop:
            with self.assertRaisesRegex(RuntimeError, '先用新版'):
                launcher.start(self.data, self.ports)
            spawn.assert_not_called(); stop.assert_not_called()

    def test_provision_extracts_only_java_and_gateway(self):
        def extract(command, **kwargs):
            folder = Path(command[command.index('-C') + 1])
            binary = folder / ('bin/java' if folder.name == 'java' else 'caddy')
            binary.parent.mkdir(parents=True, exist_ok=True)
            binary.write_bytes(b'\x7fELF\x02\x01' + bytes(12) + (62).to_bytes(2, 'little'))
        with mock.patch.object(launcher.subprocess, 'run', side_effect=extract) as run:
            launcher.provision()
        self.assertEqual(['java21.tar.gz', 'caddy.tar.gz'], [Path(c.args[0][2]).name for c in run.call_args_list])
        for name in ('mysql', 'redis', 'elasticsearch'):
            self.assertFalse((self.root / 'runtime' / name).exists())

    def test_read_only_connection_check_performs_no_import_and_redacts_failures(self):
        self.write_config()
        _, endpoints = launcher.configuration()
        with mock.patch.object(launcher.socket, 'create_connection') as connect:
            launcher.check_connections(endpoints)
            self.assertEqual(5, connect.call_count)
        with mock.patch.object(launcher.socket, 'create_connection', side_effect=OSError('validation secret')):
            with self.assertRaises(RuntimeError) as caught:
                launcher.check_connections(endpoints)
            self.assertNotIn('validation secret', str(caught.exception))

    def test_overlay_has_no_binary_or_live_config_and_preserves_legacy_files(self):
        output = self.root / 'overlay.zip'
        report = builder.build(output)
        with ZipFile(output) as archive:
            names = archive.namelist()
            self.assertEqual(8, len(names))
            for name in ('config/ports.json', 'config/application.yml', 'config/Caddyfile',
                         'config/external-services.json', 'data/local/credentials.json'):
                self.assertNotIn(name, names)
            self.assertFalse(any(name.startswith(('backend/', 'vendor/', 'db/', 'www/', 'data/')) for name in names))
            for name in ('start.sh', 'stop.sh', 'status.sh', 'check.sh'):
                self.assertNotIn(b'\r', archive.read(name))
                self.assertEqual(0o755, (archive.getinfo(name).external_attr >> 16) & 0o777)
        self.assertLess(report['bytes'], 30000)
        source = (ROOT / 'tools/police-deployment/mysql-external/standalone.py').read_text(encoding='utf8')
        ast.parse(source, feature_version=(3, 6))


if __name__ == '__main__':
    unittest.main()

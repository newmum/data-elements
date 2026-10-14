"""Exercise the real launcher's environment selection without starting a JVM."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = (ROOT / 'start-data-elements.ps1').read_text(encoding='utf-8-sig')
PWSH = shutil.which('pwsh')


@unittest.skipUnless(PWSH, 'PowerShell 7 is required to execute the Windows launcher contract')
class LocalLauncherTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='data-elements-launcher-')
        self.addCleanup(self.directory.cleanup)
        self.folder = Path(self.directory.name)
        self.config = self.folder / 'runtime.json'
        self.fixture = {
            'executable': 'fixture-java', 'port': 18088,
            'environment': {
                'NACOS_HOST': '203.0.113.10', 'NACOS_PORT': '8848',
                'NACOS_NAMESPACE': 'fixture', 'DATA_ELEMENT_PROFILE': 'dev',
                'NACOS_CONFIG_IMPORTS': 'nacos:complete.yml?group=DEFAULT_GROUP',
                'SPRING_APPLICATION_JSON': '{"fixture":{"enabled":true}}',
            },
        }
        # Stop immediately after environment selection. It is the exact production
        # prefix, before any key lookup, process inspection, JAR copying or launch.
        prefix = SOURCE.split('# Provisioning encryption key stays outside', 1)[0]
        prefix = prefix.replace('$PSScriptRoot', "'" + ROOT.as_posix().replace("'", "''") + "'")
        self.probe = self.folder / 'probe.ps1'
        self.probe.write_text(prefix + '''
@{hostName=$env:NACOS_HOST; nacosPort=$env:NACOS_PORT; profile=$env:DATA_ELEMENT_PROFILE;
  imports=$env:NACOS_CONFIG_IMPORTS; runtimeJson=$env:SPRING_APPLICATION_JSON;
  javaPath=$JavaExe; port=$Port; privateSelected=$usePrivateRuntime} | ConvertTo-Json -Compress
''', encoding='utf-8')

    def run_probe(self, *arguments):
        self.config.write_text(json.dumps(self.fixture), encoding='utf-8')
        environment = {key: value for key, value in os.environ.items()
                       if not key.startswith(('NACOS_', 'DATA_ELEMENT_', 'DATA_ELEMENTS_', 'SPRING_'))}
        environment.update(NACOS_HOST='stale.invalid', NACOS_CONFIG_IMPORTS='nacos:stale.yml',
                           SPRING_APPLICATION_JSON='{"stale":true}')
        return subprocess.run([PWSH, '-NoProfile', '-File', str(self.probe), '-ConfigPath',
                               str(self.config), *arguments], env=environment,
                              capture_output=True, text=True, encoding='utf-8', timeout=15)

    def test_private_environment_replaces_stale_process_settings(self):
        result = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        actual = json.loads(result.stdout)
        self.assertEqual('203.0.113.10', actual['hostName'])
        self.assertEqual('nacos:complete.yml?group=DEFAULT_GROUP', actual['imports'])
        self.assertEqual('{"fixture":{"enabled":true}}', actual['runtimeJson'])
        self.assertEqual('fixture-java', actual['javaPath'])
        self.assertEqual(18088, actual['port'])
        self.assertTrue(actual['privateSelected'])

    def test_explicit_host_does_not_load_another_environments_private_settings(self):
        self.fixture['environment']['UNEXPECTED_KEY'] = 'must-not-load'
        result = self.run_probe('-NacosHost', '203.0.113.11')
        self.assertEqual(0, result.returncode, result.stderr)
        actual = json.loads(result.stdout)
        self.assertEqual('203.0.113.11', actual['hostName'])
        self.assertFalse(actual['privateSelected'])

    def test_missing_config_fails_before_any_java_start(self):
        result = subprocess.run([PWSH, '-NoProfile', '-File', str(self.probe), '-ConfigPath',
                                 str(self.folder / 'missing.json')], capture_output=True,
                                text=True, encoding='utf-8', timeout=15)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Private runtime configuration is missing', result.stderr)

    def test_workspace_private_config_is_rejected(self):
        result = subprocess.run([PWSH, '-NoProfile', '-File', str(self.probe), '-ConfigPath',
                                 str(ROOT / 'runtime.private.json')], capture_output=True,
                                text=True, encoding='utf-8', timeout=15)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('outside the Git workspace', result.stderr)

    def test_unknown_environment_key_is_rejected(self):
        self.fixture['environment']['PATH'] = 'unexpected'
        result = self.run_probe()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Unexpected runtime environment variable name', result.stderr)

    def test_configuration_import_is_required(self):
        del self.fixture['environment']['NACOS_CONFIG_IMPORTS']
        result = self.run_probe()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('configuration imports', result.stderr)

    def test_explicit_runtime_options_are_preserved(self):
        result = self.run_probe('-Port', '18089', '-NacosPort', '18848', '-Profile', 'test')
        self.assertEqual(0, result.returncode, result.stderr)
        actual = json.loads(result.stdout)
        self.assertEqual(18089, actual['port'])
        self.assertEqual('18848', actual['nacosPort'])
        self.assertEqual('test', actual['profile'])


if __name__ == '__main__':
    unittest.main()

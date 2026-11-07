"""Exercise root launcher selection and readiness in an isolated workspace."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PWSH = shutil.which('pwsh')


@unittest.skipUnless(PWSH, 'PowerShell 7 is required to execute the Windows launcher contract')
class LocalLauncherTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix='data-elements-launcher-')
        self.addCleanup(directory.cleanup)
        folder = Path(directory.name)
        self.workspace = folder / 'workspace'
        (self.workspace / 'tools').mkdir(parents=True)
        self.profile = folder / 'profile'
        self.config = folder / 'runtime.json'
        self.delegation = self.workspace / 'tools' / 'delegation.json'

        source = (ROOT / 'start-data-elements.ps1').read_text(encoding='utf-8-sig')
        prefix, boundary, _ = source.partition('# Provisioning encryption key stays outside')
        self.assertTrue(boundary, 'The probe must stop before key lookup or process launch')
        self.probe = self.workspace / 'start-data-elements.ps1'
        self.probe.write_text(prefix + '''
@{hostName=$env:NACOS_HOST; nacosPort=$env:NACOS_PORT; profile=$env:DATA_ELEMENT_PROFILE;
  imports=$env:NACOS_CONFIG_IMPORTS; kafka=$env:KAFKA_BOOTSTRAP_SERVERS;
  port=$Port; privateSelected=$usesPrivateRuntime} | ConvertTo-Json -Compress
''', encoding='utf-8')

        # The stub replaces the private launcher only inside this disposable workspace.
        # Process inspection, the health request and waiting are intercepted in this child shell.
        (self.workspace / 'tools' / 'start-local-backend.ps1').write_text('''
param([string]$ConfigPath, [switch]$Restart)
function global:Get-Process {
    param([int]$Id, [string]$ErrorAction)
    if ($Id -ne 54321) { throw 'Unexpected process' }
    @{Id=$Id}
}
function global:Invoke-RestMethod {
    param([string]$Uri, [int]$TimeoutSec)
    if ($Uri -ne 'http://localhost:18088/sym/tenant/login-options') {
        throw 'Unexpected readiness endpoint'
    }
    @{code=0}
}
function global:Wait-Process {
    param([int]$Id, [string]$ErrorAction)
    @{waitedFor=$Id} | ConvertTo-Json -Compress
}
@{configPath=$ConfigPath; restart=[bool]$Restart} | ConvertTo-Json -Compress |
    Set-Content -LiteralPath (Join-Path $PSScriptRoot 'delegation.json') -Encoding utf8
@{pid=54321; port=18088} | ConvertTo-Json -Compress
''', encoding='utf-8')

    def run_probe(self, *arguments):
        environment = {key: value for key, value in os.environ.items()
                       if not key.startswith(('NACOS_', 'DATA_ELEMENT_', 'DATA_ELEMENTS_'))}
        environment.update(USERPROFILE=str(self.profile), NACOS_HOST='stale.invalid',
                           NACOS_CONFIG_IMPORTS='nacos:stale.yml', KAFKA_BOOTSTRAP_SERVERS='stale:9092')
        result = subprocess.run([PWSH, '-NoProfile', '-File', str(self.probe), '-NoLiveLog',
                                 '-StartupTimeoutSeconds', '3', *arguments], env=environment,
                                capture_output=True, text=True, encoding='utf-8', timeout=15)
        self.assertEqual(0, result.returncode, result.stderr)
        return json.loads(result.stdout.splitlines()[-1])

    def assert_delegated(self, path, restart, *arguments):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('{}', encoding='utf-8')
        self.assertEqual({'waitedFor': 54321}, self.run_probe(*arguments))
        actual = json.loads(self.delegation.read_text(encoding='utf-8-sig'))
        self.assertEqual(path, Path(actual['configPath']))
        self.assertEqual(restart, actual['restart'])

    def test_default_private_configuration_and_restart_are_forwarded(self):
        default = self.profile / '.codex/private/data-elements/local-runtime.private.json'
        self.assert_delegated(default, True, '-Restart')

    def test_explicit_private_configuration_is_forwarded(self):
        self.assert_delegated(self.config, False, '-RuntimeConfigPath', str(self.config))

    def test_missing_private_configuration_uses_configurable_launcher(self):
        actual = self.run_probe('-RuntimeConfigPath', str(self.config))
        self.assertFalse(self.delegation.exists())
        self.assertFalse(actual['privateSelected'])
        self.assertNotEqual('stale.invalid', actual['hostName'])
        self.assertIn('nacos:data-element-dev.yml', actual['imports'])

    def test_explicit_host_does_not_select_existing_private_configuration(self):
        self.config.write_text('{}', encoding='utf-8')
        actual = self.run_probe('-RuntimeConfigPath', str(self.config), '-NacosHost', '203.0.113.11')
        self.assertFalse(self.delegation.exists())
        self.assertFalse(actual['privateSelected'])
        self.assertEqual('203.0.113.11', actual['hostName'])

    def test_explicit_runtime_options_and_imports_are_preserved(self):
        actual = self.run_probe('-Port', '18089', '-NacosPort', '18848', '-Profile', 'test',
                                '-NacosConfigImports', 'nacos:complete.yml?group=DEFAULT_GROUP')
        self.assertEqual(18089, actual['port'])
        self.assertEqual('18848', actual['nacosPort'])
        self.assertEqual('test', actual['profile'])
        self.assertEqual('nacos:complete.yml?group=DEFAULT_GROUP', actual['imports'])

    def test_configurable_kafka_setting_does_not_inherit_stale_value(self):
        self.assertIsNone(self.run_probe()['kafka'])
        actual = self.run_probe('-KafkaBootstrapServers', 'fixture:9092')
        self.assertEqual('fixture:9092', actual['kafka'])


if __name__ == '__main__':
    unittest.main()

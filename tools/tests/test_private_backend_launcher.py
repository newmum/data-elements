"""Run the private launcher with process stubs; never start or stop a real JVM."""
import base64
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PWSH = shutil.which('pwsh')


@unittest.skipUnless(PWSH, 'PowerShell 7 is required')
class PrivateBackendLauncherTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory(prefix='private-backend-launcher-')
        self.addCleanup(self.directory.cleanup)
        self.folder = Path(self.directory.name).resolve()
        self.repo = self.folder / 'workspace'
        (self.repo / 'tools').mkdir(parents=True)
        (self.repo / '.runtime').mkdir()
        self.launcher = self.repo / 'tools/start-local-backend.ps1'
        shutil.copyfile(ROOT / 'tools/start-local-backend.ps1', self.launcher)
        (self.repo / '.runtime/data-element-runtime.jar').touch()
        self.java = self.folder / 'fixture-java.exe'
        self.java.touch()
        self.key = base64.b64encode(bytes(range(32))).decode('ascii')
        self.keyfile = self.folder / 'transport.key'
        self.keyfile.write_text(self.key + '\n', encoding='utf-8')
        self.config = self.folder / 'runtime.json'
        self.fixture = {
            'executable': str(self.java), 'port': 18088,
            'argumentLine': '-jar "' + str(self.repo / '.runtime/data-element-runtime.jar') + '"',
            'environment': {'NACOS_HOST': '203.0.113.10'},
            'transportKeyFile': str(self.keyfile),
        }
        self.probe = self.folder / 'probe.ps1'
        self.probe.write_text(r'''
param([string]$Launcher, [string]$Config, [string]$ExpectedKey, [string]$EventFile, [string]$ExpectedLocalHttp)
$ErrorActionPreference = 'Stop'
function Get-NetTCPConnection { [pscustomobject]@{OwningProcess=12345} }
function Get-CimInstance {
    $cfg = Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json
    [pscustomobject]@{CommandLine=$cfg.argumentLine; ExecutablePath=$cfg.executable}
}
function Stop-Process { Add-Content -LiteralPath $EventFile -Value 'stop' }
function Wait-Process { }
function Start-Process {
    if ($env:IDAAS_TRANSPORT_KEY -cne $ExpectedKey) { throw 'Wrong child environment' }
    if ($env:NACOS_HOST -ne '203.0.113.10') { throw 'Runtime environment was lost' }
    if ([string]$env:IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP -cne $ExpectedLocalHttp) { throw 'Wrong HTTP development setting' }
    Add-Content -LiteralPath $EventFile -Value 'start'
    [pscustomobject]@{Id=54321}
}
& $Launcher -ConfigPath $Config -Restart
''', encoding='utf-8')

    def run_probe(self, inherited_key=None):
        self.config.write_text(json.dumps(self.fixture), encoding='utf-8')
        environment = {key: value for key, value in os.environ.items()
                       if key not in {'IDAAS_TRANSPORT_KEY', 'IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP'}}
        if inherited_key is not None:
            environment['IDAAS_TRANSPORT_KEY'] = inherited_key
        events = self.folder / 'events.txt'
        result = subprocess.run([PWSH, '-NoProfile', '-File', str(self.probe),
                                 '-Launcher', str(self.launcher), '-Config', str(self.config),
                                 '-ExpectedKey', self.key, '-EventFile', str(events),
                                 '-ExpectedLocalHttp', self.fixture['environment'].get('IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP', '')],
                                env=environment, capture_output=True, text=True,
                                encoding='utf-8', timeout=15)
        self.assertNotIn(self.key, result.stdout + result.stderr)
        return result, events.read_text(encoding='utf-8-sig').splitlines() if events.exists() else []

    def test_persistent_key_is_inherited_by_child_and_not_replaced(self):
        before = self.keyfile.read_bytes()
        result, events = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(['stop', 'start'], events)
        self.assertEqual(before, self.keyfile.read_bytes())

    def test_explicit_environment_key_takes_precedence(self):
        self.keyfile.write_text('invalid unused fallback', encoding='utf-8')
        result, events = self.run_probe(self.key)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(['stop', 'start'], events)

    def test_private_config_can_supply_key(self):
        self.fixture['environment']['IDAAS_TRANSPORT_KEY'] = self.key
        self.keyfile.unlink()
        result, events = self.run_probe()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(['stop', 'start'], events)

    def test_explicit_local_http_setting_reaches_backend(self):
        for value in ('true', 'false'):
            with self.subTest(setting=value):
                self.fixture['environment']['IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP'] = value
                result, events = self.run_probe()
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(['stop', 'start'], events[-2:])

    def test_invalid_local_http_setting_keeps_running_backend(self):
        self.fixture['environment']['IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP'] = 'sometimes'
        result, events = self.run_probe()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('IDAAS_TRANSPORT_ALLOW_LOCAL_HTTP must be true or false', result.stderr)
        self.assertEqual([], events)

    def test_default_persistent_key_path_matches_root_launcher(self):
        del self.fixture['transportKeyFile']
        default = self.folder / 'profile/.codex/private/idaas-provision/transport-master.key'
        default.parent.mkdir(parents=True)
        default.write_text(self.key, encoding='utf-8')
        self.fixture['environment']['DATA_ELEMENTS_FIXTURE'] = 'test'
        # USERPROFILE is supplied by the test process, never by private config.
        previous = os.environ.get('USERPROFILE')
        try:
            os.environ['USERPROFILE'] = str(self.folder / 'profile')
            result, events = self.run_probe()
        finally:
            if previous is None:
                os.environ.pop('USERPROFILE', None)
            else:
                os.environ['USERPROFILE'] = previous
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(['stop', 'start'], events)

    def test_missing_key_keeps_running_backend(self):
        self.keyfile.unlink()
        result, events = self.run_probe()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('IDAAS transport key is missing', result.stderr)
        self.assertEqual([], events)
        self.assertFalse(self.keyfile.exists())

    def test_invalid_key_keeps_running_backend(self):
        for value in ('not base64!', base64.b64encode(b'too short').decode('ascii')):
            with self.subTest(kind='invalid encoding or length'):
                self.keyfile.write_text(value, encoding='utf-8')
                result, events = self.run_probe()
                self.assertNotEqual(0, result.returncode)
                self.assertIn('exactly 32 bytes', result.stderr)
                self.assertEqual([], events)

    def test_unknown_environment_variable_keeps_running_backend(self):
        self.fixture['environment']['PATH'] = 'unexpected'
        result, events = self.run_probe()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Unexpected runtime environment variable name', result.stderr)
        self.assertEqual([], events)

    def test_key_file_inside_workspace_is_rejected(self):
        self.fixture['transportKeyFile'] = str(self.repo / 'key.txt')
        result, events = self.run_probe()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('key must stay outside the Git workspace', result.stderr)
        self.assertEqual([], events)


if __name__ == '__main__':
    unittest.main()

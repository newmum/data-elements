"""Verify no-Nacos selection without starting a JVM or contacting external services."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
SHELL = shutil.which('pwsh') or shutil.which('powershell')


@unittest.skipUnless(SHELL, 'PowerShell is required for launcher verification')
class NoNacosLauncherTest(unittest.TestCase):
    def setUp(self):
        folder = tempfile.TemporaryDirectory(prefix='data-elements-no-nacos-')
        self.addCleanup(folder.cleanup)
        self.folder = Path(folder.name)
        self.workspace = self.folder / 'workspace with spaces'
        (self.workspace / 'tools').mkdir(parents=True)
        (self.workspace / 'db/nacos').mkdir(parents=True)
        for script in ('start-data-elements.ps1', 'tools/start-local-backend.ps1'):
            shutil.copyfile(ROOT / script, self.workspace / script)
        for name in ('chengtian-14.yml', 'data-element_ct.yml', 'application-no-nacos.yml'):
            shutil.copyfile(ROOT / 'db/nacos' / name, self.workspace / 'db/nacos' / name)
        self.java = self.folder / 'java.exe'
        self.java.write_bytes(b'validation-not-executed')
        self.jar = self.folder / 'data-element.jar'
        self.jar.write_bytes(b'validation-not-executed')
        self.runtime = self.folder / 'runtime.private.json'
        self.runtime.write_text(json.dumps({'executable': str(self.java), 'port': 8088, 'environment': {
            'NACOS_CONFIG_IMPORTS': 'nacos:stale.yml', 'NACOS_USER': 'stale-user', 'NACOS_PASSWORD': 'stale-secret',
            'SPRING_APPLICATION_JSON': json.dumps({'spring': {'cloud': {'nacos': {'config': {'enabled': True}}}}})}}), encoding='utf-8')
        self.environment = self.folder / 'environment.private.json'
        self.environment.write_text(json.dumps({'environment': {'NACOS_SPRING_DATASOURCE_PASSWORD': 'synthetic-business-secret'}}), encoding='utf-8')

    def invoke(self, *extra):
        env = dict(os.environ)
        env['NACOS_CONFIG_IMPORTS'] = 'nacos:another-stale.yml'
        local_environment = str(self.environment)
        if '-LocalEnvironmentPath' in extra:
            position = extra.index('-LocalEnvironmentPath')
            local_environment = extra[position + 1]
            extra = extra[:position] + extra[position + 2:]
        result = subprocess.run([SHELL, '-NoProfile', '-File', str(self.workspace / 'start-data-elements.ps1'),
                                 '-WithoutNacos', '-ValidateOnly', '-RuntimeConfigPath', str(self.runtime),
                                 '-LocalEnvironmentPath', local_environment, '-JarPath', str(self.jar),
                                 '-Port', '18099', *extra], env=env, capture_output=True, text=True, encoding='utf-8', timeout=20)
        return result

    def test_complete_local_config_selected_and_stale_remote_bootstrap_removed(self):
        result = self.invoke()
        self.assertEqual(0, result.returncode, result.stderr)
        plan = json.loads(result.stdout)
        self.assertEqual('without-nacos', plan['mode'])
        self.assertEqual(18099, plan['port'])
        self.assertFalse(plan['nacosConfigEnabled'])
        self.assertFalse(plan['nacosDiscoveryEnabled'])
        self.assertTrue(plan['businessConfiguration'].replace('\\', '/').endswith('/db/nacos/chengtian-14.yml'))
        self.assertNotIn('NACOS_PASSWORD', plan['environmentVariableNames'])
        self.assertNotIn('NACOS_CONFIG_IMPORTS', plan['environmentVariableNames'])
        self.assertIn('NACOS_SPRING_DATASOURCE_PASSWORD', plan['environmentVariableNames'])
        for value in ('synthetic-business-secret', 'stale-secret', 'stale-user'):
            self.assertNotIn(value, result.stdout)
        self.assertIn('--spring.config.import=', plan['argumentLine'])
        self.assertIn('--spring.cloud.nacos.discovery.enabled=false', plan['argumentLine'])
        self.assertIn('-Djdk.net.unixdomain.tmpdir=', plan['argumentLine'])
        self.assertIn('.runtime/no-nacos/data-element-runtime.jar', plan['argumentLine'].replace('\\', '/'))
        self.assertFalse((self.workspace / '.runtime').exists(), 'Validation must not launch or copy a runtime')

    def test_no_private_runtime_can_use_explicit_java_jar_and_process_environment(self):
        self.runtime.unlink()
        self.environment.unlink()
        result = self.invoke('-JavaExe', str(self.java))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('without-nacos', json.loads(result.stdout)['mode'])

    def test_incomplete_local_config_and_in_workspace_secrets_are_rejected(self):
        result = self.invoke('-LocalConfigPath', str(self.folder / 'missing.yml'))
        self.assertNotEqual(0, result.returncode)
        local_secret = self.workspace / 'private.json'
        local_secret.write_text('{"environment":{}}', encoding='utf-8')
        result = self.invoke('-LocalEnvironmentPath', str(local_secret))
        self.assertNotEqual(0, result.returncode)
        self.assertIn('outside the Git workspace', result.stderr)


if __name__ == '__main__':
    unittest.main()

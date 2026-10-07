"""Offline publication guards; fake connections never reach a real database."""
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import runpy
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import urllib.error


TOOLS = Path(__file__).resolve().parents[1]


def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, TOOLS / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


sync = load('resource_sync', 'resource-sync.py')
configure = load('configure_resources', 'configure-resources.py')


class Cursor:
    def __init__(self, connection):
        self.connection = connection
        self.rowcount = 0

    def __enter__(self):
        return self

    def __exit__(self, *_):
        return False

    def execute(self, sql, values=None):
        self.connection.statements.append((sql, values))
        if sql.startswith('UPDATE') and self.connection.fail_update:
            raise RuntimeError('Synthetic driver failure')
        self.rowcount = self.connection.update_count if sql.startswith('UPDATE') else 1

    def fetchall(self):
        return copy.deepcopy(self.connection.rows)

    def fetchone(self):
        return copy.deepcopy(self.connection.locked)


class Connection:
    db = b'baseline'

    def __init__(self):
        self.statements = []
        self.rollbacks = self.commits = 0
        self.update_count = 1
        self.fail_update = False
        self.rows = []
        self.locked = None

    def cursor(self):
        return Cursor(self)

    def rollback(self):
        self.rollbacks += 1

    def commit(self):
        self.commits += 1


class ResourceTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.root_patch = patch.object(sync, 'ROOT', self.root)
        self.root_patch.start()
        self.connection = Connection()
        self.meta = {'id': 'api-id', 'groupId': 'group-id', 'path': '/read', 'method': 'POST'}
        self.base = json.dumps(self.meta) + '\n' + sync.SEPARATOR + '\nreturn 1\n'
        self.entry = {'path': 'api/example.ms', 'databasePath': '/magic-api/api/example.ms',
                      'rowIds': ['row-id'], 'kind': 'api', 'metadataId': 'api-id',
                      'baseSha256': sync.digest(self.base)}
        self.manifest = {'database': 'baseline', 'resources': [self.entry]}
        sync.write_json(self.root / 'magic/.manifest.json', self.manifest)
        self.source = self.root / 'magic/api/example.ms'
        self.source.parent.mkdir(parents=True)
        self.source.write_bytes(self.base.encode())
        self.remote = {'database': 'baseline', 'resources': [{**self.entry, 'content': self.base}]}

    def tearDown(self):
        self.root_patch.stop()
        self.temp.cleanup()

    def changed_source(self):
        value = self.base.replace('return 1', 'return 2')
        self.source.write_bytes(value.encode())
        return value

    def runtime(self, *_args, **_kwargs):
        return {**self.meta, 'script': 'return 1'}

    def assert_no_write(self):
        self.assertEqual(self.connection.commits, 0)
        self.assertFalse(any(sql.startswith(('UPDATE', 'INSERT', 'DELETE')) for sql, _ in self.connection.statements))

    def test_crlf_pull_and_status_do_not_report_false_conflicts(self):
        self.source.write_bytes(self.base.replace('\n', '\r\n').encode())
        with patch.object(sync, 'snapshot', return_value=copy.deepcopy(self.remote)):
            self.assertEqual(sync.status(self.connection, 'magic')['changes'], [])
            sync.pull(self.connection, 'magic')
        self.assertEqual(self.source.read_bytes(), self.base.encode())

    def test_pull_refuses_to_overwrite_local_change(self):
        changed = self.changed_source()
        before = (self.root / 'magic/.manifest.json').read_bytes()
        with patch.object(sync, 'snapshot', return_value=copy.deepcopy(self.remote)):
            with self.assertRaisesRegex(ValueError, 'overwrite local changes'):
                sync.pull(self.connection, 'magic')
        self.assertEqual(self.source.read_text(), changed)
        self.assertEqual((self.root / 'magic/.manifest.json').read_bytes(), before)
        self.assert_no_write()

    def test_lowcode_tree_accepts_component_parents_and_matches_display_names(self):
        self.connection.rows = [
            {'tid': '0', 'pid': '', 'name': 'root', 'remark': '', 'type': '0', 'sort_order': 0, 'source_code': ''},
            {'tid': 'group', 'pid': '0', 'name': '数据登记', 'remark': '', 'type': '0', 'sort_order': 1, 'source_code': ''},
            {'tid': 'parent', 'pid': 'group', 'name': 'register-modal', 'remark': '登记组件', 'type': '1', 'sort_order': 2, 'source_code': '<template />'},
            {'tid': 'child', 'pid': 'parent', 'name': 'register-app', 'remark': '应用登记', 'type': '1', 'sort_order': 3, 'source_code': '<template />'},
        ]
        result = sync.snapshot(self.connection, 'lowcode')
        child = next(row for row in result['resources'] if row['id'] == 'child')
        self.assertEqual(child['path'], '数据登记/登记组件(register-modal)/应用登记(register-app).vue')
        self.assertEqual(child['name'], 'register-app')
        self.assertEqual(result['warnings'], [])
        self.assert_no_write()

    def test_lowcode_path_migration_does_not_discard_local_edits(self):
        old_entry = {'id': 'component', 'parentId': 'parent', 'name': 'child', 'type': '1',
                     'path': '_unattached/parent/child.vue', 'baseSha256': sync.digest('old')}
        sync.write_json(self.root / 'lowcode/.manifest.json', {'resources': [old_entry]})
        old_file = sync.safe_path(self.root / 'lowcode', old_entry['path'])
        old_file.parent.mkdir(parents=True)
        old_file.write_text('local edit')
        new_entry = {**old_entry, 'path': '中文父级/child.vue', 'content': 'old'}
        with patch.object(sync, 'snapshot', return_value={'resources': [new_entry]}):
            with self.assertRaisesRegex(ValueError, 'overwrite local changes'):
                sync.pull(self.connection, 'lowcode')
        self.assertEqual(old_file.read_text(), 'local edit')
        self.assertFalse((self.root / 'lowcode/中文父级/child.vue').exists())
        self.assert_no_write()

    def test_pull_removes_retired_id_tree_but_preserves_current_empty_group(self):
        old_entry = {'id': 'component', 'type': '1', 'path': '_unattached/parent/child.vue',
                     'baseSha256': sync.digest('old')}
        sync.write_json(self.root / 'lowcode/.manifest.json', {'resources': [old_entry]})
        old_file = sync.safe_path(self.root / 'lowcode', old_entry['path'])
        old_file.parent.mkdir(parents=True)
        old_file.write_text('old')
        incoming = {'resources': [
            {**old_entry, 'path': '中文父级/child.vue', 'content': 'old'},
            {'id': 'group', 'type': '0', 'path': '保留分组', 'content': ''},
        ]}
        with patch.object(sync, 'snapshot', return_value=incoming):
            result = sync.pull(self.connection, 'lowcode')
        self.assertFalse((self.root / 'lowcode/_unattached').exists())
        self.assertTrue((self.root / 'lowcode/保留分组').is_dir())
        self.assertEqual((self.root / 'lowcode/中文父级/child.vue').read_text(), 'old')
        self.assertEqual(result['removedEmptyDirectories'], 2)
        self.assert_no_write()

    def test_pull_preserves_unregistered_file_in_retired_folder(self):
        unregistered = self.source.parent / 'notes.txt'
        unregistered.write_text('keep local notes')
        with patch.object(sync, 'snapshot', return_value={'database': 'baseline', 'resources': []}):
            sync.pull(self.connection, 'magic')
        self.assertFalse(self.source.exists())
        self.assertEqual(unregistered.read_text(), 'keep local notes')
        self.assertTrue((self.root / 'magic').is_dir())
        self.assert_no_write()

    def test_concurrent_remote_content_blocks_publication(self):
        self.changed_source()
        remote = copy.deepcopy(self.remote)
        remote['resources'][0].update(content=self.base.replace('return 1', 'return 3'), baseSha256=sync.digest('other'))
        with patch.object(sync, 'snapshot', return_value=remote), patch.object(sync, 'runtime_call') as runtime:
            with self.assertRaisesRegex(ValueError, 'Concurrent remote change'):
                sync.publish(self.connection, {}, 'magic/api/example.ms')
            runtime.assert_not_called()
        self.assert_no_write()

    def test_remote_row_identity_change_blocks_even_equal_content(self):
        remote = copy.deepcopy(self.remote)
        remote['resources'][0]['rowIds'] = ['replacement-row']
        with patch.object(sync, 'snapshot', return_value=remote):
            with self.assertRaisesRegex(ValueError, 'identity changed'):
                sync.publish(self.connection, {}, 'magic/api/example.ms')
        self.assert_no_write()

    def test_remote_duplicate_metadata_id_at_another_path_blocks_publication(self):
        self.changed_source()
        remote = copy.deepcopy(self.remote)
        remote['resources'].append({**remote['resources'][0], 'path': 'api/other.ms'})
        with patch.object(sync, 'snapshot', return_value=remote):
            with self.assertRaisesRegex(ValueError, 'Duplicate Magic metadata'):
                sync.publish(self.connection, {}, 'magic/api/example.ms')
        self.assert_no_write()

    def test_row_replaced_after_runtime_check_rolls_back(self):
        self.changed_source()
        locked = copy.deepcopy(self.remote)
        locked['resources'][0]['rowIds'] = ['new-row']
        with patch.object(sync, 'snapshot', side_effect=[self.remote, locked]), patch.object(sync, 'runtime_call', side_effect=self.runtime):
            with self.assertRaisesRegex(ValueError, 'identity changed'):
                sync.publish(self.connection, {}, 'magic/api/example.ms')
        self.assertGreaterEqual(self.connection.rollbacks, 2)
        self.assert_no_write()

    def test_duplicate_added_after_runtime_check_rolls_back(self):
        self.changed_source()
        locked = copy.deepcopy(self.remote)
        locked['resources'].append({**locked['resources'][0], 'path': 'api/duplicate.ms'})
        with patch.object(sync, 'snapshot', side_effect=[self.remote, locked]), patch.object(sync, 'runtime_call', side_effect=self.runtime):
            with self.assertRaisesRegex(ValueError, 'Duplicate Magic metadata'):
                sync.publish(self.connection, {}, 'magic/api/example.ms')
        self.assert_no_write()

    def test_guard_row_count_mismatch_rolls_back_without_advancing_manifest(self):
        self.changed_source()
        self.connection.update_count = 0
        before = (self.root / 'magic/.manifest.json').read_bytes()
        with patch.object(sync, 'snapshot', return_value=self.remote), patch.object(sync, 'runtime_call', side_effect=self.runtime):
            with self.assertRaisesRegex(ValueError, 'atomic publication'):
                sync.publish(self.connection, {}, 'magic/api/example.ms')
        self.assertEqual(self.connection.commits, 0)
        self.assertGreaterEqual(self.connection.rollbacks, 2)
        self.assertEqual((self.root / 'magic/.manifest.json').read_bytes(), before)

    def test_driver_exception_rolls_back(self):
        self.changed_source()
        self.connection.fail_update = True
        with patch.object(sync, 'snapshot', return_value=self.remote), patch.object(sync, 'runtime_call', side_effect=self.runtime):
            with self.assertRaises(RuntimeError):
                sync.publish(self.connection, {}, 'magic/api/example.ms')
        self.assertEqual(self.connection.commits, 0)
        self.assertGreaterEqual(self.connection.rollbacks, 2)

    def test_success_uses_exact_raw_cas_and_advances_manifest_after_runtime_verification(self):
        local = self.changed_source()
        remote = copy.deepcopy(self.remote)
        remote['resources'][0]['content'] = self.base.replace('\n', '\r\n')
        count = 0

        def runtime(_config, route, body=None):
            nonlocal count
            if route.endswith('/refresh'):
                return {}
            count += 1
            return {**self.meta, 'script': 'return 1' if count == 1 else 'return 2'}

        with patch.object(sync, 'snapshot', return_value=remote) as snapshot, patch.object(sync, 'runtime_call', side_effect=runtime):
            result = sync.publish(self.connection, {}, 'magic/api/example.ms')
        self.assertEqual(result['result'], 'published-and-runtime-verified')
        self.assertTrue(snapshot.call_args_list[-1].kwargs['for_update'])
        self.assertEqual(self.connection.statements[-1][1][-1], remote['resources'][0]['content'])
        self.assertEqual(self.connection.commits, 1)
        self.assertEqual(sync.load_manifest('magic')['resources'][0]['baseSha256'], sync.digest(local))

    def test_lowcode_bundle_wrong_component_identity_is_rejected(self):
        entry = {'id': 'component-a', 'parentId': 'group', 'name': 'a', 'type': '1', 'path': 'a.vue', 'baseSha256': sync.digest('old')}
        sync.write_json(self.root / 'lowcode/.manifest.json', {'database': 'baseline', 'resources': [entry]})
        (self.root / 'lowcode/a.vue').write_text('new')
        bundle = self.root / 'bundle.json'
        sync.write_json(bundle, {'componentId': 'component-b', 'sourceSha256': sync.digest('new'), 'compileJs': 'return __sfc__', 'compileCss': ''})
        remote = {'database': 'baseline', 'resources': [{**entry, 'content': 'old'}]}
        with patch.object(sync, 'snapshot', return_value=remote):
            with self.assertRaisesRegex(ValueError, 'Compilation does not match'):
                sync.publish(self.connection, {}, 'lowcode/a.vue', bundle)
        self.assert_no_write()

    def test_pure_json_function_metadata_is_recognized_without_rewriting_source(self):
        # A separator inside a JSON string is code data, not the storage delimiter.
        raw = json.dumps({**self.meta, 'script': 'return "================================"'})
        self.connection.rows = [{'tid': 'function-row', 'file_path': '/magic-api/function/a.ms', 'file_kind': None, 'file_content': raw}]
        snapshot = sync.snapshot(self.connection, 'magic')
        self.assertEqual(snapshot['resources'][0]['metadataId'], 'api-id')
        self.assertEqual(snapshot['resources'][0]['content'], raw)
        self.assertEqual(snapshot['warnings'], [])
        self.assertEqual(sync.metadata(raw)[1], 'return "================================"')

    def test_private_projection_never_exports_secret_or_its_raw_digest(self):
        raw = json.dumps({'username': 'synthetic-login', 'password': 'synthetic-secret', 'url': 'jdbc:example'})
        self.connection.rows = [{'tid': 'source-row', 'file_path': '/magic-api/datasource/云梯网关.json', 'file_kind': 'datasource', 'file_content': raw}]
        data = sync.snapshot(self.connection, 'magic')
        serialized = json.dumps(data)
        self.assertNotIn('synthetic-secret', serialized)
        self.assertNotIn('synthetic-login', serialized)
        self.assertNotIn(hashlib.sha256(raw.encode()).hexdigest(), serialized)
        self.assertTrue(data['resources'][0]['publicationBlocked'])
        self.assertEqual(json.loads(data['resources'][0]['content'])['password'], '')

    def test_gateway_and_lowcode_projection_only_touch_exact_scoped_fields(self):
        raw = 'var gatewayConfig = {clientId: "synthetic-id", clientSecret: "synthetic-secret", nested: {other: "keep"}}\nvar clientSecret = "unrelated"'
        projected, flags = sync.public_projection('magic', {'file_path': '/magic-api/api/11.省厅公安定制-GA/02.零信任网关对接/01.网关配置检查.ms'}, raw)
        self.assertNotIn('synthetic-secret', projected)
        self.assertIn('var clientSecret = "unrelated"', projected)
        self.assertIn('__PRIVATE_GATEWAY_CLIENT_SECRET__', projected)
        vue = 'const dataSources = ref<any[]>([{user_name: "synthetic-user", password: "synthetic-secret"}]);\nconst password = "unrelated";'
        projected, _ = sync.public_projection('lowcode', {'tid': '2060245186132844544', 'name': 'api-build-dialog'}, vue)
        self.assertNotIn('synthetic-secret', projected)
        self.assertIn('const password = "unrelated"', projected)
        untouched, flags = sync.public_projection('lowcode', {'tid': 'other-id', 'name': 'api-build-dialog'}, vue)
        self.assertEqual(untouched, vue)
        self.assertFalse(flags)

    def test_private_projection_blocks_even_already_current_publication(self):
        self.remote['resources'][0]['publicationBlocked'] = 'private'
        with patch.object(sync, 'snapshot', return_value=self.remote), patch.object(sync, 'runtime_call') as runtime:
            with self.assertRaisesRegex(ValueError, 'Publication blocked'):
                sync.publish(self.connection, {}, 'magic/api/example.ms')
            runtime.assert_not_called()
        self.assert_no_write()

    def test_redacted_datasource_json_is_explicitly_blocked_before_script_identity_checks(self):
        entry = {'path': 'datasource/private.json', 'databasePath': '/magic-api/datasource/云梯网关.json',
                 'rowIds': ['source-row'], 'kind': 'datasource', 'baseSha256': sync.digest('{}'), 'publicationBlocked': 'private'}
        sync.write_json(self.root / 'magic/.manifest.json', {'database': 'baseline', 'resources': [entry]})
        target = self.root / 'magic/datasource/private.json'
        target.parent.mkdir(parents=True)
        target.write_text('{}')
        with self.assertRaisesRegex(ValueError, 'Publication blocked'):
            sync.publish(self.connection, {}, 'magic/datasource/private.json')
        self.assert_no_write()

    def test_canonical_paths_reject_absolute_traversal_and_symlink_escape(self):
        for value in ['../outside.ms', '/outside.ms', 'C:/outside.ms', 'api/../../outside.ms', 'api/a\x00.ms']:
            with self.subTest(value=value), self.assertRaises(ValueError):
                sync.safe_path(self.root / 'magic', value)
        outside = self.root.parent / (self.root.name + '-outside')
        outside.mkdir()
        link = self.root / 'magic/link'
        try:
            try:
                link.symlink_to(outside, target_is_directory=True)
            except OSError as error:
                self.skipTest('Host does not permit symlink creation: ' + type(error).__name__)
            with self.assertRaisesRegex(ValueError, 'escapes canonical folder'):
                sync.safe_path(self.root / 'magic', 'link/a.ms')
        finally:
            if link.is_symlink():
                link.unlink()
            outside.rmdir()


class CompilerTests(unittest.TestCase):
    def test_output_identity_is_not_a_path_and_source_hash_normalizes_crlf(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'tools').mkdir()
            shutil.copyfile(TOOLS / 'compile-lowcode.mjs', root / 'tools/compile-lowcode.mjs')
            (root / 'package.json').write_text('{"type":"module"}')
            (root / 'lowcode').mkdir()
            source = '<template>ok</template>\r\n'
            (root / 'lowcode/a.vue').write_bytes(source.encode())
            identifier = '../../escape'
            (root / 'lowcode/.manifest.json').write_text(json.dumps({'resources': [{'id': identifier, 'path': 'a.vue', 'type': '1'}]}))
            compiler = root / 'data-elements-chengtian/src/plugins/compiler'
            compiler.mkdir(parents=True)
            (compiler / 'sfc-compiler.js').write_text('export async function compileCode() { return {compileJs: "const __sfc__ = {}; return __sfc__", compileCss: ""}; }')
            public = root / 'data-elements-chengtian/public'
            public.mkdir(parents=True)
            (public / 'babel.min.js').write_text('this.Babel = {};')
            process = subprocess.run(['node', str(root / 'tools/compile-lowcode.mjs'), 'lowcode/a.vue'], capture_output=True, text=True, check=True)
            output = json.loads(process.stdout)['compiled']
            self.assertTrue(output.replace('\\', '/').startswith('logs/lowcode/compiled/'))
            self.assertEqual(Path(output).name, hashlib.sha256(identifier.encode()).hexdigest() + '.json')
            bundle = json.loads((root / output).read_text())
            self.assertEqual(bundle['componentId'], identifier)
            self.assertEqual(bundle['sourceSha256'], sync.digest(source))
            escaped = subprocess.run(['node', str(root / 'tools/compile-lowcode.mjs'), '../outside.vue'], capture_output=True, text=True)
            self.assertNotEqual(escaped.returncode, 0)


class ConfigurationTests(unittest.TestCase):
    def test_private_output_rejects_repo_and_parent_symlinks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            repo = root / 'repo'
            repo.mkdir()
            with self.assertRaises(configure.ConfigurationError):
                configure.private_output(repo / 'private.json', repo)
            self.assertEqual(configure.private_output(root / 'private.json', repo), root / 'private.json')
            link = root / 'link'
            try:
                link.symlink_to(repo, target_is_directory=True)
            except OSError as error:
                self.skipTest('Host does not permit symlink creation: ' + type(error).__name__)
            with self.assertRaises(configure.ConfigurationError):
                configure.private_output(link / 'private.json', repo)

    def test_urls_reject_embedded_credentials_without_repeating_them(self):
        for value in ['http://name:synthetic-secret@localhost/nacos', 'http://localhost/nacos?accessToken=synthetic-token']:
            with self.assertRaises(configure.ConfigurationError) as caught:
                configure.service_url(value)
            self.assertNotIn('synthetic-secret', str(caught.exception))
            self.assertNotIn('synthetic-token', str(caught.exception))

    def test_network_error_does_not_print_access_token_url_or_password(self):
        secret_url = 'http://localhost/nacos?accessToken=synthetic-token'
        responses = [io.BytesIO(b'{"accessToken":"synthetic-token"}'), urllib.error.HTTPError(secret_url, 500, 'synthetic-secret', {}, None)]
        stderr = io.StringIO()
        with tempfile.TemporaryDirectory() as directory, patch.dict('os.environ', {'NACOS_USER': 'synthetic-user', 'NACOS_PASSWORD': 'synthetic-secret'}), patch.object(sys, 'argv', ['configure-resources.py', '--output', str(Path(directory) / 'private.json')]), patch('urllib.request.urlopen', side_effect=responses), patch('sys.stderr', stderr):
            with self.assertRaises(SystemExit):
                runpy.run_path(str(TOOLS / 'configure-resources.py'), run_name='__main__')
            self.assertFalse((Path(directory) / 'private.json').exists())
        self.assertNotIn('synthetic-token', stderr.getvalue())
        self.assertNotIn('synthetic-secret', stderr.getvalue())
        self.assertIn('HTTPError', stderr.getvalue())


if __name__ == '__main__':
    unittest.main()

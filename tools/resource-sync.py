"""Version shared Magic/low-code source locally; database access defaults to read-only.

Connection settings stay outside Git. See docs/本地资源版本管理.md.
"""
import argparse
import errno
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import sys
import urllib.request
import urllib.parse

ROOT = Path(__file__).resolve().parents[1]
PRIVATE_CONFIG = Path.home() / '.codex/private/data-elements/resources.local.json'
SEPARATOR = '================================'


def digest(text):
    # Git/editor line-ending conversion is not a business-source change. SQL guards
    # still compare the exact remote text so normalization never weakens CAS.
    normalized = (text or '').replace('\r\n', '\n').replace('\r', '\n')
    return hashlib.sha256(normalized.encode('utf-8')).hexdigest()


def read_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def read_source(path):
    # Preserve CRLF exactly: read_text's newline conversion would create false conflicts.
    with path.open(encoding='utf-8', newline='') as stream:
        return stream.read()


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def connect(config):
    import pymysql
    cfg = dict(config['control'])
    for field in ['host', 'port', 'user', 'password', 'database']:
        value = os.environ.get('RESOURCE_DB_' + field.upper())
        if value is not None:
            cfg[field] = int(value) if field == 'port' else value
    return pymysql.connect(**cfg, cursorclass=pymysql.cursors.DictCursor,
                           connect_timeout=10, read_timeout=60, autocommit=False)


def safe_path(base, relative):
    if not base.resolve().is_relative_to(ROOT.resolve()):
        raise ValueError('Canonical folder escapes repository')
    parts = PurePosixPath(relative).parts
    if PurePosixPath(relative).is_absolute() or not parts or any(p in {'.', '..'}
                        or any(c in '<>:"\\|?*' or ord(c) < 32 for c in p)
                        or p.endswith((' ', '.')) for p in parts):
        raise ValueError('Unsupported local resource path: ' + relative)
    target = (base / Path(*parts)).resolve()
    if not target.is_relative_to(base.resolve()):
        raise ValueError('Resource escapes canonical folder')
    return target


def metadata(content):
    head, sep, script = content.partition(SEPARATOR)
    try:
        value = json.loads(content)
    except json.JSONDecodeError:
        value = None
    if value is not None or not sep:
        if value is None:
            value = json.loads(content)
        if not isinstance(value, dict):
            raise ValueError('Magic metadata must be an object')
        script = value.get('script') or ''
        if not isinstance(script, str):
            raise ValueError('Magic script must be text')
        return {key: item for key, item in value.items() if key != 'script'}, script.strip()
    value = json.loads(head)
    if not isinstance(value, dict):
        raise ValueError('Magic metadata must be an object')
    return value, script.strip()


def local_segment(value):
    """Windows-invalid names use reversible %XX escaping; remote identities stay exact."""
    value = str(value)
    result = ''.join(urllib.parse.quote(c, safe='') if c in '<>:"/\\|?*%' or ord(c) < 32 else c
                     for c in value)
    if result.endswith((' ', '.')):
        result = result[:-1] + ('%20' if result[-1] == ' ' else '%2E')
    if result in {'.', '..', ''}:
        raise ValueError('Invalid empty/dot resource name')
    if result.split('.')[0].upper() in {'CON', 'PRN', 'AUX', 'NUL',
            *(f'COM{i}' for i in range(1, 10)), *(f'LPT{i}' for i in range(1, 10))}:
        result = '%' + format(ord(result[0]), '02X') + result[1:]
    return result


def projected_literals(content, anchor, opener, closer, replacements):
    """Redact only named literal fields inside one explicitly identified JS/MS block."""
    matches = list(re.finditer(anchor, content))
    if len(matches) != 1:
        raise ValueError('Private resource projection needs reviewed source structure')
    start = matches[0].end() - 1
    depth, quote, escaped, line_comment, block_comment = 0, None, False, False, False
    end = None
    i = start
    while i < len(content):
        char, following = content[i], content[i:i + 2]
        if line_comment:
            if char in '\r\n':
                line_comment = False
        elif block_comment:
            if following == '*/':
                block_comment = False
                i += 1
        elif quote:
            if escaped:
                escaped = False
            elif char == '\\':
                escaped = True
            elif char == quote:
                quote = None
        elif following == '//':
            line_comment = True
            i += 1
        elif following == '/*':
            block_comment = True
            i += 1
        elif char in '\'"`':
            quote = char
        elif char == opener:
            depth += 1
        elif char == closer:
            depth -= 1
            if depth == 0:
                end = i + 1
                break
        i += 1
    if end is None:
        raise ValueError('Private resource projection needs a complete source block')
    block = content[start:end]
    for field, replacement in replacements.items():
        pattern = re.compile(r'(?P<prefix>(?<![\w$])(?:' + re.escape(field) + r'|[\'"]' + re.escape(field)
                             + r'[\'"])\s*:\s*)(?P<quote>[\'"])(?:\\.|(?!(?P=quote))[^\\])*(?P=quote)')
        block, count = pattern.subn(lambda m: m['prefix'] + m['quote'] + replacement + m['quote'], block)
        if not count:
            raise ValueError('Private resource projection needs reviewed literal fields')
    return content[:start] + block + content[end:]


def public_projection(kind, row, content):
    fields = []
    if kind == 'magic' and row['file_path'] == '/magic-api/datasource/云梯网关.json':
        value = json.loads(content)
        if not isinstance(value, dict) or not all(key in value for key in ['username', 'password']):
            raise ValueError('Private datasource projection needs reviewed JSON fields')
        value['username'] = value['password'] = ''
        content = json.dumps(value, ensure_ascii=False, indent=2) + '\n'
        fields = ['username', 'password']
    elif kind == 'magic' and row['file_path'] in {
            '/magic-api/api/11.省厅公安定制-GA/02.零信任网关对接/01.网关配置检查.ms',
            '/magic-api/api/11.省厅公安定制/02.零信任网关对接/01.网关配置检查.ms'}:
        content = projected_literals(content, r'\bvar\s+gatewayConfig\s*=\s*\{', '{', '}', {
            'clientId': '__PRIVATE_GATEWAY_CLIENT_ID__',
            'clientSecret': '__PRIVATE_GATEWAY_CLIENT_SECRET__',
        })
        fields = ['gatewayConfig.clientId', 'gatewayConfig.clientSecret']
    elif kind == 'lowcode' and row['tid'] == '2060245186132844544' and row['name'] == 'api-build-dialog':
        content = projected_literals(content, r'\bconst\s+dataSources\s*=\s*ref(?:<[^\n]*?>)?\s*\(\s*\[', '[', ']', {
            'user_name': '', 'password': '',
        })
        fields = ['dataSources[].user_name', 'dataSources[].password']
    return content, ({'redactedFields': fields,
                      'publicationBlocked': 'Private literals are omitted from Git; migrate them to runtime configuration before publication'} if fields else {})


def magic_layout_warnings(entries):
    """Report physical/editor tree drift; never guess a missing parent or move remote files."""
    parsed, groups, warnings = [], {}, []
    for entry in entries:
        path = entry['databasePath']
        if path.endswith('/') or not path.startswith(('/magic-api/api/', '/magic-api/function/', '/magic-api/task/')):
            continue
        try:
            meta, _ = metadata(entry['content'])
        except (ValueError, TypeError):
            continue
        parsed.append((entry, meta))
        if path.endswith('/group.json') and meta.get('id'):
            groups.setdefault(meta['id'], meta)

    def names(identity, seen=()):
        if identity in {None, '', '0', 'api:0', 'function:0', 'task:0'}:
            return []
        if identity in seen or identity not in groups:
            raise ValueError(str(identity))
        group = groups[identity]
        return names(group.get('parentId'), seen + (identity,)) + [group['name']]

    for entry, meta in parsed:
        if not meta.get('name'):
            continue
        path = entry['databasePath']
        group = path.endswith('/group.json')
        if group and meta.get('parentId') in {None, ''}:
            warnings.append({'invalidMagicRootParent': entry['path'], 'expectedParentId': '0'})
        try:
            lineage = names(meta.get('id') if group else meta.get('groupId'))
        except (ValueError, KeyError) as error:
            warnings.append({'brokenMagicParent': entry['path'], 'parentId': str(error)})
            continue
        expected = '/magic-api/' + path.split('/')[2] + '/' + '/'.join(
            lineage + (['group.json'] if group else [meta['name'] + '.ms']))
        if expected != path:
            warnings.append({'magicEditorPathMismatch': entry['path'], 'expectedDatabasePath': expected})
    return warnings


def snapshot(connection, kind, for_update=False):
    with connection.cursor() as q:
        if kind == 'magic':
            q.execute('SELECT tid,file_path,file_kind,file_content FROM api_file_t '
                      'WHERE is_del=0 ORDER BY file_path,tid' + (' FOR UPDATE' if for_update else ''))
            by_path = {}
            raw_by_path = {}
            for row in q.fetchall():
                remote = row['file_path']
                if not remote.startswith('/magic-api/'):
                    raise ValueError('Magic path outside configured root: ' + str(remote))
                raw_relative = remote[len('/magic-api/'):].rstrip('/')
                relative = '/'.join(local_segment(p) for p in raw_relative.split('/')) if raw_relative else ''
                raw_content = row['file_content'] or ''
                content, privacy = public_projection(kind, row, raw_content)
                if remote in raw_by_path and raw_by_path[remote] != raw_content:
                    raise ValueError('Different active contents share Magic path: ' + remote)
                raw_by_path[remote] = raw_content
                entry = by_path.setdefault(remote, {
                    'path': relative, 'databasePath': remote, 'rowIds': [],
                    'kind': 'directory' if remote.endswith('/') else row['file_kind'],
                    'baseSha256': digest(content), 'content': content,
                    **privacy,
                })
                if entry['content'] != content:
                    raise ValueError('Different active contents share Magic path: ' + remote)
                entry['rowIds'].append(row['tid'])
                if not remote.endswith('/'):
                    try:
                        meta, _ = metadata(content)
                        if meta.get('id'):
                            entry['metadataId'] = meta['id']
                    except (ValueError, TypeError):
                        entry['invalidMetadata'] = True
            paths = [e['path'].casefold() for e in by_path.values()]
            if len(paths) != len(set(paths)):
                raise ValueError('Case-insensitive Magic local path collision')
            ids = {}
            for e in by_path.values():
                if e.get('metadataId'):
                    ids.setdefault(e['metadataId'], []).append(e['path'])
            warnings = [{'metadataId': key, 'duplicatePaths': value}
                        for key, value in ids.items() if len(value) > 1]
            warnings += [{'invalidMetadata': e['path']} for e in by_path.values()
                         if e.get('invalidMetadata')]
            warnings += magic_layout_warnings(list(by_path.values()))
            return {'version': 1, 'database': connection.db.decode(),
                    'databaseRoot': '/magic-api/', 'warnings': warnings,
                    'resources': list(by_path.values())}
        q.execute('SELECT tid,pid,name,remark,type,sort_order,source_code '
                  'FROM ui_component_t WHERE is_del=0 ORDER BY tid')
        rows = {r['tid']: r for r in q.fetchall()}
    warnings = []

    def display_label(row):
        # Match UiComponentAdminService.tree(), including historical components
        # that legitimately own child components. Import names remain in the index.
        name = row['name']
        remark = (row['remark'] or '').strip()
        return f'{remark}({name})' if str(row['type']) == '1' and remark else name

    def group_path(tid, chain=()):
        if tid in {None, '', '0'}:
            return []
        if tid in chain:
            raise ValueError('Cyclic low-code group: ' + tid)
        r = rows.get(tid)
        if not r:
            warnings.append({'id': chain[0] if chain else tid,
                             'missingParentId': tid})
            return ['_unattached', str(tid)]
        return group_path(r['pid'], chain + (tid,)) + [local_segment(display_label(r))]

    entries = []
    for row in rows.values():
        if row['tid'] == '0':
            continue
        is_group = str(row['type']) == '0'
        parts = group_path(row['pid'], (row['tid'],)) + [local_segment(display_label(row))]
        relative = '/'.join(parts) + ('' if is_group else '.vue')
        raw_content = row['source_code'] or ''
        content, privacy = public_projection(kind, row, raw_content)
        entries.append({'id': row['tid'], 'parentId': row['pid'], 'name': row['name'],
                        'displayName': row['remark'], 'type': str(row['type']),
                        'sortOrder': row['sort_order'], 'path': relative,
                        'baseSha256': digest(content), 'content': content,
                        **privacy})
    paths = [e['path'].casefold() for e in entries]
    if len(paths) != len(set(paths)):
        raise ValueError('Case-insensitive low-code local path collision')
    return {'version': 1, 'database': connection.db.decode(),
            'rootId': '0', 'warnings': warnings,
            'resources': sorted(entries, key=lambda e: e['path'])}


def is_directory(entry, kind):
    return entry.get('kind') == 'directory' if kind == 'magic' else entry['type'] == '0'


def load_manifest(kind):
    return read_json(safe_path(ROOT / kind, '.manifest.json'))


def pull(connection, kind):
    base = ROOT / kind
    data = snapshot(connection, kind)
    old = load_manifest(kind) if (base / '.manifest.json').exists() else {'resources': []}
    previous = {e['path']: e for e in old['resources']}
    incoming = {e['path']: e for e in data['resources']}
    conflicts = []
    for rel, e in incoming.items():
        if is_directory(e, kind):
            if rel:
                safe_path(base, rel)
            continue
        target = safe_path(base, rel)
        if target.exists():
            local_hash = digest(read_source(target))
            if local_hash not in {e['baseSha256'], previous.get(rel, {}).get('baseSha256')}:
                conflicts.append(rel)
    removed = []
    for rel, e in previous.items():
        if rel in incoming or is_directory(e, kind):
            continue
        target = safe_path(base, rel)
        if target.exists():
            if digest(read_source(target)) != e['baseSha256']:
                conflicts.append(rel)
            else:
                removed.append(target)
    if conflicts:
        raise ValueError('Pull would overwrite local changes: ' + ', '.join(conflicts))
    required_directories = {base.resolve()}
    obsolete_directories = set()
    for rel, entry in incoming.items():
        if not rel:
            continue
        target = safe_path(base, rel)
        parent = target if is_directory(entry, kind) else target.parent
        while parent != base.resolve():
            required_directories.add(parent)
            parent = parent.parent
    for rel, entry in previous.items():
        if not rel or rel in incoming:
            continue
        target = safe_path(base, rel)
        parent = target if is_directory(entry, kind) else target.parent
        while parent != base.resolve():
            obsolete_directories.add(parent)
            parent = parent.parent
    base.mkdir(exist_ok=True)
    for e in data['resources']:
        if not e['path']:
            e.pop('content', None)
            continue
        target = safe_path(base, e['path'])
        content = e.pop('content')
        if is_directory(e, kind):
            target.mkdir(parents=True, exist_ok=True)
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content.encode('utf-8'))
    for target in removed:
        target.unlink()
    # Retired paths must not leave ID/old-name buckets after a tree rename.
    # Only empty ancestors recorded by the old manifest qualify; never recurse
    # into or delete unregistered files, current groups, or the canonical root.
    removed_directories = 0
    for directory in sorted(obsolete_directories - required_directories,
                            key=lambda path: len(path.parts), reverse=True):
        if directory.is_symlink():
            continue
        try:
            directory.rmdir()
            removed_directories += 1
        except OSError as error:
            if error.errno not in {errno.ENOENT, errno.ENOTEMPTY, errno.EEXIST}:
                data.setdefault('warnings', []).append('Unable to remove retired empty directory: '
                                                       + directory.relative_to(base.resolve()).as_posix())
    write_json(base / '.manifest.json', data)
    return {'kind': kind, 'resources': len(data['resources']),
            'files': sum(not is_directory(e, kind) for e in data['resources']),
            'removedEmptyDirectories': removed_directories,
            'warningCount': len(data.get('warnings', []))}


def status(connection, kind, files=None):
    old = load_manifest(kind)
    remote = snapshot(connection, kind)
    current = {e['path']: e for e in remote['resources']}
    changes = []
    known = set()
    for e in old['resources']:
        if is_directory(e, kind):
            continue
        rel = e['path']
        known.add(rel)
        if files and str(PurePosixPath(kind) / rel) not in files:
            continue
        target = safe_path(ROOT / kind, rel)
        local_hash = digest(read_source(target)) if target.exists() else None
        r = current.get(rel)
        remote_hash = r['baseSha256'] if r else None
        remote_changed = remote_hash != e['baseSha256']
        if local_hash != e['baseSha256'] or remote_changed:
            changes.append({'file': kind + '/' + rel, 'localChanged': local_hash != e['baseSha256'],
                            'remoteChanged': remote_changed,
                            'sameContent': local_hash == remote_hash,
                            'missingLocal': local_hash is None, 'missingRemote': r is None})
    unknown = [kind + '/' + p.relative_to(ROOT / kind).as_posix()
               for p in (ROOT / kind).rglob('*.ms' if kind == 'magic' else '*.vue')
               if p.relative_to(ROOT / kind).as_posix() not in known]
    remote_new = [kind + '/' + e['path'] for e in remote['resources']
                  if not is_directory(e, kind) and e['path'] not in known]
    return {'kind': kind, 'changes': changes, 'unregisteredFiles': unknown,
            'newRemoteFiles': remote_new, 'warningCount': len(remote.get('warnings', []))}


def runtime_call(config, route, body=None):
    runtime = config.get('runtime', {})
    base = os.environ.get('RESOURCE_API_URL', runtime.get('baseUrl', ''))
    token = os.environ.get('RESOURCE_API_TOKEN', runtime.get('token', ''))
    if not base or not token:
        raise ValueError('Publication requires RESOURCE_API_URL and RESOURCE_API_TOKEN')
    headers = {'token': token}
    data = None if body is None else json.dumps(body).encode('utf-8')
    if data is not None:
        headers['Content-Type'] = 'application/json'
    req = urllib.request.Request(base.rstrip('/') + route, data=data, headers=headers)
    result = json.load(urllib.request.urlopen(req, timeout=60))
    if result.get('code') not in {0, 1, 200}:
        raise ValueError('Runtime rejected resource operation: ' + route)
    return result.get('data')


def validate_identity(manifest, remote_data, entry, kind):
    if manifest.get('database') != remote_data.get('database'):
        raise ValueError('Control database identity changed; inspect before publication')
    remote = next((e for e in remote_data['resources'] if e['path'] == entry['path']), None)
    fields = ['databasePath', 'rowIds', 'kind', 'metadataId'] if kind == 'magic' else ['id', 'parentId', 'name', 'type']
    if not remote or any(remote.get(field) != entry.get(field) for field in fields):
        raise ValueError('Remote identity changed; inspect before publication')
    if kind == 'magic':
        identifier = remote.get('metadataId')
        if not identifier or sum(e.get('metadataId') == identifier for e in remote_data['resources']) != 1:
            raise ValueError('Duplicate Magic metadata identity; resolve before publication')
    return remote


def verify_runtime_magic(loaded, meta, script):
    if not loaded or any(loaded.get(field) != meta.get(field) for field in ['id', 'groupId', 'path', 'method']):
        raise ValueError('Runtime resource identity differs from control database; do not publish')
    if digest(loaded.get('script', '').strip()) != digest(script):
        raise ValueError('Runtime source differs from control database; do not publish')


def publish(connection, config, file, compiled=None):
    kind, _, rel = file.replace('\\', '/').partition('/')
    if kind not in {'magic', 'lowcode'}:
        raise ValueError('Publish accepts a canonical magic/ or lowcode/ file')
    manifest = load_manifest(kind)
    entry = next((e for e in manifest['resources'] if e['path'] == rel), None)
    if not entry or is_directory(entry, kind):
        raise ValueError('New resources/groups require reviewed registration before publication')
    local = read_source(safe_path(ROOT / kind, rel))
    if entry.get('publicationBlocked'):
        raise ValueError('Publication blocked for a private-resource projection; migrate credentials to runtime configuration first')
    remote_data = snapshot(connection, kind)
    candidate = next((e for e in remote_data['resources'] if e['path'] == rel), None)
    if candidate and candidate.get('publicationBlocked'):
        raise ValueError('Publication blocked for a private-resource projection; migrate credentials to runtime configuration first')
    remote = validate_identity(manifest, remote_data, entry, kind)
    if remote['baseSha256'] == digest(local):
        return {'file': file, 'result': 'already-current'}
    if remote['baseSha256'] != entry['baseSha256']:
        raise ValueError('Concurrent remote change; inspect before publication')
    if kind == 'magic':
        meta, script = metadata(local)
        previous_meta, _ = metadata(remote['content'])
        if sum(e.get('metadataId') == meta.get('id') for e in manifest['resources']) != 1:
            raise ValueError('Duplicate Magic metadata identity; resolve before publication')
        if not file.endswith('.ms') or not script:
            raise ValueError('This command publishes nonempty scripts only; review group edits separately')
        for field in ['id', 'groupId', 'path', 'method']:
            if meta.get(field) != previous_meta.get(field):
                raise ValueError('Identity/route change requires separate review: ' + field)
        # Authenticate the target runtime and prove it points at this resource before DB writes.
        loaded = runtime_call(config, '/api/web/resource/file/' + urllib.parse.quote(meta['id'], safe=''))
        verify_runtime_magic(loaded, previous_meta, metadata(remote['content'])[1])
    else:
        if not compiled:
            raise ValueError('Low-code publication requires --compiled from tools/compile-lowcode.mjs')
        bundle = read_json(Path(compiled))
        if (bundle.get('componentId') != entry['id'] or bundle.get('sourceSha256') != digest(local)
                or not isinstance(bundle.get('compileJs'), str) or not isinstance(bundle.get('compileCss'), str)
                or 'return __sfc__' not in bundle['compileJs']):
            raise ValueError('Compilation does not match current source/runtime factory')
        runtime_source = runtime_call(config, '/sym/component?action=getSourceCode&tid=' + urllib.parse.quote(entry['id'], safe=''), {})
        if digest(runtime_source) != digest(remote['content']):
            raise ValueError('Runtime source differs from control database; do not publish')
    # Private, ignored rollback material: never a second versioned source location.
    backup_name = kind + '-' + digest(entry['path']) + '-' + hashlib.sha256(remote['content'].encode('utf-8')).hexdigest() + '.json'
    backup = safe_path(ROOT / 'logs', 'resources/backups/' + backup_name)
    write_json(backup, remote)
    connection.rollback()
    try:
        if kind == 'magic':
            # Lock the current registry while checking global metadata-ID uniqueness.
            # This current read catches row replacement after the earlier read/runtime check.
            # SERIALIZABLE also protects the range against a new duplicate-ID row
            # when the server's default isolation would otherwise allow phantoms.
            with connection.cursor() as q:
                q.execute('SET TRANSACTION ISOLATION LEVEL SERIALIZABLE')
            locked = validate_identity(manifest, snapshot(connection, kind, for_update=True), entry, kind)
            if locked['content'] != remote['content']:
                raise ValueError('Concurrent change prevented atomic publication')
            with connection.cursor() as q:
                q.execute('UPDATE api_file_t SET file_content=%s,updated_time=CURRENT_TIMESTAMP '
                          'WHERE file_path=%s AND is_del=0 AND BINARY file_content=BINARY %s',
                          (local, entry['databasePath'], remote['content']))
                if q.rowcount != len(entry['rowIds']):
                    raise ValueError('Concurrent change prevented atomic publication')
        else:
            # Keep existing history semantics in the same transaction as a guarded source update.
            import uuid
            with connection.cursor() as q:
                q.execute('SELECT tid,pid,name,type,tenant_id,source_code FROM ui_component_t '
                          'WHERE tid=%s AND is_del=0 FOR UPDATE', (entry['id'],))
                locked = q.fetchone()
                if (not locked or (locked['source_code'] or '') != remote['content']
                        or (locked['tid'], locked['pid'], locked['name'], str(locked['type']))
                        != (entry['id'], entry['parentId'], entry['name'], entry['type'])):
                    raise ValueError('Concurrent change prevented atomic publication')
                q.execute('INSERT INTO ui_component_history_t '
                      '(tid,tenant_id,component_id,source_code,created_time,updated_time,created_by,updated_by,is_del) '
                      'VALUES (%s,%s,%s,%s,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,%s,%s,0)',
                      (uuid.uuid4().hex, locked['tenant_id'], entry['id'], local, '1', '1'))
                q.execute('UPDATE ui_component_t SET source_code=%s,compile_js=%s,compile_css=%s,'
                      'updated_time=CURRENT_TIMESTAMP,updated_by=%s WHERE tid=%s AND is_del=0',
                      (local, bundle['compileJs'], bundle['compileCss'], '1', entry['id']))
                if q.rowcount != 1:
                    raise ValueError('Concurrent change prevented atomic publication')
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    try:
        if kind == 'magic':
            runtime_call(config, '/sym/node-config/magic-resource/refresh', {})
            loaded = runtime_call(config, '/api/web/resource/file/' + urllib.parse.quote(meta['id'], safe=''))
            verify_runtime_magic(loaded, meta, script)
        else:
            runtime_call(config, '/sym/component/cache/refresh', {})
            if digest(runtime_call(config, '/sym/component?action=getSourceCode&tid=' + urllib.parse.quote(entry['id'], safe=''), {})) != digest(local):
                raise ValueError('Low-code source verification failed')
            items = runtime_call(config, '/sym/component?action=list', {})
            candidates = [r for r in items if r['name'] == entry['name']]
            item = candidates[0] if len(candidates) == 1 else None
            if not item or item.get('compileJs') != bundle['compileJs'] or item.get('compileCss') != bundle['compileCss']:
                raise ValueError('Low-code runtime bundle verification failed')
    except Exception as exc:
        raise ValueError('Database saved, runtime verification incomplete. Inspect ignored backup and refresh: '
                         + type(exc).__name__) from None
    entry['baseSha256'] = digest(local)
    write_json(ROOT / kind / '.manifest.json', manifest)
    return {'file': file, 'result': 'published-and-runtime-verified'}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('command', choices=['pull', 'status', 'plan', 'publish'])
    p.add_argument('--kind', choices=['all', 'magic', 'lowcode'], default='all')
    p.add_argument('--config', type=Path, default=PRIVATE_CONFIG)
    p.add_argument('--file', action='append')
    p.add_argument('--compiled', type=Path)
    p.add_argument('--apply', action='store_true')
    args = p.parse_args()
    if args.command == 'publish' and (not args.apply or not args.file or len(args.file) != 1):
        p.error('publish requires --apply and exactly one --file; use plan to review first')
    config = read_json(args.config)
    connection = connect(config)
    try:
        if args.command == 'publish':
            result = publish(connection, config, args.file[0], args.compiled)
        else:
            kinds = ['magic', 'lowcode'] if args.kind == 'all' else [args.kind]
            result = [pull(connection, k) if args.command == 'pull' else status(connection, k, args.file)
                      for k in kinds]
        print(json.dumps(result, ensure_ascii=False, indent=2))
    finally:
        connection.close()


if __name__ == '__main__':
    try:
        main()
    except Exception as exc:
        # Driver exceptions can contain connection details; only deliberate validation text is public.
        print(str(exc) if isinstance(exc, (ValueError, FileNotFoundError)) else
              'Resource operation failed (' + type(exc).__name__ + '); inspect private connection settings.',
              file=sys.stderr)
        sys.exit(1)

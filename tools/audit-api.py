"""Read-only Magic reference inventory. No absence of a literal authorizes deletion.

Reports belong in logs/. Optional context contains current compiled components,
menus, forms and runtime configuration read from the selected environment; never
write their raw contents into a report or Git.
"""
import argparse
from collections import Counter
from functools import lru_cache
import importlib.util
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('resource_sync', ROOT / 'tools/resource-sync.py')
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)
APPS = ['data-elements-chengtian', 'data-elements-haitong',
        'data-elements-idaas', 'data-elements-wanxiang', 'data-elements-parent']
EXTENSIONS = {'.vue', '.ts', '.tsx', '.js', '.jsx', '.java', '.json', '.yaml',
              '.yml', '.xml', '.properties', '.html'}


def executable_text(text):
    # Preserve quoted strings/templates and newlines while removing comments.
    tokens = re.compile(r'''("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)|//[^\n]*|/\*[\s\S]*?\*/''')
    return tokens.sub(lambda m: m.group(0) if m.group(1)
                      else '\n' * m.group(0).count('\n'), text)


def resolved_literals(text):
    clean = executable_text(text)
    strings = [(m.start(), next(g for g in m.groups() if g is not None)) for m in
               re.finditer(r'''"((?:\\.|[^"\\])*)"|'((?:\\.|[^'\\])*)'|`((?:\\.|[^`\\])*)`''', clean)]
    constants = {}
    for m in re.finditer(r'''\b(?:const|let|var|String)\s+(\w+)\s*=\s*(["'`])([^\n]*?)\2''', clean):
        if '${' not in m.group(3):
            constants[m.group(1)] = m.group(3)
    result = []
    for offset, value in strings:
        value = re.sub(r'\$\{(\w+)\}', lambda m: constants.get(m.group(1), m.group(0)), value)
        # BASE_URL + '/page' is common in frontend API modules.
        prefix = re.search(r'(\w+)\s*\+\s*$', clean[max(0, offset - 100):offset])
        if prefix and prefix.group(1) in constants:
            value = constants[prefix.group(1)].rstrip('/') + '/' + value.lstrip('/')
        result.append((offset, value))
        # An unresolved environment/proxy base does not change a known API path.
        tail = re.match(r'^\$\{[^}]+\}(/[^\s]+)$', value)
        if tail:
            result.append((offset, tail.group(1)))
    return clean, result


@lru_cache(maxsize=2048)
def route_pattern(route):
    # Exact static routes; placeholders may match a single path segment.
    parts = re.split(r'(\{[^}]+\}|:\w+|\*\*)', route)
    return re.compile('^' + ''.join('[^/?#]+' if p.startswith(('{', ':'))
                      else '.*' if p == '**' else re.escape(p) for p in parts) + '$')


@lru_cache(maxsize=32768)
def match_reference(route, literal):
    value = request_path(literal)
    if route_pattern(route).fullmatch(value):
        return 'literal'
    # Unresolved request values are uncertainty, never positive usage evidence.
    if '${' in value:
        prefix = value.split('${', 1)[0]
        if len(prefix.rstrip('/').split('/')) >= 3 and route.startswith(prefix):
            return 'dynamicPrefix'
    return None


def request_path(value):
    value = re.sub(r'^https?://[^/]+', '', value)
    value = re.sub(r'^/(?:dev-api|api)(?=/)', '', value)
    depth = 0
    for index, char in enumerate(value):
        if char == '{':
            depth += 1
        elif char == '}':
            depth = max(0, depth - 1)
        elif char in '?#' and not depth:
            return value[:index]
    return value


def template_matches(route, template, literal_values):
    value = request_path(template)
    pieces = re.split(r'(\$\{[^{}]+\})', value)
    if len(pieces) == 1:
        return False
    pattern = '^' + ''.join('([^/]+)' if p.startswith('${') else re.escape(p) for p in pieces) + '$'
    match = re.fullmatch(pattern, route)
    if not match:
        return False
    expressions = [p for p in pieces if p.startswith('${')]
    for expression, segment in zip(expressions, match.groups()):
        alternatives = re.findall(r'''["']([a-zA-Z][\w-]*)["']''', expression)
        if segment not in (alternatives or literal_values):
            return False
    return True


def inventory():
    entries = sync.load_manifest('magic')['resources']
    resources, groups = [], {}
    for entry in entries:
        if entry.get('kind') == 'directory' or entry['path'].split('/')[0] not in {'api', 'function', 'task'}:
            continue
        path = sync.safe_path(ROOT / 'magic', entry['path'])
        meta, script = sync.metadata(sync.read_source(path))
        item = {'id': meta.get('id'), 'file': 'magic/' + entry['path'],
                'metadata': meta, 'script': script, 'section': entry['path'].split('/')[0]}
        if path.name == 'group.json':
            groups[item['id']] = item
        else:
            resources.append(item)

    def prefix(group_id, chain=()):
        if group_id in {None, '', '0', 'api:0', 'function:0', 'task:0'}:
            return ''
        if group_id in chain or group_id not in groups:
            raise ValueError('Broken Magic group graph: ' + str(group_id))
        meta = groups[group_id]['metadata']
        return prefix(meta.get('parentId'), chain + (group_id,)).rstrip('/') + '/' + str(meta.get('path') or '').strip('/')

    for item in resources:
        meta = item['metadata']
        item['route'] = '/' + '/'.join(p for p in
                          (prefix(meta.get('groupId')) + '/' + str(meta.get('path') or '')).split('/') if p)
        item['method'] = str(meta.get('method') or '').upper()
    return resources


def corpus(context=None):
    docs = []
    for app in APPS:
        for path in sorted((ROOT / app / 'src').rglob('*')):
            if path.is_file() and path.suffix.lower() in EXTENSIONS and not any(
                    part in {'test', 'tests', '__tests__'} for part in path.parts):
                docs.append({'label': path.relative_to(ROOT).as_posix(), 'text': sync.read_source(path)})
    for entry in sync.load_manifest('lowcode')['resources']:
        if entry['type'] == '1':
            docs.append({'label': 'lowcode/' + entry['path'],
                         'text': sync.read_source(sync.safe_path(ROOT / 'lowcode', entry['path']))})
    if context:
        docs.extend(json.loads(context.read_text(encoding='utf-8-sig')))
    return docs


def audit(context=None):
    resources = inventory()
    apis = [r for r in resources if r['section'] == 'api']
    route_ids = {}
    for item in apis:
        route_ids.setdefault(item['route'], []).append(item['id'])
    patterns = [item for item in apis if '{' in item['route'] or ':' in item['route']]
    roots = sorted({r['route'].split('/')[1] for r in apis if r['route'] != '/'})
    broad_routes = re.compile('/(?:' + '|'.join(re.escape(r) for r in roots) + r')(?:/[A-Za-z0-9_{}$.:=-]+)*')
    documents = corpus(context)
    refs = {r['id']: [] for r in apis}
    uncertain = {r['id']: [] for r in apis}
    for doc in documents:
        clean, literals = resolved_literals(doc['text'])
        literal_values = {value for _, value in literals if re.fullmatch(r'[a-zA-Z][\w-]{0,63}', value)}
        hits, dynamic = {}, {}
        # Vue templates, regular-expression literals and minified output can put
        # quotes in positions a lightweight string scanner cannot parse. A second
        # conservative scan retains exact route text rather than risking deletion.
        for occurrence in broad_routes.finditer(clean):
            value = request_path(occurrence.group(0))
            for identity in route_ids.get(value, ()):
                hits.setdefault(identity, set()).add(clean.count('\n', 0, occurrence.start()) + 1)
        for offset, literal in literals:
            if not literal.startswith(('/', 'http://', 'https://', '${')):
                continue
            value = request_path(literal)
            line = clean.count('\n', 0, offset) + 1
            for identity in route_ids.get(value, ()):
                hits.setdefault(identity, set()).add(line)
            for item in patterns:
                if match_reference(item['route'], literal) == 'literal':
                    hits.setdefault(item['id'], set()).add(line)
            if '${' in value:
                prefix = value.split('${', 1)[0]
                if len(prefix.rstrip('/').split('/')) >= 2:
                    for item in apis:
                        if template_matches(item['route'], value, literal_values):
                            hits.setdefault(item['id'], set()).add(line)
                        elif item['route'].startswith(prefix):
                            dynamic.setdefault(item['id'], set()).add(line)
        # Database service registries and Java dispatch can select metadata IDs.
        for _, literal in literals:
            if literal in refs and (doc['label'].startswith('runtime-java/') or
                    any(table in doc['label'] for table in ['/fs_publish_record/', '/api_info_t/', '/api_job_t/'])):
                hits.setdefault(literal, set()).add(1)
        for identity, lines in hits.items():
            refs[identity].append({'source': doc['label'], 'lines': sorted(lines)})
        for identity, lines in dynamic.items():
            uncertain[identity].append({'source': doc['label'], 'lines': sorted(lines)})
    edges = {}
    functions = [r for r in resources if r['section'] == 'function']
    for origin in resources:
        clean, literals = resolved_literals(origin['script'])
        target_ids = set()
        for imported in re.findall(r'@(?:get|post|put|patch|delete|head|options):([^\s;"\'(),]+)', clean, re.I):
            target_ids.update(route_ids.get(imported, ()))
        for _, value in literals:
            if not value.startswith(('/', 'http://', 'https://')):
                continue
            normalized = request_path(value)
            target_ids.update(route_ids.get(normalized, ()))
            target_ids.update(r['id'] for r in patterns if match_reference(r['route'], value) == 'literal')
        target_ids.discard(origin['id'])
        # Named function imports (transitive functions also retain their API calls).
        for target in functions:
            name = target['route'].strip('/')
            if name and re.search(r'''\bimport\s+["']?(?:@/?)?''' + re.escape(name) + r'(?![\w/.-])', clean):
                target_ids.add(target['id'])
        edges[origin['id']] = target_ids
    reachable = {r['id'] for r in apis if refs[r['id']]}
    # Scheduled jobs and functions called by Java are additional runtime roots.
    all_external = '\n'.join(executable_text(d['text']) for d in documents)
    reachable.update(r['id'] for r in resources if r['section'] == 'task')
    reachable.update(r['id'] for r in resources if r['section'] == 'function' and
                     r['route'].strip('/') and r['route'].strip('/') in all_external)
    changed = True
    while changed:
        before = len(reachable)
        reachable.update(t for source in tuple(reachable) for t in edges.get(source, ()))
        changed = before != len(reachable)
    incoming = {r['id']: [] for r in apis}
    by_id = {r['id']: r for r in resources}
    for source, targets in edges.items():
        for target in targets:
            if target in incoming:
                incoming[target].append({'source': by_id[source]['file'], 'reachable': source in reachable})
    result = []
    for item in apis:
        identity = item['id']
        state = 'literalReference' if refs[identity] else 'indirectReference' if identity in reachable else 'dynamicReview' if uncertain[identity] else 'noReferenceFound'
        result.append({'id': identity, 'file': item['file'], 'method': item['method'],
                       'route': item['route'], 'classification': state,
                       'references': refs[identity], 'dynamicReferences': uncertain[identity],
                       'magicReferences': incoming[identity]})
    duplicates = Counter((r['method'], r['route']) for r in apis)
    return {'scope': APPS + ['lowcode source'] + (['selected live context'] if context else []),
            'limits': ['Literal presence is a reference candidate, not proof of a live request.',
                       'Dynamic expressions, Java dispatch, external callers and runtime bindings need review.',
                       'No automatic deletion; scheduled and indirect dependencies are retained.'],
            'counts': dict(Counter(r['classification'] for r in result)),
            'apiCount': len(apis), 'sourceCount': len(documents),
            'duplicateRoutes': [{'method': method, 'route': route, 'count': count}
                                for (method, route), count in duplicates.items() if count > 1],
            'resources': result}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--context', type=Path, help='Private live texts; never exported into the report')
    parser.add_argument('--output', type=Path, default=ROOT / 'logs/api-audit/references.json')
    args = parser.parse_args()
    output = args.output.resolve()
    if not output.is_relative_to((ROOT / 'logs').resolve()):
        parser.error('Reports must be written below the ignored root logs directory')
    report = audit(args.context)
    sync.write_json(output, report)
    print(json.dumps({key: report[key] for key in ['apiCount', 'sourceCount', 'counts', 'duplicateRoutes']}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()

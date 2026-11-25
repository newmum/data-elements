"""Build a HaiTong frontend addition for the frozen police MySQL R3 deployment."""
import argparse
import hashlib
import importlib.util
import json
import re
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / 'tools/police-deployment/mysql-haitong'
MODULE = 'www/static/capabilityCenters-Lv7nYHxA.js'
ORIGINAL_BINDING = 'haitong:"/haitong/".trim()||"/haitong/"'
DEPLOYED_BINDING = 'haitong:new URL("./haitong/",window.location.href).pathname'


def component_row(sql):
    for line in sql.splitlines():
        if not line.startswith('INSERT INTO `ui_component_t`'): continue
        left, values = line.split(' VALUES (', 1)
        columns = re.findall(r'`([^`]+)`', left)[1:]
        cells = []
        for cell in values[:-2].split(','):
            match = re.fullmatch(r'CONVERT\(0x([0-9a-fA-F]+) USING utf8mb4\)', cell)
            if match: cells.append(bytes.fromhex(match.group(1)).decode())
            elif cell == 'NULL': cells.append(None)
            elif cell == "''": cells.append('')
            else: raise ValueError('无法识别R3组件初始化格式')
        row = dict(zip(columns, cells))
        if row.get('name') == 'schedule-setting': return row
    raise ValueError('R3未包含任务配置组件')


def build(base, frontend, output, component):
    if not (frontend / 'index.html').is_file() or not (frontend / 'assets').is_dir():
        raise ValueError('缺少已构建的海通前端')
    index = (frontend / 'index.html').read_text(encoding='utf8')
    if 'src="./assets/' not in index or '海通数据集成中心' not in index:
        raise ValueError('海通前端必须使用--base=./构建')
    scripts = '\n'.join(p.read_text(encoding='utf8') for p in (frontend / 'assets').glob('*.js'))
    if not all(value in scripts for value in ('police-mysql', '../prod-api', '../prod-api/nifi', '../#/')):
        raise ValueError('缺少公安R3的会话命名空间或相对API/登录地址，请按部署文档重新构建海通')
    payload = {'www-haitong/' + p.relative_to(frontend).as_posix(): p.read_bytes()
               for p in frontend.rglob('*') if p.is_file()}
    with ZipFile(base) as archive:
        binding = archive.read(MODULE).decode()
        if binding.count(ORIGINAL_BINDING) != 1:
            raise ValueError('不是支持的公安MySQL R3前端，拒绝猜测覆盖')
        payload[MODULE] = binding.replace(ORIGINAL_BINDING, DEPLOYED_BINDING).encode()
        prior_binding = payload[MODULE]
        alias = re.search(r'export\{(\w+) as A,', binding)
        if not alias or 'getAccessToken()' not in binding:
            raise ValueError('无法核验R3宿主的AuthStorage导出')
        payload[MODULE] += ('\nwindow.__DATA_ELEMENTS_GET_PLATFORM_TOKEN__=()=>'+alias.group(1)+'.getAccessToken();\n').encode()
        old_component = component_row(archive.read('db/03-resources.sql').decode())
        original = {MODULE: hashlib.sha256(archive.read(MODULE)).hexdigest(),
                    'config/Caddyfile': hashlib.sha256(archive.read('config/Caddyfile')).hexdigest()}
        for path in ('start.sh', 'stop.sh', 'status.sh', 'tools/standalone.py'):
            original[path] = hashlib.sha256(archive.read(path)).hexdigest()
    spec = importlib.util.spec_from_file_location('external_overlay', ROOT / 'tools/package-police-external-overlay.py')
    external = importlib.util.module_from_spec(spec); spec.loader.exec_module(external)
    work = ROOT / 'logs/police-haitong-overlay/external'
    work.mkdir(parents=True, exist_ok=True)
    dependency = work / 'external-overlay.zip'
    external.build(dependency)
    with ZipFile(dependency) as archive:
        for name in archive.namelist():
            if name != 'external-overlay-manifest.json':
                payload[name] = archive.read(name)
    payload['config/Caddyfile'] = (TEMPLATE / 'Caddyfile').read_bytes().replace(b'\r\n', b'\n')
    manifest = {'format': 1, 'target': 'police-mysql-linux-x86_64-20261122-r3',
                'haitongSourceCommit': 'dcc7f6db', 'managedServices': ['web', 'backend'],
                'storagePrefix': 'police-mysql', 'apiBase': '../prod-api', 'platformHome': '../#/',
                'middlewareBinaries': False, 'databaseWrites': 'selected schedule-setting component only',
                'expectedOriginals': original,
                'acceptedOriginals': {MODULE: [original[MODULE], hashlib.sha256(prior_binding).hexdigest()]},
                'files': {name: hashlib.sha256(data).hexdigest() for name, data in payload.items()}}
    files = {'payload/' + name: value for name, value in payload.items()}
    files.update({'install.py': (TEMPLATE / 'install.py').read_bytes().replace(b'\r\n', b'\n'),
                  'install.sh': b'#!/usr/bin/env bash\nset -euo pipefail\nHERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"\nexec python3 "$HERE/install.py" --target "${1:-$PWD}"\n',
                  'manifest.json': (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode(),
                  '安装说明.txt': (TEMPLATE / '安装说明.txt').read_bytes().replace(b'\r\n', b'\n')})
    bundle = json.loads(component.read_text(encoding='utf8'))
    source = (ROOT / 'lowcode/03.数据接入/2.3.1 数据汇聚(data-convergence)/任务配置(schedule-setting).vue').read_bytes()
    if bundle['componentId'] != old_component['tid'] or hashlib.sha256(source.replace(b'\r\n', b'\n')).hexdigest() != bundle['sourceSha256']:
        raise ValueError('组件身份或源码与编译结果不一致')
    selected = {'component/source.vue': source, 'component/script.js': bundle['compileJs'].encode(),
                'component/style.css': bundle['compileCss'].encode()}
    properties = ['id='+old_component['tid']]
    for i, (column, name) in enumerate(zip(('source_code', 'compile_js', 'compile_css'), selected)):
        old = old_component[column]
        properties.append('before.'+str(i)+'='+('-' if old is None else hashlib.sha256(old.encode()).hexdigest()))
        properties.append('after.'+str(i)+'='+hashlib.sha256(selected[name]).hexdigest())
    selected['component/component.properties'] = ('\n'.join(properties)+'\n').encode()
    selected['ApplyCanvasComponent.java'] = (TEMPLATE / 'ApplyCanvasComponent.java').read_bytes().replace(b'\r\n', b'\n')
    files.update(selected)
    manifest['componentFiles'] = {name: hashlib.sha256(data).hexdigest() for name, data in selected.items()}
    files['manifest.json'] = (json.dumps(manifest, ensure_ascii=False, indent=2)+'\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(output, 'w', ZIP_DEFLATED) as archive:
        for name, data in files.items():
            info = ZipInfo(name); info.create_system = 3; info.compress_type = ZIP_DEFLATED
            info.external_attr = (0o100755 if name.endswith('.sh') else 0o100644) << 16
            archive.writestr(info, data)
    with ZipFile(output) as archive:
        if archive.testzip() is not None: raise ValueError('ZIP校验失败')
        for name, digest in manifest['files'].items():
            if hashlib.sha256(archive.read('payload/' + name)).hexdigest() != digest:
                raise ValueError('补丁内容校验失败')
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    (output.parent / 'SHA256SUMS.txt').write_text(digest + '  ' + output.name + '\n', encoding='ascii')
    (output.parent / '安装说明.txt').write_bytes(files['安装说明.txt'])
    return {'file': str(output), 'bytes': output.stat().st_size, 'sha256': digest,
            'frontendFiles': sum(name.startswith('www-haitong/') for name in payload),
            'payloadFiles': len(payload)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--frontend', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--component', type=Path, required=True, help='当前任务配置组件的compile-lowcode输出')
    args = parser.parse_args()
    print(json.dumps(build(args.base, args.frontend, args.output, args.component), ensure_ascii=False, indent=2))

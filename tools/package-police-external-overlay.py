"""Build a script-only overlay for the police MySQL R3 package; never connect to a server."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
TEMPLATE = ROOT / 'tools/police-deployment/mysql-external'


def build(output):
    files = {
        'tools/standalone.py': (TEMPLATE / 'standalone.py').read_bytes(),
        'config/external-services.json.example': (TEMPLATE / 'external-services.json.example').read_bytes(),
        '外部中间件补丁说明.txt': (TEMPLATE / '部署说明.txt').read_bytes(),
    }
    for action in ('start', 'stop', 'status', 'check'):
        files[action + '.sh'] = ('#!/usr/bin/env bash\nset -euo pipefail\n'
                                'cd -- "$(dirname -- "${BASH_SOURCE[0]}")"\n'
                                'exec python3 tools/standalone.py ' + action + '\n').encode()
    files = {name: data.replace(b'\r\n', b'\n') for name, data in files.items()}
    manifest = {'format': 1, 'target': 'police-mysql-linux-x86_64-20261122-r3',
                'managedServices': ['web', 'backend'], 'automaticDatabaseInitialization': False,
                'files': {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}}
    files['external-overlay-manifest.json'] = (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode()
    output.parent.mkdir(parents=True, exist_ok=True)
    with ZipFile(output, 'w', ZIP_DEFLATED) as archive:
        for name, data in files.items():
            info = ZipInfo(name)
            info.create_system = 3
            info.compress_type = ZIP_DEFLATED
            info.external_attr = (0o100755 if name.endswith('.sh') else 0o100644) << 16
            archive.writestr(info, data)
    with ZipFile(output) as archive:
        if archive.testzip() is not None:
            raise ValueError('ZIP校验失败')
        for name, sha in manifest['files'].items():
            if hashlib.sha256(archive.read(name)).hexdigest() != sha:
                raise ValueError('文件校验失败')
    sha = hashlib.sha256(output.read_bytes()).hexdigest()
    (output.parent / 'SHA256SUMS.txt').write_text(sha + '  ' + output.name + '\n', encoding='ascii')
    (output.parent / '使用说明.txt').write_bytes(files['外部中间件补丁说明.txt'])
    return {'path': str(output), 'bytes': output.stat().st_size, 'sha256': sha,
            'files': sorted(files)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    print(json.dumps(build(parser.parse_args().output), ensure_ascii=False, indent=2))

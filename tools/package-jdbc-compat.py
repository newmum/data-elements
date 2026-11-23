#!/usr/bin/env python3
"""Package a selected pure-JDK compatibility driver as a separately managed NiFi JDBC asset."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--driver', choices=('hive', 'oracle'), default='hive')
    parser.add_argument('--classes', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    class_name = 'NifiHiveJdbcDriver' if args.driver == 'hive' else 'NifiOracleJdbcDriver'
    prefix = 'com/linewell/dataelement/metautil/jdbc/' + class_name
    source = (ROOT/'data-elements-parent/src/main/java'/f'{prefix}.java').read_text(encoding='utf-8')
    name = re.search(r'ASSET_NAME\s*=\s*"([^"]+)"', source).group(1)
    match = re.fullmatch(r'haitong-' + args.driver + r'-jdbc-compat-(\d+\.\d+\.\d+)\.jar', name)
    if not match:
        raise ValueError('Asset declaration does not match the selected driver')
    version = match.group(1)
    output = args.output.resolve()
    if output.name != name or (ROOT/'logs').resolve() not in output.parents:
        raise ValueError('Use the declared asset filename inside the ignored workspace logs directory')
    classes = args.classes.resolve()
    files = sorted((classes/prefix).parent.glob(class_name + '*.class'))
    if not files or not (classes/f'{prefix}.class').is_file():
        raise ValueError('Compile and test the selected JDBC compatibility driver before packaging')
    entries = {p.relative_to(classes).as_posix(): p.read_bytes() for p in files}
    if any(not data.startswith(b'\xca\xfe\xba\xbe') for data in entries.values()):
        raise ValueError('Invalid compiled Java class')
    title = 'Haitong Hive JDBC metadata compatibility' if args.driver == 'hive' else 'Haitong Oracle JDBC vendor type compatibility'
    entries['META-INF/MANIFEST.MF'] = (f'Manifest-Version: 1.0\r\nImplementation-Title: {title}\r\n'
                                       f'Implementation-Version: {version}\r\n\r\n').encode('ascii')
    entries['META-INF/services/java.sql.Driver'] = (prefix.replace('/', '.')+'\n').encode('ascii')
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, 'x', compression=zipfile.ZIP_DEFLATED) as jar:
        for path, data in entries.items():
            entry = zipfile.ZipInfo(path, date_time=(2020, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            jar.writestr(entry, data)
    with zipfile.ZipFile(output) as jar:
        if set(jar.namelist()) != set(entries) or any(jar.read(path) != data for path, data in entries.items()):
            raise ValueError('JDBC compatibility asset content verification failed')
    print(json.dumps({'asset': str(output), 'version': version, 'classes': len(files), 'bytes': output.stat().st_size,
                      'sha256': hashlib.sha256(output.read_bytes()).hexdigest(), 'vendorDriverBundled': False,
                      'configurationBundled': False, 'nifiExtensionChanged': False, 'verified': True}))


if __name__ == '__main__':
    main()

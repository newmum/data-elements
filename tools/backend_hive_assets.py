"""Validate isolated Apache Hive assets in a normal executable backend JAR."""
import argparse
import hashlib
import io
import importlib.util
import json
from pathlib import Path
import re
import shutil
from zipfile import ZipFile, ZIP_STORED

ROOT = Path(__file__).resolve().parents[1]
RESOURCE = 'jdbc-clients/open-source-hive/'


def properties(content):
    return dict(line.split('=', 1) for line in content.decode('ascii').splitlines()
                if line.strip() and not line.lstrip().startswith(('#', '!')))


def verify_bundled_hive_client(backend):
    """Fail before packaging/deployment if payloads are missing, stale or corrupt."""
    expected = properties((ROOT / 'data-elements-parent/src/main/resources' /
                           RESOURCE / 'client.properties').read_bytes())
    prefix = 'BOOT-INF/classes/' + RESOURCE
    with ZipFile(backend) as jar:
        if prefix + 'client.properties' not in jar.namelist():
            raise ValueError('Backend is missing the isolated Apache Hive client; build a complete JAR from current Maven source')
        if properties(jar.read(prefix + 'client.properties')) != expected:
            raise ValueError('Backend Apache Hive manifest differs from current source; rebuild with Maven')
        for name, digest in expected.items():
            if name == 'driver':
                continue
            data = jar.read(prefix + name)
            if hashlib.sha256(data).hexdigest() != digest:
                raise ValueError('Backend Apache Hive payload digest mismatch: ' + name)
            with ZipFile(io.BytesIO(data)) as client:
                if client.testzip() is not None:
                    raise ValueError('Corrupt Apache Hive payload: ' + name)
        if any(name.startswith('BOOT-INF/lib/hive-jdbc-') and '-standalone.' in name
               for name in jar.namelist()):
            raise ValueError('Apache Hive standalone must stay isolated from Huawei BOOT-INF/lib')
        for name in ('BundledHiveClient', 'HiveKerberosSupport', 'NifiHiveJdbcDriver'):
            jar.getinfo('BOOT-INF/classes/com/linewell/dataelement/metautil/jdbc/' + name + '.class')
    return {'verified': True, 'driver': expected['driver'],
            'isolatedFiles': {k: v for k, v in expected.items() if k != 'driver'}}


def export_nifi_client(backend, output):
    """Export the same tested client and JDK-only shim; installation never edits flows."""
    verified = verify_bundled_hive_client(backend)
    source = (ROOT / 'data-elements-parent/src/main/java/com/linewell/dataelement/metautil/jdbc/NifiHiveJdbcDriver.java').read_text(encoding='utf8')
    asset = re.search(r'public static final String ASSET_NAME\s*=\s*"([^"]+)"', source).group(1)
    version = re.fullmatch(r'haitong-hive-jdbc-compat-(\d+\.\d+\.\d+)\.jar', asset).group(1)
    bundle = 'hive-jdbc-kerberos-' + version
    output = output.resolve()
    if (ROOT / 'logs').resolve() not in output.parents:
        raise ValueError('Export release artifacts to the ignored workspace logs directory')
    output.mkdir(parents=True, exist_ok=False)
    stage = output / bundle
    drivers = stage / 'drivers/open-source-hive'
    drivers.mkdir(parents=True)
    package = 'com/linewell/dataelement/metautil/jdbc/'
    with ZipFile(backend) as jar:
        for name in verified['isolatedFiles']:
            (drivers / name).write_bytes(jar.read('BOOT-INF/classes/' + RESOURCE + name))
        entries = {name.removeprefix('BOOT-INF/classes/'): jar.read(name)
                   for name in jar.namelist() if name.startswith(tuple(
                       'BOOT-INF/classes/' + package + prefix for prefix in ('NifiHiveJdbcDriver', 'HiveKerberosSupport')))
                   and name.endswith('.class')}
        if asset.encode('ascii') not in entries[package + 'NifiHiveJdbcDriver.class']:
            raise ValueError('Backend compatibility asset version differs from source; rebuild')
    spec = importlib.util.spec_from_file_location('jdbc_compat_packager', ROOT / 'tools/package-jdbc-compat.py')
    packager = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(packager)
    packager.write_asset(entries, drivers / asset, 'hive', version)
    native = ['drivers/open-source-hive/' + name for name in verified['isolatedFiles']]
    selected = ['drivers/open-source-hive/' + asset] + native
    manifest = {'bundleId': bundle, 'files': {}, 'profiles': {
        'HIVE_OPEN_SOURCE': {'class': package.replace('/', '.') + 'NifiHiveJdbcDriver',
                             'files': selected, 'urlExample': 'jdbc:haitong:hive2://localhost:10000/default',
                             'note': 'Kerberos: principal/keytab/krb5.conf must exist on the NiFi execution host'},
        'HIVE_OPEN_SOURCE_NATIVE': {'class': 'org.apache.hive.jdbc.HiveDriver', 'files': native,
                                    'urlExample': 'jdbc:hive2://localhost:10000/default'}}}
    for path in sorted(drivers.iterdir()):
        manifest['files'][path.relative_to(stage).as_posix()] = {
            'sha256': hashlib.sha256(path.read_bytes()).hexdigest(), 'bytes': path.stat().st_size}
    (stage / 'manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf8')
    for name in ('install-jdbc.py', 'install-jdbc.sh'):
        shutil.copy2(ROOT / 'tools/nifi-deployment/jdbc' / name, stage / name)
    archive = output / (bundle + '.zip')
    with ZipFile(archive, 'x', ZIP_STORED) as jar:
        for path in sorted(stage.rglob('*')):
            if path.is_file():
                jar.write(path, bundle + '/' + path.relative_to(stage).as_posix())
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    (output / 'SHA256SUMS.txt').write_text(digest + '  ' + archive.name + '\n', encoding='ascii')
    return {'archive': str(archive), 'sha256': digest, 'compatibilityVersion': version,
            'existingFlowsModified': False, 'credentialsBundled': False}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--nifi-output', type=Path, help='Export a fresh, credential-free NiFi client bundle under logs')
    args = parser.parse_args()
    result = (export_nifi_client(args.jar, args.nifi_output) if args.nifi_output
              else verify_bundled_hive_client(args.jar))
    print(json.dumps(result, ensure_ascii=False, indent=2))

#!/usr/bin/env python3
"""Install the reviewed GaussDB 505.2.1.SPC0800 JDBC pair; default is read-only.

Keep native GaussDB, openGauss and PostgreSQL/DWS drivers separate. The JRE7,
gsjdbc4 and gsjdbc200 compatibility packages are not put on the shared classpath.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

APPROVED = {
    'gaussdbjdbc.jar': {
        'sha256': 'b2ff93c2db4bad4693d5e1b471b470a35ede36489c96efc93de714013c229441',
        'driver': 'com.huawei.gaussdb.jdbc.Driver',
    },
    'opengaussjdbc.jar': {
        'sha256': '2fb069dc0e6ab5d727738d810f126ab040ae96f8010c56b0c400e1fbe86bc711',
        'driver': 'com.huawei.opengauss.jdbc.Driver',
    },
}

MAVEN_VERSION = '5.0.0-htrunk4.csi.gaussdb_kernel.opengaussjdbc.r15'
MAVEN_ARTIFACTS = {
    'gaussdbjdbc.jar': ('com.huawei.gaussdb', 'gaussdbjdbc'),
    'opengaussjdbc.jar': ('org.opengauss', 'opengauss-jdbc'),
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def reviewed_files(archive, approved=None):
    approved = APPROVED if approved is None else approved
    files = {}
    with zipfile.ZipFile(archive) as source:
        for name, rule in approved.items():
            entries = [entry for entry in source.infolist()
                       if not entry.is_dir() and entry.filename.replace('\\', '/').split('/')[-1] == name]
            if len(entries) != 1 or entries[0].file_size > 32 * 1024 * 1024:
                raise ValueError('Missing, duplicate or oversized approved driver: ' + name)
            data = source.read(entries[0])
            if digest(data) != rule['sha256']:
                raise ValueError('Driver differs from the reviewed attachment: ' + name)
            with zipfile.ZipFile(io.BytesIO(data)) as jar:
                if rule['driver'].replace('.', '/') + '.class' not in jar.namelist():
                    raise ValueError('Expected driver class is absent: ' + name)
                providers = jar.read('META-INF/services/java.sql.Driver').decode('utf-8').splitlines()
                if rule['driver'] not in [value.strip() for value in providers]:
                    raise ValueError('Unexpected JDBC service provider: ' + name)
            files[name] = data
    return files


def atomic_write(path, data):
    descriptor, staged = tempfile.mkstemp(prefix=path.name + '.', suffix='.pending', dir=path.parent)
    try:
        with os.fdopen(descriptor, 'wb') as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(staged, path)
    finally:
        if os.path.exists(staged):
            os.unlink(staged)


def install(archive, directory, apply=False, backup_directory=None, approved=None):
    files = reviewed_files(archive, approved)
    directory = Path(directory).resolve()
    old = {name: (directory / name).read_bytes() if (directory / name).exists() else None for name in files}
    report = [{'file': name, 'previousSha256': digest(old[name]) if old[name] is not None else None,
               'sha256': digest(data), 'changed': old[name] != data} for name, data in files.items()]
    if not apply:
        return {'status': 'dry-run', 'directory': str(directory), 'drivers': report}
    if backup_directory is None:
        raise ValueError('Applying requires an explicit backup directory')
    backup_directory = Path(backup_directory).resolve()
    if backup_directory == directory or directory in backup_directory.parents:
        raise ValueError('Backups must be outside the active driver directory')
    backup_directory.mkdir(parents=True, exist_ok=True)
    for name, data in old.items():
        if data is not None and data != files[name]:
            backup = backup_directory / (name + '.' + digest(data)[:16] + '.previous')
            if backup.exists() and backup.read_bytes() != data:
                raise ValueError('Existing backup differs: ' + name)
            if not backup.exists():
                backup.write_bytes(data)
    directory.mkdir(parents=True, exist_ok=True)
    touched = []
    try:
        for name, data in files.items():
            path = directory / name
            current = path.read_bytes() if path.exists() else None
            if current != old[name]:
                raise ValueError('Concurrent driver update detected: ' + name)
            if current != data:
                atomic_write(path, data)
                touched.append(name)
        if any((directory / name).read_bytes() != data for name, data in files.items()):
            raise ValueError('Installed driver verification failed')
    except BaseException:
        for name in reversed(touched):
            if old[name] is None:
                (directory / name).unlink()
            else:
                atomic_write(directory / name, old[name])
        raise
    return {'status': 'installed-and-verified', 'directory': str(directory), 'drivers': report}


def install_to_maven(directory, report_directory, runner=None):
    """Install the exact reviewed bytes using Maven's configured local repository."""
    directory, report_directory = Path(directory).resolve(), Path(report_directory).resolve()
    maven = shutil.which('mvn')
    if maven is None:
        raise ValueError('Maven is unavailable; put mvn on PATH before installing vendor artifacts')
    if os.name == 'nt' and any(c in str(directory) + str(report_directory) for c in '&|<>^%!'):
        raise ValueError('Use a Maven installation path without Windows command metacharacters')
    for name, rule in APPROVED.items():
        if digest((directory / name).read_bytes()) != rule['sha256']:
            raise ValueError('Installed driver differs from the reviewed attachment: ' + name)
    report_directory.mkdir(parents=True, exist_ok=True)
    invoke = subprocess.run if runner is None else runner
    artifacts = []
    for name, (group, artifact) in MAVEN_ARTIFACTS.items():
        command = [maven, 'org.apache.maven.plugins:maven-install-plugin:3.1.3:install-file',
                   '-Dfile=' + str(directory / name), '-DgroupId=' + group,
                   '-DartifactId=' + artifact, '-Dversion=' + MAVEN_VERSION,
                   '-Dpackaging=jar', '-DgeneratePom=true']
        with (report_directory / (name + '.maven-install.log')).open('wb') as stream:
            result = invoke(command, cwd=report_directory, stdout=stream, stderr=subprocess.STDOUT)
        if result.returncode != 0:
            raise RuntimeError('Maven vendor installation failed; inspect ' + name + '.maven-install.log')
        artifacts.append(group + ':' + artifact + ':' + MAVEN_VERSION)
    return artifacts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--driver-directory', type=Path, required=True)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--backup-directory', type=Path)
    parser.add_argument('--install-to-maven', action='store_true',
                        help='With --apply, install the approved vendor pair into Maven for ordinary mvn install builds')
    args = parser.parse_args()
    if args.install_to_maven and not args.apply:
        parser.error('--install-to-maven requires --apply')
    report = install(args.archive, args.driver_directory, args.apply, args.backup_directory)
    if args.install_to_maven:
        report['mavenArtifacts'] = install_to_maven(args.driver_directory, Path(__file__).resolve().parents[1] / 'logs/gaussdb-driver-install')
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()

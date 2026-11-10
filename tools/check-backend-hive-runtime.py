#!/usr/bin/env python3
"""Verify real Hive partition RPCs using only an executable backend JAR's runtime libraries.

Build HiveMetastoreRuntimeProbe with Maven test-compile first. Only that plain Java
probe is copied outside the JAR; no JUnit, Mockito or other test dependencies are
added to the runtime classpath. The probe serves synthetic metadata on loopback.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', required=True, type=Path)
    parser.add_argument('--test-classes', required=True, type=Path)
    parser.add_argument('--java', help='Java 21 executable; defaults to JAVA_HOME/bin/java or PATH')
    args = parser.parse_args()
    jar = args.jar.resolve(strict=True)
    identity = 'com.linewell.dataelement.platform.magic.module.HiveMetastoreRuntimeProbe'
    relative = Path(*identity.split('.')).with_suffix('.class')
    probe = (args.test_classes / relative).resolve(strict=True)
    java = args.java or (str(Path(os.environ['JAVA_HOME']) / 'bin' / 'java')
                         if os.environ.get('JAVA_HOME') else shutil.which('java'))
    if not java:
        parser.error('Java 21 was not found')
    output = Path(__file__).resolve().parents[1] / 'logs' / 'backend'
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='hive-runtime-', dir=output) as temporary:
        workspace = Path(temporary)
        classes = workspace / 'probe'
        destination = classes / relative
        destination.parent.mkdir(parents=True)
        shutil.copy2(probe, destination)
        for count in (20, 100):
            command = [java, '-Dloader.path=' + str(classes), '-Dloader.main=' + identity,
                       '-cp', str(jar), 'org.springframework.boot.loader.launch.PropertiesLauncher',
                       str(workspace / ('metadata-' + str(count))), str(count)]
            result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                    text=True, timeout=45, check=False)
            if result.returncode:
                print(result.stdout)
                raise SystemExit(result.returncode)
            marker = f'PASS real HiveModule/HiveMetaStoreClient RPC: {count} partitions, one batch'
            if marker not in result.stdout:
                raise SystemExit('The packaged runtime did not confirm a complete partition RPC')
            print(marker)
    digest = hashlib.sha256()
    with jar.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    print('PASS packaged backend Hive runtime; JAR SHA-256:', digest.hexdigest())


if __name__ == '__main__':
    main()

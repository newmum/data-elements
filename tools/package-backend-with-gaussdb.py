#!/usr/bin/env python3
"""Bundle the reviewed Huawei GaussDB JDBC pair into a Spring Boot release JAR.

Run Maven first. Only the approved driver libraries and Boot classpath index are
changed; all existing application classes and configuration are preserved.
"""
import argparse
import importlib.util
import io
import json
from pathlib import Path
import zipfile


def sibling_module(name, filename):
    spec = importlib.util.spec_from_file_location(name, Path(__file__).with_name(filename))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


installer = sibling_module('gaussdb_install', 'install-gaussdb-drivers.py')
overlay_tool = sibling_module('gaussdb_overlay', 'package-backend-overlay.py')


def package(base, archive, output, approved=None):
    base, output = Path(base).resolve(), Path(output).resolve()
    if base == output or output.exists():
        raise ValueError('Choose a new output JAR; never overwrite the base or a running application')
    drivers = installer.reviewed_files(archive, approved)
    rules = installer.APPROVED if approved is None else approved
    classes = {rule['driver'].replace('.', '/') + '.class' for rule in rules.values()}
    entries = {}
    driver_entries = {}
    index = 'BOOT-INF/classpath.idx'
    delta = output.with_suffix('.gaussdb-overlay.zip')
    if delta.exists():
        raise ValueError('Preserve the existing overlay; choose a new output name')
    with zipfile.ZipFile(base) as original:
        names = original.namelist()
        if len(names) != len(set(names)):
            raise ValueError('Duplicate ZIP entries in base JAR')
        if 'org/springframework/boot/loader/launch/JarLauncher.class' not in names:
            raise ValueError('Expected the executable Spring Boot backend JAR')
        for name in names:
            if name in {'BOOT-INF/classes/' + cls for cls in classes}:
                raise ValueError('An application class would shadow the approved driver: ' + name)
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                content = original.read(name)
                with zipfile.ZipFile(io.BytesIO(content)) as library:
                    present = classes.intersection(library.namelist())
                for filename, rule in rules.items():
                    if rule['driver'].replace('.', '/') + '.class' not in present:
                        continue
                    if filename in driver_entries:
                        raise ValueError('Duplicate libraries would shadow the approved driver: ' + name)
                    if content != drivers[filename] and name != 'BOOT-INF/lib/' + filename:
                        raise ValueError('A different library would shadow the approved driver: ' + name)
                    driver_entries[filename] = name
                    if content != drivers[filename]:
                        entries[name] = drivers[filename]
        for filename, data in drivers.items():
            if filename not in driver_entries:
                entry = 'BOOT-INF/lib/' + filename
                driver_entries[filename] = entry
                entries[entry] = data
        if index in names:
            classpath = original.read(index).decode('utf-8')
        else:
            classpath = ''.join('- "' + name + '"\n' for name in names
                                if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'))
        for name in driver_entries.values():
            if name not in classpath:
                classpath = classpath.rstrip() + '\n- "' + name + '"\n'
        entries[index] = classpath.encode('utf-8')
        manifest = {'version': 1, 'entries': [
            {'name': name, 'sha256': overlay_tool.digest(data),
             'originalSha256': overlay_tool.digest(original.read(name)) if name in names else None}
            for name, data in sorted(entries.items())]}
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(delta, 'x', compression=zipfile.ZIP_DEFLATED) as archive_out:
        archive_out.writestr('overlay.json', json.dumps(manifest))
        for name, data in entries.items():
            archive_out.writestr(name, data)
    result = overlay_tool.apply(base, delta, output)
    with zipfile.ZipFile(output) as packaged:
        for name, data in drivers.items():
            entry = driver_entries[name]
            if packaged.read(entry) != data or packaged.getinfo(entry).compress_type != zipfile.ZIP_STORED:
                raise ValueError('Boot nested driver verification failed: ' + name)
    return {**result, 'output': str(output), 'overlay': str(delta),
            'bundledDrivers': [{'file': name, 'driver': rules[name]['driver'],
                                'sha256': installer.digest(data)} for name, data in drivers.items()]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--archive', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(package(args.base, args.archive, args.output), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()

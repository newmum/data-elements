#!/usr/bin/env python3
"""Package an explicitly selected set of compiled classes without replacing unrelated runtime code.

Build with Maven first. Output and the optional patched JAR must be placed in ignored logs/.
The overlay embeds hashes of touched base entries so its application refuses a changed baseline.
No resources, configuration, credentials or unselected classes are collected.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def file_digest(path):
    hasher = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            hasher.update(block)
    return hasher.hexdigest()


def apply(base, overlay, output):
    if base.resolve() == output.resolve():
        raise ValueError('Stage the output separately; never modify a running JAR')
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(overlay) as delta:
        manifest = json.loads(delta.read('overlay.json'))
        original_names = set(old.namelist())
        if len(original_names) != len(old.namelist()):
            raise ValueError('Duplicate entries in base JAR')
        for item in manifest['entries']:
            name = item['name']
            current = digest(old.read(name)) if name in original_names else None
            if current != item['originalSha256']:
                raise ValueError('Touched runtime entry changed: ' + name)
            if digest(delta.read(name)) != item['sha256']:
                raise ValueError('Overlay entry digest mismatch')
        replacements = {item['name'] for item in manifest['entries']}
        output.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(output, 'x') as result:
            for item in old.infolist():
                if item.filename not in replacements:
                    result.writestr(copy.copy(item), old.read(item.filename))
            for name in sorted(replacements):
                entry = zipfile.ZipInfo(name)
                # Spring Boot nested libraries must remain uncompressed.
                entry.compress_type = zipfile.ZIP_STORED if name.startswith('BOOT-INF/lib/') else zipfile.ZIP_DEFLATED
                result.writestr(entry, delta.read(name))
        with zipfile.ZipFile(output) as verified:
            for item in old.infolist():
                if item.filename not in replacements and digest(verified.read(item.filename)) != digest(old.read(item.filename)):
                    raise ValueError('Unselected runtime entry changed')
            for item in manifest['entries']:
                if digest(verified.read(item['name'])) != item['sha256']:
                    raise ValueError('Patched runtime entry digest mismatch')
    return {'entries': len(replacements), 'sha256': file_digest(output), 'allEntryContentsVerified': True}


def verify_existing(base, overlay, output):
    with zipfile.ZipFile(base) as old, zipfile.ZipFile(overlay) as delta, zipfile.ZipFile(output) as result:
        manifest=json.loads(delta.read('overlay.json'))
        selected={item['name']:item for item in manifest['entries']}
        for item in manifest['entries']:
            original=digest(old.read(item['name'])) if item['name'] in old.namelist() else None
            if original!=item['originalSha256'] or digest(result.read(item['name']))!=item['sha256']:
                raise ValueError('Staged runtime differs from selected baseline or overlay')
        for name in old.namelist():
            if name not in selected and digest(old.read(name))!=digest(result.read(name)):
                raise ValueError('Unselected runtime entry changed')
    return {'entries':len(selected),'sha256':file_digest(output),'allEntryContentsVerified':True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--classes', type=Path)
    parser.add_argument('--include', action='append', default=[], help='Package directory or class prefix relative to compiled classes')
    parser.add_argument('--library', action='append', default=[], type=Path)
    parser.add_argument('--overlay', type=Path, required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--verify-existing', action='store_true')
    args = parser.parse_args()
    if args.classes:
        entries = {}
        for prefix in args.include:
            relative = Path(prefix)
            if relative.is_absolute() or '..' in relative.parts:
                raise ValueError('Invalid compiled class prefix')
            path = args.classes / relative
            selected = path.rglob('*.class') if path.is_dir() else path.parent.glob(path.name + '*.class')
            found = list(selected)
            if not found:
                raise ValueError('No compiled classes found for ' + prefix)
            for file in found:
                entries['BOOT-INF/classes/' + file.relative_to(args.classes).as_posix()] = file.read_bytes()
        for file in args.library:
            if not file.is_file() or file.suffix != '.jar':
                raise ValueError('Expected dependency JAR')
            entries['BOOT-INF/lib/' + file.name] = file.read_bytes()
        if not entries:
            raise ValueError('Nothing explicitly selected')
        manifest = {'version': 1, 'entries': []}
        with zipfile.ZipFile(args.base) as base:
            names = set(base.namelist())
            index = 'BOOT-INF/classpath.idx'
            if args.library and index in names:
                text = base.read(index).decode('utf-8')
                for file in args.library:
                    name = 'BOOT-INF/lib/' + file.name
                    if name not in text:
                        text = text.rstrip() + '\n- "' + name + '"\n'
                entries[index] = text.encode('utf-8')
            for name, content in sorted(entries.items()):
                manifest['entries'].append({'name': name, 'sha256': digest(content),
                                           'originalSha256': digest(base.read(name)) if name in names else None})
        args.overlay.parent.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(args.overlay, 'x', compression=zipfile.ZIP_DEFLATED) as overlay:
            overlay.writestr('overlay.json', json.dumps(manifest))
            for name, content in entries.items():
                overlay.writestr(name, content)
        print(json.dumps({'overlayEntries': len(entries), 'overlayBytes': args.overlay.stat().st_size}))
    if args.output:
        print(json.dumps(verify_existing(args.base,args.overlay,args.output) if args.verify_existing else apply(args.base, args.overlay, args.output)))


if __name__ == '__main__':
    main()

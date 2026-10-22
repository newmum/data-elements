#!/usr/bin/env python3
"""Generate an FTP completion manifest from exact file bytes; does not upload or modify data."""
import argparse, csv, hashlib, json, os, re, sys, tempfile
from pathlib import Path

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('file', type=Path)
    parser.add_argument('--format', required=True, choices=['JSONL', 'CSV', 'BINARY'])
    parser.add_argument('--file-id', required=True, help='Stable unique ID of this batch; reuse it on retries.')
    args = parser.parse_args()
    if not args.file.is_file(): parser.error('Data file does not exist.')
    if not re.fullmatch(r'[!-~]{1,200}', args.file_id): parser.error('file-id must be 1-200 printable ASCII characters without spaces.')
    sha = hashlib.sha256(); size = 0
    with args.file.open('rb') as stream:
        for chunk in iter(lambda: stream.read(65536), b''):
            size += len(chunk); sha.update(chunk)
    count = 1
    if args.format == 'JSONL':
        count = 0
        with args.file.open('r', encoding='utf-8-sig') as stream:
            for line in stream:
                if not line.strip(): continue
                if not isinstance(json.loads(line), dict): raise ValueError('Every JSONL record must be an object.')
                count += 1
    elif args.format == 'CSV':
        with args.file.open('r', encoding='utf-8-sig', newline='') as stream:
            rows = csv.reader(stream, strict=True); header = next(rows, None); count = 0
            if header is not None:
                if any(not x for x in header) or len(set(header)) != len(header): raise ValueError('Invalid CSV header.')
                for row in rows:
                    if len(row) != len(header): raise ValueError('CSV column count mismatch.')
                    count += 1
    manifest = dict(fileId=args.file_id, fileName=args.file.name, format=args.format,
                    size=size, sha256=sha.hexdigest(), recordCount=count)
    output = Path(str(args.file) + '.done')
    payload = json.dumps(manifest, ensure_ascii=False, indent=2) + '\n'
    if output.exists():
        if json.loads(output.read_text(encoding='utf-8')) != manifest:
            raise ValueError('Existing .done differs. Use a new immutable data filename and batch ID.')
    else:
        # CREATE_NEW avoids accidentally overwriting an existing manifest.
        with output.open('x', encoding='utf-8') as stream:
            stream.write(payload); stream.flush(); os.fsync(stream.fileno())
    print(output)
    print('Upload the immutable data file first, then publish this .done last. Do not modify either afterwards.')

if __name__ == '__main__':
    try: main()
    except (OSError, ValueError, csv.Error) as error:
        print(f'Failed: {error}', file=sys.stderr); sys.exit(1)

#!/usr/bin/env python3
"""Writes ONE new demo message and checks its result. Requires a running relay with demo route enabled."""
import argparse, json, sys, time, uuid
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT = Path(__file__).resolve().parents[1]
def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:18083')
    parser.add_argument('--key-file', type=Path, default=ROOT/'data/keys/client.key')
    parser.add_argument('--gateway-key-file', type=Path)
    parser.add_argument('--timeout', type=int, default=60)
    args = parser.parse_args()
    key = args.key_file.read_text(encoding='utf-8').strip()
    headers = {'X-Api-Key': key, 'Content-Type':'application/json'}
    if args.gateway_key_file:
        headers['X-Gateway-Key'] = args.gateway_key_file.read_text(encoding='utf-8').strip()
    base = args.base_url.rstrip('/')
    body = (ROOT/'examples/event.json').read_bytes()
    def call(path, data=None, extra=None):
        request = Request(base+path, data=data, headers=headers | (extra or {}))
        try:
            with urlopen(request, timeout=15) as response:
                return response.status, json.load(response)
        except HTTPError as error:
            return error.code, json.loads(error.read())
    idem = 'smoke-'+str(uuid.uuid4())
    code, receipt = call('/api/v1/ingest/demo', body, {'Idempotency-Key':idem})
    if code != 202: raise RuntimeError(f'Expected 202, received {code}: {receipt}')
    message_id = receipt['messageId']
    code, repeated = call('/api/v1/ingest/demo', body, {'Idempotency-Key':idem})
    assert code in (200,202) and repeated['messageId'] == message_id and repeated['duplicate']
    code, _ = call('/api/v1/ingest/demo', b'{"different":true}', {'Idempotency-Key':idem})
    assert code == 409, f'Expected conflict, received {code}'
    until = time.monotonic()+args.timeout
    while time.monotonic() < until:
        code, state = call('/api/v1/messages/'+message_id)
        if code != 200: raise RuntimeError(f'Status query failed: {code}')
        if state['status'] == 'SUCCEEDED':
            print(json.dumps(state, ensure_ascii=False, indent=2))
            print('SMOKE PASSED: accepted, idempotent, conflict rejected, all demo targets succeeded.')
            return
        if state.get('completed'):
            raise RuntimeError('Target reached non-success terminal state: '+json.dumps(state, ensure_ascii=False))
        time.sleep(1)
    raise RuntimeError('Accepted but targets did not all complete before the test deadline: '+message_id)

if __name__ == '__main__':
    try: main()
    except (OSError, URLError, ValueError, RuntimeError, AssertionError) as error:
        print(f'SMOKE FAILED: {error}', file=sys.stderr); sys.exit(1)

import hashlib
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import os
import subprocess
import sys
import threading
import unittest
import uuid

ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / 'haitong-data-pull/scripts/upload_file.py'


class FilePushClientTest(unittest.TestCase):
    def run_client(self, chunked):
        payload = b'%PDF-1.7\n' + b'V' * 140000
        calls = []
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def handle_request(self):
                body = self.rfile.read(int(self.headers.get('Content-Length', '0')))
                calls.append((self.command, self.path, dict(self.headers), body))
                if self.command == 'GET' and '/parts?' in self.path:
                    result = {'parts': [{'partNumber': 1}]}
                elif self.command == 'GET':
                    result = {'state': 'READY', 'deliveryStatus': 'SUCCEEDED', 'deliveries': [{'status': 'SUCCEEDED'}]}
                else:
                    result = {'uploadId': 'validation-upload', 'state': 'CREATED', 'partSize': 65536, 'partCount': 3}
                data = json.dumps(result).encode()
                self.send_response(200 if self.command == 'GET' or '/parts/' in self.path else 202)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Content-Length', str(len(data)))
                self.end_headers()
                self.wfile.write(data)

            do_GET = do_PUT = do_POST = handle_request

        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        directory = ROOT / 'logs/file-push-client-tests' / ('validation-' + uuid.uuid4().hex)
        directory.mkdir(parents=True)
        file = directory / 'validation.pdf'
        file.write_bytes(payload)
        args = [sys.executable, str(CLIENT), str(file), '--base-url', 'http://127.0.0.1:' + str(server.server_port),
                '--datasource', 'validation-source', '--file-code', 'case_document', '--business-key', 'validation-case',
                '--idempotency-key', 'validation-request', '--metadata', '{"caseNo":"VALIDATION-1"}', '--wait-seconds', '5']
        if chunked:
            args.append('--chunked')
        try:
            result = subprocess.run(args, capture_output=True, encoding='utf-8', timeout=15,
                                    env={**os.environ, 'HAITONG_API_KEY': 'validation-test-client-key'})
        finally:
            server.shutdown()
            server.server_close()
            worker.join(timeout=3)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertNotIn('validation-test-client-key', result.stdout)
        self.assertTrue(all(call[2]['X-Api-Key'] == 'validation-test-client-key' for call in calls))
        return calls, payload

    def test_single_uses_put_multipart_and_only_queries_after_acceptance(self):
        calls, payload = self.run_client(False)
        self.assertEqual([(m, p) for m, p, _, _ in calls], [('PUT', '/api/v1/data-push'), ('GET', '/api/v1/data-push/uploads/validation-upload')])
        _, _, headers, body = calls[0]
        self.assertTrue(headers['Content-Type'].startswith('multipart/form-data; boundary='))
        self.assertEqual(int(headers['Content-Length']), len(body))
        self.assertIn(b'name="fileCode"\r\n\r\ncase_document', body)
        self.assertIn(payload, body)
        self.assertNotIn(b'name="size"', body)

    def test_chunked_resumes_and_combines_completion_with_submission(self):
        calls, payload = self.run_client(True)
        init = json.loads(calls[0][3])
        self.assertEqual(init['fileCode'], 'case_document')
        self.assertEqual(init['sha256'], hashlib.sha256(payload).hexdigest())
        parts = [(p, b) for m, p, _, b in calls if m == 'PUT']
        self.assertEqual([p.rsplit('/', 1)[1] for p, _ in parts], ['2', '3'])
        self.assertEqual(b''.join(b for _, b in parts), payload[65536:])
        complete = [(h, json.loads(b)) for m, p, h, b in calls if m == 'POST' and p.endswith('/complete')]
        self.assertEqual(len(complete), 1)
        self.assertEqual(complete[0][1]['businessKey'], 'validation-case')
        self.assertEqual(complete[0][1]['metadata'], {'caseNo': 'VALIDATION-1'})
        self.assertFalse(any('/submit' in p for _, p, _, _ in calls))


if __name__ == '__main__':
    unittest.main()

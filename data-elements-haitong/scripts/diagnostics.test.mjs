import test from 'node:test';
import assert from 'node:assert/strict';
import { readDiagnosticStream } from '../src/nifi/api/diagnosticStream.ts';
const frame = (event, data) => 'event: ' + event + '\r\ndata: ' + JSON.stringify(data) + '\r\n\r\n';
function response(text, split = 1) {
  const bytes = new TextEncoder().encode(text);
  return new Response(new ReadableStream({ start(controller) { for (let i = 0; i < bytes.length; i += split) controller.enqueue(bytes.slice(i, i + split)); controller.close(); } }), { headers: { 'Content-Type': 'text/event-stream' } });
}
test('split UTF-8/CRLF chunks preserve evidence and content without executing markup', async () => {
  const events = [];
  await readDiagnosticStream(response(': keepalive\r\n\r\n' + frame('evidence', [{ id: 'error', content: '故障' }]) + frame('delta', { text: '<script>throw 1</script>建议' }) + frame('done', {})), new AbortController().signal, event => events.push(event));
  assert.deepEqual(events.map(e => e.event), ['evidence', 'delta', 'done']);
  assert.equal(events[1].data.text, '<script>throw 1</script>建议');
});
test('EOF after partial text is an error and is not replayed', async () => {
  const events = [];
  await assert.rejects(readDiagnosticStream(response(frame('delta', { text: 'partial' })), new AbortController().signal, event => events.push(event)), /回答可能不完整/);
  assert.equal(events.length, 1);
});
test('explicit stream error is surfaced', async () => {
  await assert.rejects(readDiagnosticStream(response(frame('error', { message: '网关繁忙' })), new AbortController().signal, () => {}), /网关繁忙/);
});
test('cancelled requests cannot deliver late data', async () => {
  const controller = new AbortController(); controller.abort(); let delivered = 0;
  await assert.rejects(readDiagnosticStream(response(frame('delta', { text: 'stale' })), controller.signal, () => delivered++), error => error.name === 'AbortError');
  assert.equal(delivered, 0);
});
test('JSON failure responses preserve a safe server message', async () => {
  await assert.rejects(readDiagnosticStream(new Response(JSON.stringify({ message: '会话已过期' }), { status: 404, headers: { 'Content-Type': 'application/json' } }), new AbortController().signal, () => {}), /会话已过期/);
});
test('oversized SSE frames fail without an unbounded buffer', async () => {
  await assert.rejects(readDiagnosticStream(response('data: ' + 'x'.repeat(262145), 4096), new AbortController().signal, () => {}), /超过限制/);
});

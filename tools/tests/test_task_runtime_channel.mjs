import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

// Execute the actual low-code channel; the transport is isolated from real tasks.
const root = new URL('../../', import.meta.url), babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const source = await fs.readFile(new URL('lowcode/03.数据接入/2.3.2 接入监控(access-monitoring).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const ast = babel.Babel.transform(script, { ast: true, code: false, parserOpts: { sourceType: 'module', plugins: ['typescript'] } }).ast;
const names = ['runtimeConnection','runtimeReadAt','runtimeWarning','runtimeHint','runtimeRevision','runtimeActive','refreshRuntimePage','acceptRuntimeEvent','startRuntimeStream','stopRuntimeStream','runtimeVisibility'];
const selected = ast.program.body.filter(node => node.type === 'VariableDeclaration' && node.declarations.some(d => names.includes(d.id.name)));
const code = babel.Babel.transform(selected.map(node => script.slice(node.start, node.end)).join('\n'), { filename: 'channel.ts', presets: ['typescript'] }).code;
const refreshes = [], reads = [], requests = [];
const payloads = [{ revision: 'v1', fresh: true, observedAt: 1, message: '' }, ...Array.from({ length: 120 }, (_, i) => ({ revision: 'v1', fresh: true, observedAt: i + 2, message: '' })), { revision: 'v2', fresh: true, observedAt: 130, message: '状态变化' }];
const text = payloads.map(payload => 'event:runtime\r\ndata:'+JSON.stringify(payload)+'\r\n\r\n').join('');
const bytes = new TextEncoder().encode(text);
const context = vm.createContext({
  ref: value => ({ value }), computed: get => ({ get value() { return get(); } }), AbortController, TextDecoder, Object, JSON, Date,
  setTimeout, clearTimeout, Math, document: { hidden: false }, tableRef: { value: { refresh: reset => refreshes.push(reset) } }, activeTab: { value: 'all' },
  $request: { getUri: () => '/dev-api/nifi/api/task-runtime/events', get: async (url, config) => {
    requests.push(url); return config.adapter({ ...config, headers: { toJSON: () => ({ token: 'test-session' }) } });
  } },
  fetch: async (url, options) => {
    reads.push({ url, options }); let offset = 0;
    return { ok: true, status: 200, headers: { get: () => 'text/event-stream;charset=UTF-8' }, body: { getReader: () => ({
      read: async () => { if (offset >= bytes.length) return { done: true }; const chunk = bytes.slice(offset, offset + 7); offset += 7; return { done: false, value: chunk }; }, releaseLock() {},
    }) } };
  },
});
vm.runInContext(code + '\nglobalThis.actual={startRuntimeStream,stopRuntimeStream,acceptRuntimeEvent,runtimeConnection,runtimeWarning,runtimeReadAt};', context);
await context.actual.startRuntimeStream();
await new Promise(resolve => setTimeout(resolve, 180));
context.actual.stopRuntimeStream();
assert.equal(requests.length, 1, 'One stream for the page, not one per row');
assert.equal(reads.length, 1); assert.ok(!reads[0].url.includes('test-session'));
assert.equal(reads[0].options.headers.token, 'test-session');
assert.deepEqual(refreshes, [false], '120 unchanged samples cause no reads; a new revision refreshes the current page once');
assert.equal(context.actual.runtimeConnection.value, 'live');
context.actual.acceptRuntimeEvent({ revision: 'v3', fresh: false, observedAt: 130, message: 'NiFi 连接失败' });
assert.equal(context.actual.runtimeConnection.value, 'unavailable'); assert.equal(context.actual.runtimeWarning.value, 'NiFi 连接失败');
context.actual.stopRuntimeStream();
console.log('PASS: one authenticated stream, split UTF-8/CRLF frames, no token URL, unchanged revisions add zero requests, coalesced refresh, explicit unavailable state and cancellation');

// Exercise the canvas transport itself: multiple consumers share one connection,
// and losing the transport actively marks existing displays as unconfirmed.
const ts = (await import(new URL('data-elements-haitong/node_modules/typescript/lib/typescript.js', root))).default;
const canvasSource = await fs.readFile(new URL('data-elements-haitong/src/nifi/api/runtimeStatusStream.ts', root), 'utf8');
const canvasCode = ts.transpileModule(canvasSource.replace(/^import .*;\r?\n/gm, '').replace('export function subscribeRuntimeStatus', 'function subscribeRuntimeStatus'),
  { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext } }).outputText;
let connections = 0, failRead;
const receivedA = [], receivedB = [];
const canvasContext = vm.createContext({
  AbortController, TextDecoder, JSON, Math, Date, setTimeout, clearTimeout,
  document: { hidden: false, addEventListener() {}, removeEventListener() {} },
  apiClient: { getUri: () => '/nifi/api/task-runtime/events' }, getBridgeToken: () => 'test-session', getSessionRevision: () => 7,
  fetch: async (url, options) => {
    connections++; assert.ok(!url.includes('test-session')); assert.equal(options.headers.token, 'test-session');
    let first = true;
    return { ok: true, headers: { get: () => 'text/event-stream' }, body: { getReader: () => ({
      read: async () => {
        if (first) { first = false; return { done: false, value: new TextEncoder().encode('data:'+JSON.stringify(payloads[0])+'\n\n') }; }
        return new Promise((resolve, reject) => { failRead = reject; options.signal.addEventListener('abort', () => reject(new Error('aborted')), { once: true }); });
      }, releaseLock() {},
    }) } };
  },
});
vm.runInContext(canvasCode + '\nglobalThis.subscribe=subscribeRuntimeStatus;', canvasContext);
const closeA = canvasContext.subscribe(event => receivedA.push(event));
const closeB = canvasContext.subscribe(event => receivedB.push(event));
await new Promise(resolve => setTimeout(resolve, 10));
assert.equal(connections, 1); assert.equal(receivedA[0].fresh, true); assert.equal(receivedB[0].fresh, true);
failRead(new Error('connection lost'));
await new Promise(resolve => setTimeout(resolve, 10));
assert.equal(receivedA.at(-1).transportAvailable, false); assert.equal(receivedB.at(-1).fresh, false);
closeA(); closeB();
console.log('PASS: canvas consumers share one stream; transport loss marks state unconfirmed and unsubscribe stops reconnect');

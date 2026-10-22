import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../../lowcode/06.数据服务/数据服务(data-serve).vue', import.meta.url), 'utf8');
const block = source.slice(source.indexOf('const getToken ='), source.indexOf('const handleDelete ='));

function setup(options = {}) {
  const calls = [], sent = [], timers = new Map();
  let timer = 0;
  const child = { postMessage: (data, origin) => sent.push({ data, origin }), location: { href: 'http://localhost:3030/editor/flow-a' } };
  const ref = value => ({ value });
  const context = { URL, URLSearchParams, AbortController, Date,
    window: { location: new URL(options.href ?? 'http://localhost:3000/#/deal/data-server/list'), setTimeout: callback => { timers.set(++timer, callback); return timer; }, clearTimeout: id => timers.delete(id) },
    localStorage: { getItem: key => ({ remember_me: 'true', token: 'current-session', ...options.local })[key] ?? null },
    sessionStorage: { getItem: key => options.session?.[key] ?? null }, $user: { orgId: 'org-a' },
    ElMessage: { warning: () => {} }, orchestrationAttempt: 0, orchestrationProbeController: null, orchestrationLoadTimer: null,
    orchestrationVisible: ref(false), orchestrationLoading: ref(false), orchestrationError: ref(''), orchestrationUrl: ref(''), orchestrationRow: ref(null), orchestrationTitle: ref(''), orchestrationIframeRef: ref({ contentWindow: child }),
    fetch: async (url, init) => {
      calls.push({ url, init });
      return options.fetch ? options.fetch(url, init) : { ok: true, type: 'cors', text: async () => '<meta name="application-name" content="data-elements-yunti">' };
    },
  };
  vm.createContext(context);
  vm.runInContext(block + '\nglobalThis.api = { token: getToken, candidates: getYuntiCandidates, url: buildOrchestrationUrl, reachable: isYuntiReachable, open: openOrchestration, close: closeOrchestration, message: handleOrchestrationMessage, loaded: onOrchestrationLoad };', context);
  return { context, api: context.api, child, calls, sent, timers };
}

test('20/100 list rows add no automatic probe; one user click probes current port once', async () => {
  for (const count of [20, 100]) {
    const s = setup();
    const rows = Array.from({ length: count }, (_, i) => ({ id: `api-${i}`, flowId: `flow-${i}` }));
    assert.equal(s.calls.length, 0);
    await s.api.open(rows[0]);
    assert.equal(s.calls.length, 1);
    assert.match(s.calls[0].url, /^http:\/\/localhost:3030\//);
    assert.equal(s.calls[0].init.mode, 'cors');
    assert.equal(s.api.candidates().length, 11);
    s.api.close();
  }
});

test('document probe rejects another app and opaque responses rather than trusting a listening port', async () => {
  for (const response of [{ ok: true, type: 'cors', text: async () => '<title>Other app</title>' }, { ok: true, type: 'opaque' }]) {
    const s = setup({ fetch: async () => response });
    assert.equal(await s.api.reachable('http://localhost:3030'), false);
  }
  const production = setup({ href: 'https://platform.example/#/deal/data-server/list' });
  assert.deepEqual([...production.api.candidates()], ['https://platform.example/yunti']);
});

test('editor URL preserves service identity and return context without an authentication token', () => {
  const s = setup();
  const url = new URL(s.api.url('http://localhost:3030', { id: 'api-a', flowId: 'flow-a' }));
  assert.equal(url.pathname, '/editor/flow-a');
  assert.equal(url.searchParams.get('apiId'), 'api-a');
  assert.equal(url.searchParams.get('parentOrigin'), 'http://localhost:3000');
  assert.equal(url.searchParams.get('communication'), 'postMessage');
  assert.equal(url.searchParams.has('token'), false);
  assert.equal(url.href.includes('current-session'), false);
});

test('remembered and session-only logins use the same storage choice as platform authentication', () => {
  assert.equal(setup().api.token(), 'current-session');
  assert.equal(setup({ local: { remember_me: 'false', token: 'stale-session' }, session: { token: JSON.stringify('session-login') } }).api.token(), 'session-login');
  assert.equal(setup({ local: { remember_me: 'false', token: 'stale-session' } }).api.token(), '');
});

test('INIT targets the exact verified origin; unrelated sources/origins cannot initialize or close it', async () => {
  const s = setup();
  await s.api.open({ id: 'api-a', flowId: 'flow-a' });
  s.api.message({ source: s.child, origin: 'https://unrelated.example', data: { type: 'YUNTI_APP_READY' } });
  s.api.message({ source: {}, origin: 'http://localhost:3030', data: { type: 'YUNTI_APP_READY' } });
  assert.equal(s.sent.length, 0);
  s.api.message({ source: s.child, origin: 'http://localhost:3030', data: { type: 'YUNTI_APP_READY' } });
  assert.equal(s.sent[0].origin, 'http://localhost:3030');
  assert.equal(s.sent[0].data.payload.token, 'current-session');
  assert.equal(s.context.orchestrationLoading.value, true);
  s.api.message({ source: s.child, origin: 'http://localhost:3030', data: { type: 'YUNTI_SESSION_READY' } });
  assert.equal(s.context.orchestrationLoading.value, false);
  assert.equal(s.timers.size, 0);
  s.api.close();
});

test('close cancels an in-flight detection and ignores its late response', async () => {
  let resolve;
  const s = setup({ fetch: () => new Promise(done => { resolve = done; }) });
  const opened = s.api.open({ id: 'api-a' });
  s.api.close();
  resolve({ ok: true, type: 'cors', text: async () => '<title>云梯服务编排平台</title>' });
  await opened;
  assert.equal(s.context.orchestrationUrl.value, '');
  assert.equal(s.context.orchestrationVisible.value, false);
});

test('initialization timeout stops loading and gives a concrete retry message', async () => {
  const s = setup();
  await s.api.open({ id: 'api-a' });
  [...s.timers.values()][0]();
  assert.equal(s.context.orchestrationLoading.value, false);
  assert.match(s.context.orchestrationError.value, /初始化超时/);
  s.api.close();
});

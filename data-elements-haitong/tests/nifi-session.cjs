const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

function loadTypeScript(file, dependencies) {
  const source = fs.readFileSync(path.join(__dirname, '..', file), 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText.replaceAll('import.meta.env', 'viteEnv');
  const result = { exports: {} };
  const localRequire = (name) => {
    if (Object.hasOwn(dependencies, name)) return dependencies[name];
    throw new Error(`Unexpected dependency in session test: ${name}`);
  };
  new Function('require', 'module', 'exports', 'viteEnv', compiled)(localRequire, result, result.exports, { DEV: true });
  return result.exports;
}

test('tenant credential rotation clears NiFi and platform query data but retains local workspace data', () => {
  const session = loadTypeScript('src/nifi/api/bridgeSession.ts', {
    react: { useSyncExternalStore: () => undefined },
  });
  let canvasClears = 0;
  const removed = [];
  const querySession = loadTypeScript('src/nifi/api/querySession.ts', {
    './bridgeSession': session,
    '@/stores/canvasStore': { useCanvasStore: { getState: () => ({ clear: () => { canvasClears += 1; } }) } },
  });
  querySession.registerNifiQueryClient({
    cancelQueries: () => Promise.resolve(),
    removeQueries: (options) => removed.push(options),
  });

  session.setBridgeToken('tenant-a');
  assert.equal(session.getSessionRevision(), 1);
  assert.equal(canvasClears, 1);
  session.setBridgeToken('tenant-a');
  assert.equal(session.getSessionRevision(), 1);
  assert.equal(canvasClears, 1);
  session.setBridgeToken('tenant-b');
  assert.equal(session.getSessionRevision(), 2);
  assert.equal(canvasClears, 2);
  assert.equal(removed.length, 2);
  assert.equal(removed[1].predicate({ queryKey: ['pipeline', 'old-id', 1] }), true);
  assert.equal(removed[1].predicate({ queryKey: ['manifests', 1] }), true);
  assert.equal(removed[1].predicate({ queryKey: ['platform', 'access-tasks'] }), true);
  assert.equal(removed[1].predicate({ queryKey: ['workspace'] }), false);
  session.setBridgeToken(null);
  assert.equal(session.getSessionRevision(), 3);
  assert.equal(canvasClears, 3);
});

test('explicit routes outrank parent INIT, and changing token drops the old parent job', () => {
  const session = loadTypeScript('src/nifi/api/bridgeSession.ts', {
    react: { useSyncExternalStore: () => undefined },
  });
  const listeners = new Map();
  const previousWindow = global.window;
  const previousDocument = global.document;
  const location = {
    href: 'http://localhost:3002/#/development/canvas',
    origin: 'http://localhost:3002',
    search: '',
    hash: '#/development/canvas',
  };
  const windowMock = {
    location,
    history: { state: null, replaceState: () => {} },
    addEventListener: (type, listener) => listeners.set(type, listener),
    removeEventListener: (type) => listeners.delete(type),
  };
  const parentMock = { postMessage: () => {} };
  windowMock.parent = parentMock;
  windowMock.self = windowMock;
  windowMock.top = parentMock;
  global.window = windowMock;
  global.document = { querySelector: () => null };
  try {
    const bridge = loadTypeScript('src/nifi/api/iframeBridge.ts', {
      './client': { apiClient: { defaults: { headers: { common: {} } } } },
      './bridgeSession': session,
    });
    bridge.initIframeBridge();
    const send = (type, payload) => listeners.get('message')({ origin: 'http://localhost:3000', source: parentMock, data: { type, ...payload } });
    listeners.get('message')({ origin: 'http://untrusted.example', source: parentMock, data: { type: 'SET_TOKEN', token: 'rogue' } });
    listeners.get('message')({ origin: 'http://localhost:3000', source: windowMock, data: { type: 'SET_TOKEN', token: 'rogue' } });
    assert.equal(session.getBridgeToken(), null);

    send('INIT', { payload: { token: 'tenant-a', jobId: 'parent-a' } });
    assert.equal(bridge.getPipelineId(), 'parent-a');
    assert.equal(session.getCanvasSelectionRevision(), 1);
    bridge.setCanvasSelection({});
    assert.equal(bridge.getPipelineId(), 'parent-a');

    bridge.setCanvasSelection({ pipelineId: 'route-flow' });
    assert.equal(bridge.getPipelineId(), 'route-flow');
    bridge.setCanvasSelection({ pipelineId: null });
    assert.equal(bridge.getPipelineId(), null);
    bridge.setCanvasSelection({});
    assert.equal(bridge.getPipelineId(), 'parent-a');

    send('SET_TOKEN', { token: 'tenant-b' });
    assert.equal(bridge.getPipelineId(), null);
    send('INIT', { payload: { token: 'tenant-b', jobId: 'parent-b' } });
    assert.equal(bridge.getPipelineId(), 'parent-b');
    send('INIT', { payload: { token: 'tenant-b', jobId: 'parent-c' } });
    assert.equal(bridge.getPipelineId(), 'parent-c');
    assert.equal(session.getCanvasSelectionRevision(), 3);

    session.setBridgeToken('tenant-c');
    assert.equal(bridge.getPipelineId(), null);
    send('INIT', { payload: { token: 'tenant-c', jobId: 'parent-c' } });
    assert.equal(bridge.getPipelineId(), 'parent-c');

    location.hash = '#/development/canvas?jobId=url-flow';
    assert.equal(bridge.getPipelineId(), 'url-flow');
    bridge.setCanvasSelection({ pipelineId: 'route-flow' });
    assert.equal(bridge.getPipelineId(), 'route-flow');
    bridge.destroyIframeBridge();
  } finally {
    global.window = previousWindow;
    global.document = previousDocument;
  }
});

test('expired platform session redirects once to the existing login and preserves a token-free return route', () => {
  const previousWindow = global.window;
  const previousSessionStorage = global.sessionStorage;
  const values = new Map();
  const navigations = [];
  const cleared = [];
  const windowMock = {
    location: {
      href: 'http://localhost:3002/#/development/tasks?token=secret',
      origin: 'http://localhost:3002',
      replace: (value) => navigations.push(value),
    },
  };
  windowMock.parent = windowMock;
  global.window = windowMock;
  global.sessionStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
  };
  try {
    const session = loadTypeScript('src/services/platformSession.ts', {
      '../nifi/api/bridgeSession': { setBridgeToken: (token) => cleared.push(token) },
    });
    assert.equal(session.redirectToPlatformLogin(), true);
    assert.equal(session.redirectToPlatformLogin(), false);
    assert.deepEqual(cleared, [null]);
    assert.equal(navigations.length, 1);
    const login = new URL(navigations[0]);
    assert.equal(login.origin, 'http://localhost:3000');
    assert.equal(login.hash.split('?')[0], '#/login');
    assert.equal(new URLSearchParams(login.hash.split('?')[1]).get('redirect'),
      'http://localhost:3002/#/development/tasks');
  } finally {
    global.window = previousWindow;
    global.sessionStorage = previousSessionStorage;
  }
});

test('platform API recognizes HTTP-200 user_no_login as authentication expiry', async () => {
  const previousFetch = global.fetch;
  let redirects = 0;
  const api = loadTypeScript('src/api/platformApi.ts', {
    '../services/platformSession': {
      getPlatformSessionToken: async () => 'current-token',
      redirectToPlatformLogin: () => { redirects += 1; return true; },
    },
    '../nifi/api/bridgeSession': {
      getBridgeToken: () => null,
      getSessionRevision: () => 0,
    },
  });
  global.fetch = async () => ({
    ok: true,
    status: 200,
    json: async () => ({ code: 100120, msg: 'user_no_login', success: false, data: null }),
  });
  try {
    await assert.rejects(api.platformApi('/ods/task/page', {}), error =>
      error instanceof api.PlatformApiError && error.status === 401);
    assert.equal(redirects, 1);
  } finally {
    global.fetch = previousFetch;
  }
});

test('closing an embedded canvas preserves the parent protocol while standalone close stays in HaiTong', () => {
  const previousWindow = global.window;
  const previousCustomEvent = global.CustomEvent;
  const parentMessages = [];
  const localEvents = [];
  const parent = { postMessage: (payload, targetOrigin) => parentMessages.push({ payload, targetOrigin }) };
  const embedded = { parent, dispatchEvent: (event) => localEvents.push(event.type) };
  global.window = embedded;
  global.CustomEvent = class CustomEvent { constructor(type) { this.type = type; } };
  try {
    const bridge = loadTypeScript('src/nifi/api/iframeBridge.ts', {
      './client': { apiClient: { defaults: { headers: { common: {} } } } },
      './bridgeSession': { onNifiSessionChange: () => {} },
    });

    bridge.requestIntegratedCanvasClose();
    assert.equal(parentMessages.length, 1);
    assert.equal(parentMessages[0].payload.type, 'NIFI_APP_CLOSE');
    assert.equal(typeof parentMessages[0].payload.timestamp, 'number');
    assert.deepEqual(localEvents, []);

    const standalone = { dispatchEvent: (event) => localEvents.push(event.type) };
    standalone.parent = standalone;
    global.window = standalone;
    bridge.requestIntegratedCanvasClose();
    assert.equal(parentMessages.length, 1);
    assert.deepEqual(localEvents, ['haitong:nifi-close-requested']);
  } finally {
    global.window = previousWindow;
    global.CustomEvent = previousCustomEvent;
  }
});

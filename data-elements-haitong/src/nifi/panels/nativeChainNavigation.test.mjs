import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { compiler } from '../../../scripts/compiler.mjs';
import { resolveNifiNativeUiUrl } from '../api/nifiNativeUrl.ts';

function nativeUiTarget(pipelineId) {
  const processGroupId = `group-${pipelineId}`;
  return { url: `https://node-${pipelineId}.example/nifi/#/process-groups/${processGroupId}`,
    processGroupId, directNode: true, reusesBrowserSession: false, readOnly: false, authenticatedByBackend: false };
}

function nativeDetailsHandler({ deployment, resolveUiLink = async id => nativeUiTarget(id), ...overrides } = {}) {
  const ts = compiler();
  const source = fs.readFileSync(new URL('./Toolbar.tsx', import.meta.url), 'utf8');
  const ast = ts.createSourceFile('Toolbar.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let handler;
  const visit = node => {
    if (ts.isVariableDeclaration(node) && node.name.getText(ast) === 'handleOpenNifi') handler = node.initializer.getText(ast);
    ts.forEachChild(node, visit);
  };
  visit(ast);
  assert.ok(handler);
  const events = [];
  const popup = { opener: {}, close() { events.push('closed'); }, location: { replace(url) { events.push(['navigate', url]); } } };
  const window = {
    haitongDeployment: deployment,
    open(url, target) { events.push(['open', url, target]); return popup; },
    location: { origin: 'http://demo.example' },
  };
  const compilerOptions = { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS };
  const scope = { currentPipelineId: 'p1', canOpenNative: true,
    nativeDetails: { tooltip: '当前任务尚未部署到 NiFi' }, isCurrentSession: () => true,
    window,
    getNifiUiLink: id => { events.push(['request', id]); return resolveUiLink(id); },
    resolveNifiNativeUiUrl,
    setNativeChecking: value => events.push(['checking', value]),
    message: { error: value => events.push(['error', value]), warning: value => events.push(['warning', value]) },
    ...overrides };
  const code = ts.transpileModule(`(${handler})`, { compilerOptions }).outputText;
  return { run: vm.runInNewContext(code, scope), events, popup };
}

test('details opens the selected pipeline directly and reserves the tab before awaiting its URL', async () => {
  for (const currentPipelineId of ['p0', 'p1']) {
    const link = Promise.withResolvers();
    const { run, events, popup } = nativeDetailsHandler({ currentPipelineId, resolveUiLink: () => link.promise });
    const navigation = run();
    assert.deepEqual(events, [['open', '', '_blank'], ['checking', true], ['request', currentPipelineId]]);
    assert.equal(popup.opener, null);
    link.resolve(nativeUiTarget(currentPipelineId));
    await navigation;
    assert.deepEqual(events.slice(3), [
      ['navigate', `https://node-${currentPipelineId}.example/nifi/#/process-groups/group-${currentPipelineId}`],
      ['checking', false],
    ]);
  }
});

test('standalone details also uses the deployed task node instead of a configured default', async () => {
  const { run, events } = nativeDetailsHandler({
    deployment: {
      tenantId: 'validation-tenant', appId: 'validation-app',
      nifiUiUrl: 'https://nifi.example/gateway/nifi/?view=previous#/previous',
    },
  });
  await run();
  assert.deepEqual(events.slice(2), [
    ['request', 'p1'],
    ['navigate', 'https://node-p1.example/nifi/#/process-groups/group-p1'],
    ['checking', false],
  ]);
});

test('the deployed node link takes precedence over the standalone default NiFi address', async () => {
  const url = 'https://assigned-nifi.example/nifi/#/process-groups/group-p1';
  const { run, events } = nativeDetailsHandler({
    deployment: { tenantId: 'validation-tenant', appId: 'validation-app', nifiUiUrl: 'https://default-nifi.example/nifi/' },
    resolveUiLink: async id => ({ ...nativeUiTarget(id), url, directNode: true, reusesBrowserSession: false }),
  });
  await run();
  assert.deepEqual(events.slice(2), [['request', 'p1'], ['navigate', url], ['checking', false]]);
});

test('details never falls back to the retired read-only platform proxy', async () => {
  const { run, events } = nativeDetailsHandler({
    resolveUiLink: async id => ({ ...nativeUiTarget(id), url: '/nifi-ui/#/process-groups/group-p1', directNode: false }),
  });
  await run();
  assert.ok(events.includes('closed'));
  assert.equal(events.some(value => Array.isArray(value) && value[0] === 'navigate'), false);
  assert.ok(events.some(value => Array.isArray(value) && value[0] === 'error'));
});

test('native browser links allow HTTP and HTTPS but never script URLs or embedded credentials', () => {
  for (const url of ['http://node.example/nifi/#/process-groups/group', 'https://node.example/gateway/nifi/#/process-groups/group']) {
    assert.equal(resolveNifiNativeUiUrl(url), url);
  }
  for (const url of ['javascript:alert(1)', 'data:text/html,example', 'https://user:password@node.example/nifi/', '/nifi-ui/']) {
    assert.throws(() => resolveNifiNativeUiUrl(url));
  }
});

test('details never falls back to another deployed task when the selected task cannot be opened', async () => {
  for (const overrides of [{ canOpenNative: false }, { currentPipelineId: null }, { isCurrentSession: () => false }]) {
    const { run, events } = nativeDetailsHandler(overrides);
    await run();
    assert.equal(events.some(value => Array.isArray(value) && value[0] === 'request'), false);
    assert.equal(events.some(value => Array.isArray(value) && value[0] === 'open'), false);
  }
});

test('details closes its reserved tab on failure or a changed authentication session', async () => {
  let sessionCurrent = true;
  for (const overrides of [
    { resolveUiLink: async () => { throw new Error('unavailable'); } },
    { isCurrentSession: () => sessionCurrent, resolveUiLink: async id => { sessionCurrent = false; return nativeUiTarget(id); } },
  ]) {
    const { run, events } = nativeDetailsHandler(overrides);
    await run();
    assert.ok(events.includes('closed'));
    assert.equal(events.some(value => Array.isArray(value) && value[0] === 'navigate'), false);
    assert.deepEqual(events.at(-1), ['checking', false]);
  }
});

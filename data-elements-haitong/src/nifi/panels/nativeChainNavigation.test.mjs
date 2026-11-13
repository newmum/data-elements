import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { compiler } from '../../../scripts/compiler.mjs';
import { routes } from '../../app/routes.ts';
import { resolveNifiNativeUiUrl } from '../api/nifiNativeUrl.ts';

function nativeUiTarget(pipelineId) {
  const processGroupId = `group-${pipelineId}`;
  return { url: `/nifi-ui/#/process-groups/${processGroupId}`, processGroupId, reusesBrowserSession: true };
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
  const deploymentSource = fs.readFileSync(new URL('../../app/deployment.ts', import.meta.url), 'utf8');
  const deploymentExports = {};
  vm.runInNewContext(ts.transpileModule(deploymentSource, { compilerOptions }).outputText, {
    window, URL, exports: deploymentExports,
    require(name) { assert.equal(name, './routes'); return { routes }; },
  });
  const scope = { currentPipelineId: 'p1', canOpenNative: true,
    nativeDetails: { tooltip: '当前任务尚未部署到 NiFi' }, isCurrentSession: () => true,
    window,
    getNifiUiLink: id => { events.push(['request', id]); return resolveUiLink(id); },
    standaloneNifiUiUrl: deploymentExports.standaloneNifiUiUrl,
    resolveNifiNativeUiUrl,
    apiClient: { defaults: { baseURL: '/prod-api/nifi/api' } },
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
      ['navigate', `http://demo.example/prod-api/nifi-ui/#/process-groups/group-${currentPipelineId}`],
      ['checking', false],
    ]);
  }
});

test('standalone details uses the configured HTTPS NiFi path and the resolved process group', async () => {
  const { run, events } = nativeDetailsHandler({
    deployment: {
      tenantId: 'validation-tenant', appId: 'validation-app',
      nifiUiUrl: 'https://nifi.example/gateway/nifi/?view=previous#/previous',
    },
  });
  await run();
  assert.deepEqual(events.slice(2), [
    ['request', 'p1'],
    ['navigate', 'https://nifi.example/gateway/nifi/#/process-groups/group-p1'],
    ['checking', false],
  ]);
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

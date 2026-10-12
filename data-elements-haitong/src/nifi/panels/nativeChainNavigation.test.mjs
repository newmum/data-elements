import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { compiler } from '../../../scripts/compiler.mjs';
import { resolveNifiNativeUiUrl } from '../api/nifiNativeUrl.ts';

function nativeDetailsHandler(overrides = {}) {
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
  const scope = { currentPipelineId: 'p1', canOpenNative: true, isCurrentSession: () => true,
    window: { open(url) { events.push(['open', url]); return popup; }, location: { origin: 'http://demo.example' } },
    getNifiUiLink: async id => { events.push(['request', id]); return { url: '/nifi-ui/?processGroupId=' + id }; },
    resolveNifiNativeUiUrl,
    apiClient: { defaults: { baseURL: '/prod-api/nifi/api' } },
    setNativeChecking: value => events.push(['checking', value]),
    message: { error: value => events.push(['error', value]), warning: value => events.push(['warning', value]) },
    ...overrides };
  const code = ts.transpileModule(`(${handler})`, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  return { run: vm.runInNewContext(code, scope), events, popup };
}

test('details opens the selected pipeline directly and reserves the tab before awaiting its URL', async () => {
  for (const currentPipelineId of ['p0', 'p1']) {
    const { run, events, popup } = nativeDetailsHandler({ currentPipelineId });
    await run();
    assert.deepEqual(events.filter(value => Array.isArray(value) && ['open', 'request', 'navigate'].includes(value[0])),
      [['open', ''], ['request', currentPipelineId], ['navigate', `http://demo.example/prod-api/nifi-ui/?processGroupId=${currentPipelineId}`]]);
    assert.equal(popup.opener, null);
    assert.deepEqual(events.at(-1), ['checking', false]);
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
    { getNifiUiLink: async () => { throw new Error('unavailable'); } },
    { isCurrentSession: () => sessionCurrent, getNifiUiLink: async () => { sessionCurrent = false; return { url: '/nifi-ui/' }; } },
  ]) {
    const { run, events } = nativeDetailsHandler(overrides);
    await run();
    assert.ok(events.includes('closed'));
    assert.equal(events.some(value => Array.isArray(value) && value[0] === 'navigate'), false);
    assert.deepEqual(events.at(-1), ['checking', false]);
  }
});

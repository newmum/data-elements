import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { compiler } from '../../../scripts/compiler.mjs';

const ts = compiler();
function load(file, resolve) {
  const source = fs.readFileSync(new URL(file, import.meta.url), 'utf8').replaceAll('import.meta.env', '({})');
  const exports = {};
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  vm.runInNewContext(compiled, { exports, require: resolve });
  return exports;
}
const { groupActiveFlowErrors } = load('../runtime/activeFlowErrors.ts', () => ({}));
const { bulletinKey, visibleFlowErrors, visibleFlowBulletins } = load('../runtime/dismissedFlowErrors.ts', () => ({}));

test('canvas fallback clears history and badges across polling, but later identical errors stay visible', async () => {
  for (const response of ['old-backend', 'native-cleared', 'native-unsupported', 'unreachable']) {
    const history = { id: 'old-history', phase: 'VALIDATION', nodeId: 'source', level: 'ERROR', message: 'failed' };
    const bulletin = { id: 'old', sourceId: 'processor', sourceName: 'source', message: 'failed', level: 'ERROR', timestamp: '2026-11-13T10:00:00Z' };
    const snapshot = { errors: [history], nodeMapping: { processorIdToCanvasNode: { processor: 'source' } } };
    const state = { failures: [], dismissed: {}, dismiss(id, errors, bulletins) {
      this.dismissed[id] = { historyIds: new Set(errors.map(row => row.id)), bulletinKeys: new Set(bulletins.map(bulletinKey)) };
    } };
    const requests = [];
    const hooks = load('../api/pipelines.ts', id => {
      if (id === '@tanstack/react-query') return { useMutation: options => options, useQueryClient: () => ({
        getQueryData: key => key[0] === 'pipeline-status' ? snapshot : key[0] === 'pipeline-errors' ? [history] : [bulletin],
        invalidateQueries: async () => {},
      }) };
      if (id === './bridgeSession') return { getSessionRevision: () => 1 };
      if (id === '../stores/errorPanelStore') return { useErrorPanelStore: { getState: () => state } };
      if (id === './client') return { apiClient: { post: async path => {
        requests.push(path);
        if (response === 'unreachable') throw { response: { status: 502 } };
        return { data: response === 'old-backend' ? undefined : { historyCleared: true, bulletinsCleared: 1, nativeCleared: response === 'native-cleared' } };
      } } };
      return {};
    });
    const result = await hooks.useClearFlowErrors().mutationFn('p');
    assert.equal(result.nativeCleared, response === 'native-cleared');
    assert.deepEqual(requests, ['/pipelines/delete/p/errors']);
    const cleared = state.dismissed.p;
    assert.equal(visibleFlowErrors([history], cleared).length, 0);
    assert.equal(visibleFlowBulletins([bulletin], cleared).length, 0);
    assert.equal(Object.keys(groupActiveFlowErrors({ ...snapshot, errors: visibleFlowErrors([history], cleared) },
      visibleFlowBulletins([bulletin], cleared), new Set(['source']))).length, 0);
    const later = { ...bulletin, id: 'new' };
    assert.equal(visibleFlowBulletins([bulletin, later], cleared).length, 1);
    assert.equal(visibleFlowErrors([history, { ...history, id: 'new-history' }], cleared).length, 1);
    assert.equal(visibleFlowBulletins([bulletin], state.dismissed.other).length, 1);
    assert.equal(groupActiveFlowErrors(snapshot, [later], new Set(['source'])).source.length, 2);
  }
});

test('clear response from a previous login cannot dismiss the new session diagnostics', async () => {
  let revision = 1, dismissed = false;
  const hooks = load('../api/pipelines.ts', id => {
    if (id === '@tanstack/react-query') return { useMutation: options => options, useQueryClient: () => ({ getQueryData: () => undefined }) };
    if (id === './bridgeSession') return { getSessionRevision: () => revision };
    if (id === '../stores/errorPanelStore') return { useErrorPanelStore: { getState: () => ({ failures: [], dismiss: () => { dismissed = true; } }) } };
    if (id === './client') return { apiClient: { post: async () => { revision++; return { data: {} }; } } };
    return {};
  });
  await assert.rejects(hooks.useClearFlowErrors().mutationFn('p'), /登录状态已变化/);
  assert.equal(dismissed, false);
});

test('persisted runtime records do not color a recovered node red; live bulletin still does', () => {
  const history = { id: 'record', phase: 'RUNTIME', nodeId: 'source', nodeLabel: '来源/清单', level: 'ERROR', message: 'previous authentication failure', occurredAt: '2026-10-24T00:00:00Z' };
  const status = { status: 'RUNNING', errors: [history], nodeMapping: { processorIdToCanvasNode: { processor: 'source' } } };
  const ids = new Set(['source']);
  assert.equal(Object.keys(groupActiveFlowErrors(status, [], ids)).length, 0);
  const result = groupActiveFlowErrors(status, [{ id: 'live', level: 'ERROR', sourceName: history.nodeLabel, message: 'ListSFTP[id=processor] Authentication failed', timestamp: 'now' }], ids);
  assert.equal(result.source.length, 1);
  assert.equal(result.source[0].id, 'live-live');
  assert.equal(result.source[0].message.includes('Authentication failed'), true);
  assert.equal(Object.keys(groupActiveFlowErrors(status, [], ids)).length, 0, 'cleared live bulletins remove the runtime badge without deleting history');
  assert.equal(status.errors.length, 1);
});

test('unknown processor IDs, foreign node IDs and ambiguous legacy labels never paint another node', () => {
  const status = { errors: [
    { phase: 'RUNTIME', nodeId: 'a', nodeLabel: 'same' }, { phase: 'RUNTIME', nodeId: 'b', nodeLabel: 'same' },
  ], nodeMapping: { processorIdToCanvasNode: { foreign: 'outside' } } };
  const bulletins = ['foreign', 'unknown'].map(id => ({ id, level: 'ERROR', sourceName: 'same', message: `Processor[id=${id}] failed`, timestamp: 'now' }));
  bulletins.push({ id: 'old-format', level: 'ERROR', sourceName: 'same', message: 'no ID', timestamp: 'now' });
  assert.equal(Object.keys(groupActiveFlowErrors(status, bulletins, new Set(['a', 'b']))).length, 0);
  const validation = { id: 'validation', phase: 'VALIDATION', nodeId: 'a', message: 'missing required value', level: 'ERROR' };
  assert.equal(groupActiveFlowErrors({ errors: [validation] }, [], new Set(['a'])).a[0], validation);
});

test('20/100 nodes share one live-bulletin query; rendering a node performs no read request', async () => {
  for (const count of [20, 100]) {
    const queries = [], requests = [];
    const hooks = load('../api/pipelines.ts', id => {
      if (id === '@tanstack/react-query') return { useQuery: options => { queries.push(options); return options; } };
      if (id === './bridgeSession') return { useTokenReady: () => true, useSessionRevision: () => 1 };
      if (id === './client') return { apiClient: { get: async path => { requests.push(path); return { data: [] }; } } };
      if (id === './response') return { requireListResponse: value => value };
      return {};
    });
    hooks.useLiveFlowBulletins('selected', true);
    assert.equal(queries.length, 1);
    await queries[0].queryFn({ signal: undefined });
    assert.deepEqual(requests, ['/pipelines/selected/bulletins/live']);
    let nodeReads = 0;
    const { default: BaseNode } = load('../canvas/BaseNode.tsx', id => {
      if (id === 'react/jsx-runtime') return { jsx: React.createElement, jsxs: React.createElement };
      if (id === '@ant-design/icons') return new Proxy({}, { get: () => () => null });
      if (id === 'antd') return { Tooltip: props => props.children };
      if (id === '@/stores/canvasStore') return { useCanvasStore: select => select({ currentPipelineId: 'selected', focusNodeNonce: 0 }) };
      if (id === '@/stores/errorPanelStore') return { useErrorPanelStore: select => select({ open() {} }) };
      if (id === '@/panels/databaseIcons') return { COMPONENT_ICONS: {}, getComponentIcon: () => null };
      if (id === '../utils/fieldGovernancePresentation') return { governanceNodeLabel: value => value.label };
      if (id.includes('/api/')) { nodeReads++; throw new Error('a node must not load data'); }
      return {};
    });
    for (let i=0; i<count; i++) {
      const node = { id: `node-${i}`, label: '来源', manifestKey: 'source.sftp', category: 'source', config: {} };
      const html = renderToStaticMarkup(React.createElement(BaseNode, { node: { getData: () => ({ node, runtime: { state: 'running' }, errors: [] }) } }));
      assert.match(html, /is-running/);
      assert.doesNotMatch(html, /is-error/);
    }
    assert.equal(nodeReads, 0);
    const disabled = hooks.useLiveFlowBulletins('draft', false);
    assert.equal(disabled.enabled, false);
  }
});

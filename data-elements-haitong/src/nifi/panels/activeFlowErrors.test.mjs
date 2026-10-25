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

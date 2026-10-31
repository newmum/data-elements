import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { createRequire } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { compiler } from '../../../scripts/compiler.mjs';
import { flowActionFailure, mergeFlowErrors, diagnosticText } from '../utils/flowActionFailure.ts';

const failure = (count = 20) => ({ response: { status: 422, data: { code: 'MAPPING_PREFLIGHT_FAILED',
  error: 'MAPPING_PREFLIGHT_FAILED', message: '字段映射发布前检查失败', detail: `${count} 个字段：NiFi 缺少 1.1.12 扩展`, traceId: 'trace-fixture' } },
  config: { method: 'post', url: '/pipelines/p/deploy' }, message: 'Request failed with status code 422' });

test('failed responses keep the concrete reason, status, trace and endpoint; empty polling cannot erase them', () => {
  const row = flowActionFailure(failure(), 'deploy');
  assert.equal(row.message, '字段映射发布前检查失败');
  for (const text of ['NiFi 缺少 1.1.12', 'HTTP 422', 'trace-fixture', '/pipelines/p/deploy']) assert.ok(row.detail.includes(text));
  assert.equal(mergeFlowErrors([], [row]).length, 1);
  assert.equal(mergeFlowErrors([row], [row]).length, 1);
});

test('structured server error has the same identity after persistence and polling', () => {
  const stored = { id: 'server-error', phase: 'DEPLOYMENT', level: 'ERROR', message: 'NiFi 属性错误', detail: 'Record Writer 未启用', occurredAt: '2026-10-15T10:00:00Z' };
  const row = flowActionFailure({ response: { status: 400, data: { flowError: stored } } }, 'deploy');
  assert.equal(row.id, stored.id);
  assert.equal(mergeFlowErrors([stored], [row]).length, 1);
});

test('timeouts, proxy HTML, empty bodies and platform envelopes remain actionable and secrets are hidden', () => {
  const timeout = flowActionFailure({ code: 'ECONNABORTED', message: 'timeout' }, 'deploy');
  assert.match(timeout.message, /执行结果尚未确认/);
  assert.match(timeout.suggestion, /不要连续重复提交/);
  assert.match(flowActionFailure({ response: { status: 502, data: '<html><body>502 Bad Gateway</body></html>' } }, 'deploy').message, /502 Bad Gateway/);
  assert.match(flowActionFailure({ response: { status: 500, data: '' } }, 'save').message, /未返回错误说明/);
  const nested = flowActionFailure({ payload: { code: 500, data: { error: { message: 'DB unavailable' }, detail: 'password=private-value; token=secret-value' } } }, 'deploy');
  assert.match(nested.message, /DB unavailable/);
  assert.doesNotMatch(nested.detail, /private-value|secret-value/);
  assert.doesNotMatch(diagnosticText('jdbc:mysql://user:private-value@host/db?password=secret-value'), /private-value|secret-value/);
});

function harness(post) {
  const failures = [], requests = [], clears = [];
  let revision = 1;
  const state = { currentPipelineId: 'p' };
  const ts = compiler(), exports = {};
  const source = fs.readFileSync(new URL('../api/pipelines.ts', import.meta.url), 'utf8').replaceAll('import.meta.env', '({})');
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  vm.runInNewContext(compiled, { exports, require(id) {
    if (id === '@tanstack/react-query') return { useMutation: options => options, useQueryClient: () => ({ getQueryData: () => undefined }) };
    if (id === './client') return { apiClient: { post: async (path, body) => { requests.push(path); return post(path, body); } } };
    if (id === './bridgeSession') return { getSessionRevision: () => revision };
    if (id === '../stores/canvasStore') return { useCanvasStore: { getState: () => state } };
    if (id === '../stores/taskChainStore') return { useTaskChainStore: { getState: () => ({ chain: null }) } };
    if (id === '../stores/errorPanelStore') return { useErrorPanelStore: { getState: () => ({ record: (...args) => failures.push(args), clearFailures: (...args) => clears.push(args) }) } };
    if (id === '../utils/flowActionFailure') return { flowActionFailure };
    if (id === '../utils/incrementalFields') return { incrementalConfigurationError: () => undefined };
    return {};
  } });
  return { hooks: exports, failures, requests, clears, state, switchSession: () => { revision++; } };
}

test('20/100 mapping fields both make one deploy request and no per-row diagnostic reads', async () => {
  for (const count of [20, 100]) {
    const h = harness(async () => { throw failure(count); });
    await assert.rejects(h.hooks.useDeployPipeline().mutationFn({ id: 'p' }));
    assert.deepEqual(h.requests, ['/pipelines/p/deploy']);
    assert.equal(h.failures.length, 1);
    assert.equal(h.failures[0][0], 'p');
    assert.equal(h.failures[0][3], true);
    assert.match(h.failures[0][2].detail, new RegExp(`${count} 个字段`));
  }
});

test('save, start, stop also capture failed requests and a successful retry clears only its own action', async () => {
  for (const [hook, args, action] of [['useSavePipeline', { id: 'p', name: 'x', dsl: {} }, 'save'], ['useStartPipeline', { id: 'p' }, 'start'], ['useStopPipeline', 'p', 'stop']]) {
    const h = harness(async () => { throw new Error('synthetic failure'); });
    await assert.rejects(h.hooks[hook]().mutationFn(args));
    assert.equal(h.failures[0][1], action);
  }
  const h = harness(async () => ({ data: { pipeline: { id: 'p' } } }));
  await h.hooks.useDeployPipeline().mutationFn({ id: 'p' });
  assert.deepEqual(h.clears, [['p', 'deploy']]);
});

test('late errors never leak across sessions or open an unrelated selected task; invalid HTTP 200 is rejected', async () => {
  const h = harness(async () => { h.switchSession(); throw failure(); });
  await assert.rejects(h.hooks.useDeployPipeline().mutationFn({ id: 'p' }));
  assert.equal(h.failures.length, 0);
  const switched = harness(async () => { switched.state.currentPipelineId = 'other'; throw failure(); });
  await assert.rejects(switched.hooks.useDeployPipeline().mutationFn({ id: 'p' }));
  assert.equal(switched.failures[0][0], 'p');
  assert.equal(switched.failures[0][3], false);
  const bad = harness(async () => ({ data: { success: false, message: 'invalid processor' } }));
  await assert.rejects(bad.hooks.useDeployPipeline().mutationFn({ id: 'p' }));
  assert.match(bad.failures[0][2].message, /invalid processor/);
});

test('the actual error panel renders the failed response even when the server error list is empty/unavailable', () => {
  const ts = compiler(), localRequire = createRequire(import.meta.url), exports = {};
  const source = fs.readFileSync(new URL('./ErrorPanel.tsx', import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  const row = flowActionFailure(failure(), 'deploy');
  vm.runInNewContext(compiled, { exports, require(id) {
    if (id === 'react' || id === 'react/jsx-runtime') return localRequire(id);
    if (id === '@ant-design/icons') return new Proxy({}, { get: () => () => null });
    if (id === 'antd') return { App: { useApp: () => ({ message: {} }) },
      Drawer: p => React.createElement('div', null, p.title, p.children),
      Segmented: p => React.createElement('div', null, p.options.map(row => row.label).join(' ')),
      Empty: p => React.createElement('div', null, p.description), Button: p => React.createElement('button', null, p.children),
      Tag: p => React.createElement('span', null, p.children), Tooltip: p => p.children, Alert: p => React.createElement('div', null, p.message) };
    if (id === '@/stores/errorPanelStore') return { useErrorPanelStore: select => select({ visible: true, failures: [{ pipelineId: 'p', error: row }], failureSequence: 1 }) };
    if (id === '@/stores/canvasStore') return { useCanvasStore: select => select({ currentPipelineId: 'p' }) };
    if (id === '@/api/pipelines') return { useFlowErrors: () => ({ data: [], isError: true, error: new Error('poll failed') }), useClearFlowErrors: () => ({}) };
    if (id === '../api/bridgeSession') return { useSessionRevision: () => 1, useTokenReady: () => false };
    if (id === '../utils/flowActionFailure') return { mergeFlowErrors };
    return {};
  } });
  const html = renderToStaticMarkup(React.createElement(exports.default));
  assert.match(html, /错误与告警记录/);
  assert.match(html, /部署 \(1\)/);
  assert.match(html, /字段映射发布前检查失败/);
  assert.match(html, /NiFi 缺少 1.1.12 扩展/);
  assert.doesNotMatch(html, /暂无错误/);
});

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { createRequire } from 'node:module';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { compiler } from '../../../scripts/compiler.mjs';

function harness(chain) {
  const ts = compiler(), mutations = [], requests = [];
  const source = fs.readFileSync(new URL('../api/pipelines.ts', import.meta.url), 'utf8').replaceAll('import.meta.env', '({})');
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  vm.runInNewContext(compiled, { exports, require(id) {
    if (id === '@tanstack/react-query') return {
      useQueryClient: () => ({}), useMutation(options) { mutations.push(options); return options; },
    };
    if (id === './client') return { apiClient: {
      get: async path => { requests.push(path); return { data: chain }; },
      post: async path => { requests.push(path); return { data: {} }; },
    } };
    if (id === '../stores/taskChainStore') return { useTaskChainStore: { getState: () => ({ chain }) } };
    if (id === '../stores/canvasStore') return { useCanvasStore: { getState: () => ({ currentPipelineId: 'selected' }) } };
    return {};
  } });
  return { hooks: exports, mutations, requests };
}

test('20/100 task snapshots each use one chain request and preserve a read-only response', async () => {
  for (const count of [20, 100]) {
    const chain = { selectedPipelineId: 'selected', readOnly: true, warnings: ['来源库已更换'],
      items: Array.from({ length: count }, (_, i) => ({ taskId: `t${i}`, pipeline: { id: `p${i}` } })) };
    const { hooks, requests } = harness(chain);
    const response = await hooks.fetchTaskChain({ accessTaskId: 'task' });
    assert.equal(response, chain);
    assert.deepEqual(requests, ['/pipelines/task-chain']);
  }
});

test('read-only saved design blocks save/copy/deploy/start/stop/delete/native calls before any request', async () => {
  const { hooks, requests } = harness({ selectedPipelineId: 'selected', readOnly: true });
  await assert.rejects(hooks.useSavePipeline().mutationFn({ id: 'selected', name: 'saved', dsl: {} }), /仅可查看/);
  await assert.rejects(hooks.useSavePipeline().mutationFn({ name: 'copy', copy: true, dsl: {} }), /仅可查看/);
  await assert.rejects(hooks.useDeployPipeline().mutationFn({ id: 'selected' }), /仅可查看/);
  await assert.rejects(hooks.useStartPipeline().mutationFn({ id: 'selected' }), /仅可查看/);
  await assert.rejects(hooks.useStopPipeline().mutationFn('selected'), /仅可查看/);
  await assert.rejects(hooks.useDeletePipeline().mutationFn('selected'), /仅可查看/);
  await assert.rejects(hooks.getNifiUiLink('selected'), /仅可查看/);
  assert.equal(requests.length, 0);
});

test('compatibility state never disables an unrelated current pipeline operation', async () => {
  const { hooks, requests } = harness({ selectedPipelineId: 'selected', readOnly: true });
  await hooks.useStopPipeline().mutationFn('other');
  assert.deepEqual(requests, ['/pipelines/other/stop']);
});

test('the actual editor renders the saved canvas with its warning only for the selected read-only task', () => {
  const ts = compiler(), localRequire = createRequire(import.meta.url);
  const source = fs.readFileSync(new URL('../pages/EditorPage.tsx', import.meta.url), 'utf8');
  const compiled = ts.transpileModule(source, { compilerOptions: {
    target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX,
  } }).outputText;
  const chain = { readOnly: true, selectedPipelineId: 'selected', warnings: ['来源库已更换，任务关联需核对'] };
  for (const currentPipelineId of ['selected', 'other']) {
    const exports = {};
    vm.runInNewContext(compiled, { exports, require(id) {
      if (id === 'react' || id === 'react/jsx-runtime' || id === 'antd') return localRequire(id);
      if (id === '@tanstack/react-query') return { useQueryClient: () => ({}) };
      if (id === '@ant-design/icons') return {};
      if (id === '@/panels/Toolbar') return { default: () => null };
      if (id === '@/canvas/Canvas') return { default: () => React.createElement('div', { 'data-testid': 'saved-canvas' }, 'saved') };
      if (id === '@/api/manifests') return { useComponentManifests: () => ({ isLoading: false, isError: false }) };
      if (id === '@/stores/canvasStore') return { useCanvasStore: select => select({ currentPipelineId, drawerNodeId: null }) };
      if (id === '@/stores/errorPanelStore') return { useErrorPanelStore: select => select({ visible: false }) };
      if (id === '@/stores/lineageStore') return { useLineageStore: select => select({ nodeId: null }) };
      if (id === '../stores/taskChainStore') return { useTaskChainStore: select => select({ chain }) };
      if (id === '@/api/iframeBridge') return { getPipelineId: () => null, getAccessTaskId: () => null, getTid: () => null };
      return {};
    } });
    const markup = renderToStaticMarkup(React.createElement(exports.default));
    assert.match(markup, /data-testid="saved-canvas"/);
    assert.equal(markup.includes('已打开当前任务保存的画布（只读）'), currentPipelineId === 'selected');
    assert.equal(markup.includes('来源库已更换，任务关联需核对'), currentPipelineId === 'selected');
  }
});

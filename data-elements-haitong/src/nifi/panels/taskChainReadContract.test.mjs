import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { compiler } from '../../../scripts/compiler.mjs';

function pipelineHooks(queries, requests) {
  const ts = compiler();
  const source = fs.readFileSync(new URL('../api/pipelines.ts', import.meta.url), 'utf8').replaceAll('import.meta.env', '({})');
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  const exports = {};
  vm.runInNewContext(compiled, { exports, require(id) {
    if (id === '@tanstack/react-query') return { useQuery(options) { queries.push(options); return {}; } };
    if (id === './client') return { apiClient: { get: async path => { requests.push(path); return { data: {} }; } } };
    if (id === './bridgeSession') return { useTokenReady: () => true, useSessionRevision: () => 7 };
    return {};
  } });
  return exports;
}

test('actual pipeline cache-only hook never calls legacy detail even after stale/focus/reconnect or manual refetch', async () => {
  const queries = [], requests = [], hooks = pipelineHooks(queries, requests);
  for (const count of [20, 100]) {
    for (let index = 0; index < count; index++) {
      hooks.usePipeline(`allowed-${index}`, false);
      const query = queries.at(-1);
      assert.equal(query.enabled, false);
      assert.equal(query.staleTime, Infinity);
      assert.equal(query.refetchOnMount, false);
      assert.equal(query.refetchOnWindowFocus, false);
      assert.equal(query.refetchOnReconnect, false);
      await assert.rejects(query.queryFn({ signal: new AbortController().signal }), /只读链路快照/);
    }
  }
  assert.equal(requests.length, 0);
  hooks.usePipeline('independent', true);
  assert.equal(queries.at(-1).enabled, true);
  await queries.at(-1).queryFn({ signal: new AbortController().signal });
  assert.deepEqual(requests, ['/pipelines/independent']);
});

test('actual Toolbar explicitly selects cache-only saved-design reads for every authorized chain task', () => {
  const ts = compiler();
  const source = fs.readFileSync(new URL('./Toolbar.tsx', import.meta.url), 'utf8');
  const ast = ts.createSourceFile('Toolbar.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const definitions = new Map();
  const visit = node => {
    if (ts.isVariableDeclaration(node) && ['hasSelectedChainTask', 'currentPipelineQuery'].includes(node.name.getText(ast))) {
      definitions.set(node.name.getText(ast), node.initializer.getText(ast));
    }
    ts.forEachChild(node, visit);
  };
  visit(ast);
  assert.equal(definitions.size, 2);
  const expressions = `const hasSelectedChainTask = ${definitions.get('hasSelectedChainTask')}; const currentPipelineQuery = ${definitions.get('currentPipelineQuery')};`;
  for (const count of [1, 20, 100]) {
    const taskChain = { items: Array.from({ length: count }, (_, i) => ({ taskId: `t${i}`, pipeline: { id: `p${i}` } })) };
    for (const currentPipelineId of ['p0', `p${count - 1}`]) {
      const calls = [];
      vm.runInNewContext(expressions, { taskChain, currentPipelineId, usePipeline: (id, allowNetwork) => calls.push({ id, allowNetwork }) });
      assert.deepEqual(calls, [{ id: currentPipelineId, allowNetwork: false }]);
    }
  }
  const calls = [];
  vm.runInNewContext(expressions, { taskChain: { items: [{ taskId: '', pipeline: { id: 'independent' } }] }, currentPipelineId: 'independent',
    usePipeline: (id, allowNetwork) => calls.push({ id, allowNetwork }) });
  assert.deepEqual(calls, [{ id: 'independent', allowNetwork: true }]);
});

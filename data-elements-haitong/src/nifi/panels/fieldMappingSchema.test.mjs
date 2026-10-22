import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { compiler } from '../../../scripts/compiler.mjs';

// 执行实际编辑器入口和 effect，验证引用变化后重新加载，而不是只检查源码包含某段文字。
function editorHarness() {
  const ts = compiler(), states = [], effects = [], requests = [];
  let cursor = 0, pending = [];
  const hooks = {
    useMemo: fn => fn(),
    useState(initial) {
      const id = cursor++;
      if (!(id in states)) states[id] = typeof initial === 'function' ? initial() : initial;
      return [states[id], value => { states[id] = typeof value === 'function' ? value(states[id]) : value; }];
    },
    useEffect(fn, deps) {
      const id = cursor++, previous = effects[id];
      if (!previous || deps.some((value, index) => value !== previous.deps[index])) {
        previous?.cleanup?.();
        effects[id] = { deps };
        pending.push(() => { effects[id].cleanup = fn(); });
      }
    },
  };
  const api = {
    mappingVersions: { schema: async (sourceRef, targetRef) => {
      requests.push({ sourceRef, targetRef });
      if (sourceRef) throw new Error('Schema表引用不存在、无权或不唯一');
      return { targetRef, target: [{ name: 'id' }] };
    } },
    compileFieldMapping: async spec => ({ plan: { executionPlan: { ...spec, schemaVersion: '2.0', rules: [] } } }),
  };
  const exports = {};
  const code = ts.transpileModule(fs.readFileSync(new URL('./FieldMappingEditor.tsx', import.meta.url), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  vm.runInNewContext(code, { exports, require(name) {
    if (name === 'react') return hooks;
    if (name === 'react/jsx-runtime') return { jsx: (type, props) => ({ type, props }) };
    if (name === '../api/fieldMapping') return api;
    if (name === './unifiedRuleModel') return { assertExactJsonNumbers() {} };
    if (name === 'antd') return { Alert: 'Alert', Button: 'Button', Skeleton: 'Skeleton' };
    return {};
  } });
  return { requests, render(props) { cursor = 0; const result = exports.default(props); const work = pending; pending = []; work.forEach(fn => fn()); return result; } };
}
const flush = () => new Promise(resolve => setImmediate(resolve));

test('a changed table reference reloads a legacy editor without retry or loss of its rules', async () => {
  const h = editorHarness(), targetRef = { datasourceId: 'target-db', tableId: 'target-table', table: 'events' };
  const value = { version: '1.0', mappings: [{ from: '/id', to: '/id' }] };
  const original = JSON.stringify(value);
  const props = { value, onChange: assert.fail, assetContext: { sourceRef: { datasourceId: 'file-source', table: 'events' }, targetRef } };
  h.render(props); await flush();
  assert.equal(h.render(props).type, 'Alert');
  const corrected = { ...props, assetContext: { targetRef } };
  h.render(corrected); await flush();
  const loaded = h.render(corrected);
  assert.equal(loaded.type.name, 'FieldMappingEditorContent');
  assert.equal(loaded.props.value.schemaContract.targetRef.tableId, 'target-table');
  assert.equal(h.requests.length, 2);
  assert.equal(h.requests[1].sourceRef, undefined);
  assert.equal(JSON.stringify(value), original, 'Opening or retrying never persists or mutates the original mapping');
});

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import ts from 'typescript';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { checkedDraftKeys, draftOrderRequest } from './mappingDraftOrder.ts';

test('the first render shows every loaded rule, with no pagination or table scroll container', async () => {
  const source = fs.readFileSync(new URL('./MappingDraftTable.tsx', import.meta.url), 'utf8');
  let js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX } }).outputText;
  const mockApi = 'data:text/javascript,' + encodeURIComponent('export function platformApi(){throw new Error("Initial display must not request server ordering")}');
  for (const [from, to] of [['react', import.meta.resolve('react')], ['react/jsx-runtime', import.meta.resolve('react/jsx-runtime')], ['antd', import.meta.resolve('antd')], ['../../api/platformApi', mockApi], ['./mappingDraftOrder', new URL('./mappingDraftOrder.ts', import.meta.url).href]]) {
    js = js.replaceAll(`from '${from}'`, `from '${to}'`).replaceAll(`from "${from}"`, `from "${to}"`);
  }
  const { default: DraftTable } = await import('data:text/javascript;base64,' + Buffer.from(js).toString('base64'));
  for (const size of [20, 100]) {
    const rows = Array.from({ length: size }, (_, index) => ({ id: 'rule-' + index, name: 'field-' + index }));
    const html = renderToStaticMarkup(React.createElement(DraftTable, { rowKey: row => row.id, dataSource: rows, fields: { name: { kind: 'text', value: row => row.name } }, columns: [{ key: 'name', title: '字段', dataIndex: 'name' }] }));
    assert.equal((html.match(/data-row-key=/g) ?? []).length, size);
    assert.doesNotMatch(html, /ant-pagination|ant-table-body|ant-spin-spinning/);
    assert.match(html, new RegExp('field-' + (size - 1)));
  }
});
test('explicit sorting returns the whole bounded draft in one request and preserves source order', () => {
  for (const size of [20, 100]) {
    const items = Array.from({ length: size }, (_, i) => ({ id: 'r' + i, value: i }));
    const before = JSON.stringify(items), request = draftOrderRequest(items, 'number', 'desc');
    assert.equal(request.wholeCollection, true);
    const keys = items.map(item => item.id).reverse();
    assert.deepEqual(checkedDraftKeys(items, { keys, total: size }), keys);
    assert.equal(JSON.stringify(items), before);
  }
  assert.throws(() => draftOrderRequest(Array.from({ length: 1001 }, (_, i) => ({ id: String(i), value: i })), 'number', 'asc'));
});
test('a partial, duplicate or foreign sort result cannot introduce or silently drop draft rows', () => {
  const items = [{ id: 'a', value: 1 }, { id: 'b', value: 2 }];
  for (const result of [{ keys: ['a'], total: 2 }, { keys: ['a', 'foreign'], total: 2 }, { keys: ['a', 'a'], total: 2 }, { keys: ['a', 'b'], total: 3 }]) assert.throws(() => checkedDraftKeys(items, result));
});

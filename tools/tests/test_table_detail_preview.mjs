import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/02.数据盘点/02.登记流程组件/详情页面(detail-page)/表4-数据预览(table-preview).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, {ast: true, code: false, parserOpts: {sourceType: 'module', plugins: ['typescript']}}).ast;
const names = ['previewColumns', 'fetchData'];
const selected = names.map(name => {
  const node = ast.program.body.find(n => n.type === 'VariableDeclaration' && n.declarations.some(d => d.id.name === name));
  assert.ok(node, name);
  return script.slice(node.start, node.end);
});
const code = babel.Babel.transform(selected.join('\n'), {filename: 'preview.ts', presets: ['typescript']}).code;
const reads = [];
let response = {columns: ['ID', 'demo.gender_code', 'updated_at'], columnLabels: {id: '编号', gender_code: '性别代码'}, rows: [], total: 240};
let fail = false;
let release;
const context = vm.createContext({props: {tableId: 'table-a'}, state: {previewCols: [], errMsg: ''}, isString: v => typeof v === 'string',
  h: (tag, props, children) => ({tag, props, children}),
  resolveComponent: name => name,
  $common: {post: async (route, payload) => {
    reads.push({route, payload});
    if (release) await new Promise(resolve => { release = resolve; });
    if (fail) throw new Error('read failed');
    return {...response, rows: Array.from({length: payload.pageSize}, (_, i) => ({ID: (payload.pageNo - 1) * payload.pageSize + i + 1}))};
  }}
});
vm.runInContext(code + '\nglobalThis.actual = {' + names.join(',') + '};', context);
for (const size of [20, 100]) {
  reads.length = 0;
  const page = await context.actual.fetchData({pageNo: 2, pageSize: size, sortField: 'ID', sortDir: 'desc'});
  assert.equal(reads.length, 1);
  assert.equal(page.list.length, size);
  assert.equal(page.list[0].ID, size + 1);
  assert.equal(reads[0].payload.sortField, 'ID');
  assert.equal(context.state.previewCols[0].label, '编号\nID');
  assert.equal(context.state.previewCols[1].label, '性别代码\ngender_code');
  assert.equal(context.state.previewCols[1].prop, 'demo.gender_code');
  const tooltip = context.state.previewCols[1].renderHeader();
  assert.equal(tooltip.props.content, '性别代码\ngender_code');
  const header = tooltip.children.default();
  assert.equal(header.children.length, 2);
  assert.equal(header.children[0].children, '性别代码');
  assert.equal(header.children[1].children, 'gender_code');
  assert.equal(context.state.previewCols[2].label, 'updated_at');
}
response = {...response, columns: ['id'], columnLabels: {id: '  '}, total: 0};
await context.actual.fetchData({pageNo: 1, pageSize: 20});
assert.equal(context.state.previewCols[0].label, 'id');
assert.equal(context.state.previewCols[0].renderHeader().children.default().children[0].children, '—');
response = {...response, columns: ['extremely_long_registered_field_name'], columnLabels: {extremely_long_registered_field_name: '很长的中文字段名称\n换行也不能增加表头行数'}};
await context.actual.fetchData({pageNo: 1, pageSize: 20});
const longHeader = context.state.previewCols[0].renderHeader();
assert.equal(longHeader.children.default().children.length, 2);
assert.ok(longHeader.props.content.includes('extremely_long_registered_field_name'));
fail = true;
assert.equal((await context.actual.fetchData({pageNo: 1, pageSize: 20})).total, 0);
assert.equal(context.state.errMsg, 'read failed');
fail = false; release = true;
const pending = context.actual.fetchData({pageNo: 1, pageSize: 20});
context.props.tableId = 'table-b';context.state.previewCols = [];context.state.errMsg = '';
release();await pending;
assert.equal(context.state.previewCols.length, 0, 'An old table response cannot replace the current table headers');
assert.equal(context.state.errMsg, '');
assert.ok(!source.includes('/metadata/columns'), 'No separate metadata read per page');
console.log('PASS: Chinese-first/English-second headers, case and qualified names, English fallback, one request for 20/100 rows, server sort, errors and stale table responses');

import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(new URL('../package.json', import.meta.url));
const { reactive, ref } = require('vue');
const { parse, compileScript } = require('vue/compiler-sfc');
const root = new URL('../../', import.meta.url);
const componentPath = 'lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource).vue';
const source = fs.readFileSync(new URL(componentPath, root), 'utf8');
const magic = fs.readFileSync(new URL('magic/api/02.数据盘点/02.数据库表/06.本部门数据源清单.ms', root), 'utf8');
const { descriptor, errors } = parse(source, { filename: componentPath });
assert.deepEqual(errors, []);
const { scriptSetupAst } = compileScript(descriptor, { id: 'datasource-loading-test' });
const bindings = [
  'SOURCE_PAGE_SIZES', 'sourceCursors', 'state', 'normalizeNumber', 'loadGeneration',
  'sourcePageGeneration', 'loadSourcePage', 'loadOrganizationTree',
  'loadData', 'getTreeNodeCount', 'filterZeroNodes', 'appendApplicationNodes',
  'rebuildTree', 'collectTreeNodeOrgIds', 'findSelectedTreeNode', 'sourceScopePayload',
  'sourcePageFilters', 'sourcePageFilterKey', 'sourcePagePayload', 'isExpanded', 'canExpand',
  'toggleExpand', 'tablePageSize', 'tablePageKey', 'tableLoadState', 'tableTotal',
  'tableTotalPages', 'tablePage', 'pagedTables', 'loadTablePage', 'sortTables', 'changeTablePage',
];
const declarations = bindings.map((name) => {
  const node = scriptSetupAst.find((statement) =>
    statement.type === 'VariableDeclaration' && statement.declarations.some((entry) => entry.id.name === name));
  assert.ok(node, `Missing registration binding: ${name}`);
  return descriptor.scriptSetup.content.slice(node.start, node.end);
}).join('\n');

function harness(reply) {
  const calls = [];
  const post = (url, body) => {
    calls.push({ url, body });
    return reply(body, url);
  };
  const setup = new Function(
    'reactive', 'treeData', 'orgTreeSource', 'treeStatistics', 'countMap', '$common', '$user',
    'normalizeItem', 'applyDefaultTreeState', 'typeStatisticsPayload', 'applyTypeStatistics',
    'scheduleTypeStatistics', 'console',
    declarations + '\nreturn { state, loadData, loadSourcePage, toggleExpand, tableLoadState, tablePage, pagedTables, loadTablePage, sortTables, changeTablePage };',
  );
  return {
    calls,
    ...setup(
      reactive, ref([{ value: '', type: 'org', children: [] }]), ref([]), ref({}), ref({}),
      { post }, { orgRootId: 'ROOT' }, (row) => row, () => {}, () => ({}), () => {}, () => {},
      { error() {} },
    ),
  };
}

const settled = () => new Promise(setImmediate);
function deferred() {
  let resolve;
  const promise = new Promise((done) => { resolve = done; });
  return { promise, resolve };
}

const row = { tid: 'db1', tableNum: 25, registeredTableNum: 25 };
const table = { tid: 'table1' };
const cardPage = ({ pageNum, pageSize }) => ({ pageNum, pageSize, total: 25, list: [row], dataScope: 'ALL' });
const tablePage = ({ pageNum = 1, rows = [table] } = {}) => ({ pageNum, total: 25, list: rows });
const currentReply = (body) => {
  if (body.mode === 'cards') return cardPage(body);
  if (body.mode === 'facets') return {};
  return [];
};

test('cards settle without waiting for the organization tree and never request overview metrics', async () => {
  const tree = deferred();
  const h = harness((body, url) => url.endsWith('/getOrgTree') ? tree.promise : currentReply(body));
  const loading = h.loadData();
  await settled();

  assert.equal(h.state.loading, false);
  assert.equal(h.state.treeLoading, true);
  assert.deepEqual(h.state.list, [row]);
  assert.deepEqual(h.calls.map(({ url, body }) => [url, body.mode]), [
    ['/dst/database/departmentDataSources', 'cards'],
    ['/sym/org/getOrgTree', undefined],
    ['/dst/database/treeStatistics', 'facets'],
  ]);

  tree.resolve([]);
  await loading;
});

test('tree failure does not clear successful cards', async () => {
  const h = harness((body, url) => {
    if (url.endsWith('/getOrgTree')) throw new Error('tree down');
    return currentReply(body);
  });
  await h.loadData();

  assert.deepEqual(h.state.list, [row]);
  assert.ok(h.state.treeError);
  assert.equal(h.state.loading, false);
});

test('failed card refresh displays its own error and retries the same page', async () => {
  let fail = false;
  const h = harness((body) => {
    if (fail && body.mode === 'cards') throw new Error('cards down');
    return currentReply(body);
  });
  await h.loadData();
  h.state.sourcePage = 2;
  fail = true;
  await h.loadData();

  assert.deepEqual(h.state.list, []);
  assert.ok(h.state.listError);
  assert.equal(h.state.sourcePage, 2);

  fail = false;
  await h.loadSourcePage();
  assert.equal(h.calls.at(-1).body.pageNum, 2);
  assert.deepEqual(h.state.list, [row]);
  assert.equal(h.state.listError, '');
});

test('expanding a card loads one scoped table page and reuses the pending request', async () => {
  const pending = deferred();
  const h = harness((body) => body.pageNum === 1 ? pending.promise : tablePage({ pageNum: body.pageNum }));
  h.toggleExpand(row);
  h.toggleExpand(row);
  h.toggleExpand(row);
  h.changeTablePage(row, 1);

  assert.equal(h.calls.length, 1);
  assert.deepEqual(h.calls[0], {
    url: '/dst/database/departmentDataSources',
    body: { mode: 'tables', datasourceId: 'db1', pageNum: 1, pageSize: 10 },
  });

  pending.resolve(tablePage());
  await settled();
  assert.equal(h.tablePage(row), 1);
  assert.deepEqual(h.pagedTables(row), [table]);

  h.changeTablePage(row, 1);
  await settled();
  assert.equal(h.calls.length, 2);
  assert.deepEqual(h.calls[1].body, { mode: 'tables', datasourceId: 'db1', pageNum: 2, pageSize: 10 });
  assert.equal(h.tablePage(row), 2);

  h.sortTables(row, { prop: 'tableName', order: 'ascending' });
  await settled();
  assert.deepEqual(h.calls[2].body, {
    mode: 'tables', datasourceId: 'db1', pageNum: 1, pageSize: 10, sortField: 'tableName', sortDir: 'asc',
  });
});

test('table failure is local and permits retry', async () => {
  let fail = true;
  const h = harness(() => {
    if (fail) throw new Error('tables down');
    return tablePage();
  });
  h.state.list = [row];
  await h.loadTablePage(row, 1);
  assert.ok(h.tableLoadState(row).error);
  assert.deepEqual(h.state.list, [row]);

  fail = false;
  await h.loadTablePage(row, 1);
  assert.equal(h.tableLoadState(row).loaded, true);
  assert.equal(h.tableLoadState(row).error, '');
  assert.deepEqual(h.pagedTables(row), [table]);
});

for (const fail of [false, true]) {
  test(`a ${fail ? 'failed' : 'successful'} card refresh invalidates in-flight table responses`, async () => {
    const pending = deferred();
    const h = harness((body) => {
      if (body.mode === 'tables') return pending.promise;
      if (fail && body.mode === 'cards') throw new Error('cards down');
      return currentReply(body);
    });
    const loading = h.loadTablePage(row, 1);
    await h.loadData();
    assert.deepEqual(h.tableLoadState(row), {});
    assert.equal(Boolean(h.state.listError), fail);

    pending.resolve(tablePage({ rows: [{ tid: 'stale' }] }));
    await loading;
    assert.deepEqual(h.state.tableLoads, {});
    assert.deepEqual(h.pagedTables(row), []);
  });
}

test('card queries use server pages and expanded tables enforce trusted tenant and menu boundaries', () => {
  const cards = magic.match(/if \(\['cards', 'cardMetrics', 'choices'\]\.contains\(requestMode\)\) \{([\s\S]*?)\r?\nif \(requestMode == 'reportTables'\)/)?.[1];
  const visibleSources = magic.match(/var visibleSources = \(ids, readPool\) => \{([\s\S]*?)\r?\n\}/)?.[1];
  const tables = magic.match(/if \(requestMode == 'tables'\) \{([\s\S]*?)\r?\nvar summaryOnly/)?.[1];
  assert.match(cards, /rowSource\(\)\.pageClamped\([^\n]+pageNum, pageSize\)/);
  assert.match(cards, /return response/);
  assert.match(magic, /tenantRuntime\.id\(\)/);
  assert.match(magic, /scopeDecision = dataScope\.resource\(scopeResource\)/);
  assert.doesNotMatch(magic, /(?:body|param|principal)\??\.tenantId/);
  assert.match(visibleSources, /\.eq\('tenant_id', tenantId\)\.eq\('is_del', 0\)\.in\('tid', ids\)/);
  assert.match(visibleSources, /query\.eq\('created_by', scopeUserId\)/);
  assert.match(visibleSources, /query\.in\('org_id', scopeOrgIds\)/);
  assert.match(tables, /visibleSources\(\[datasourceId\], false\)[\s\S]*?exit 404[\s\S]*?tableRead\.from\('db_table_t', 't', tenantId\)/);
  assert.match(tables, /if \(pageSize > 100\) \{\s*pageSize = 100/);
  assert.match(tables, /tableRead\.equal\(numberColumn\('t', 'asset_status'\), 2\)/);
  assert.match(tables, /tables\.pageClamped\([\s\S]*?, pageNum, pageSize\)/);
  assert.match(tables, /\.search\(\{\s*size: pageSize,[\s\S]*?ids: \{ values: pageIds \}/);
});

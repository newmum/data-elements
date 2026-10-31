import assert from "node:assert/strict";
import fs from "node:fs/promises";
import test from "node:test";
import vm from "node:vm";

const source = await fs.readFile(new URL("../../lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/04.登记内容审查(register-confirm).vue", import.meta.url), "utf8");
const start = source.indexOf("async function loadReviewPage()");
const script = source.slice(start, source.indexOf("\nwatch(", start));
function fixture(count, pageSize = 20) {
  const rows = Array.from({ length: count }, (_, index) => ({ tableName: `validation_${index}`, tid: String(index) }));
  const calls = [];
  const context = {
    sourceTid: { value: "source" }, tables: { value: rows }, filteredReviewTables: { value: rows },
    tableListLoading: { value: false }, reviewTablePage: { value: 1 }, reviewTablePageSize: pageSize,
    reviewSort: { value: {} }, reviewOrderLoading: { value: false }, reviewOrderError: { value: "" },
    visibleTables: { value: [] }, reviewOrderSequence: 0, requestWithTimeout: action => action(),
    $common: { post: async (url, body) => {
      calls.push({ url, body });
      const selected = context.filteredReviewTables.value;
      return { total: selected.length, keys: selected.slice((body.pageNo - 1) * pageSize, body.pageNo * pageSize).map(row => row.tableName) };
    } },
  };
  vm.createContext(context);
  vm.runInContext(`${script};this.load = loadReviewPage;`, context);
  return { context, calls };
}

for (const pageSize of [20, 100]) {
  test(`30,000 tables: page size ${pageSize} makes one request and sends no complete name list`, async () => {
    const { context, calls } = fixture(30_000, pageSize);
    await context.load();
    assert.equal(calls.length, 1);
    assert.equal(calls[0].body.allTables, true);
    assert.equal("names" in calls[0].body, false);
    assert.equal(context.visibleTables.value.length, pageSize);
    context.reviewTablePage.value = 30_000 / pageSize;
    await context.load();
    assert.equal(calls.length, 2);
    assert.equal(context.visibleTables.value.at(-1).tableName, "validation_29999");
  });
}

test("15,000 filtered tables keep the exact scope and use one request", async () => {
  const { context, calls } = fixture(30_000);
  context.filteredReviewTables.value = context.tables.value.filter((_, index) => index % 2 === 0);
  await context.load();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].body.names.length, 15_000);
  assert.equal("allTables" in calls[0].body, false);
  assert.equal(context.visibleTables.value.length, 20);
});

test("empty results clear an earlier error; loading snapshots do not request a stale source scope", async () => {
  const { context, calls } = fixture(20);
  context.tableListLoading.value = true;
  await context.load();
  assert.equal(calls.length, 0);
  context.tableListLoading.value = false;
  context.reviewOrderError.value = "earlier failure";
  context.filteredReviewTables.value = [];
  await context.load();
  assert.equal(context.reviewOrderError.value, "");
  assert.equal(context.visibleTables.value.length, 0);
});

test("network, foreign and duplicate responses stay actionable; obsolete responses cannot replace a newer page", async () => {
  for (const keys of [["foreign"], ["validation_0", "validation_0"]]) {
    const { context } = fixture(20);
    context.$common.post = async () => ({ total: 20, keys });
    await context.load();
    assert.match(context.reviewOrderError.value, /不完整/);
    assert.equal(context.reviewOrderLoading.value, false);
  }
  const { context } = fixture(40);
  let resolveOld;
  context.$common.post = () => new Promise(resolve => { resolveOld = resolve; });
  const old = context.load();
  context.reviewTablePage.value = 2;
  context.$common.post = async () => ({ total: 40, keys: context.tables.value.slice(20).map(row => row.tableName) });
  await context.load();
  resolveOld({ total: 40, keys: ["validation_0"] });
  await old;
  assert.equal(context.visibleTables.value[0].tableName, "validation_20");
  context.$common.post = async () => { throw new Error("连接中断，请重试"); };
  await context.load();
  assert.equal(context.reviewOrderError.value, "连接中断，请重试");
});

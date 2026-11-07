import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { test } from "node:test";
import { babelParse, parse } from "vue/compiler-sfc";

const sourcePath = new URL(
  "../../lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/03.登记数据表(register-dbTable-catalog).vue",
  import.meta.url
);
const source = fs.readFileSync(sourcePath, "utf8");
const { descriptor, errors } = parse(source);
assert.deepEqual(errors, []);
const script = descriptor.scriptSetup.content;
const nodes = babelParse(script, { sourceType: "module" }).program.body;

function loadFunction(name, scope) {
  const declaration = nodes.find(
    (node) => node.type === "FunctionDeclaration" && node.id.name === name
  );
  assert.ok(declaration, name);
  return vm.runInNewContext(script.slice(declaration.start, declaration.end) + "\n" + name, scope);
}
const tick = () => new Promise((resolve) => setImmediate(resolve));
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
};

test("saved MySQL columns render without waiting for governance or preview", async () => {
  const governance = deferred();
  const reads = [];
  const scope = {
    fieldMap: {},
    fieldLoadingMap: {},
    fieldErrorMap: {},
    store: { data: {} },
    detailMode: { value: false },
    loadTableGovernance: () => governance.promise,
    waitForFieldsLoaded: () => assert.fail("unexpected competing field read"),
    unwrapList: (value) => value,
    normalizeFieldRows: (value) => value,
    console: { error: () => assert.fail("unexpected field error") },
    $common: {
      getSilently: async (path, { tid, snapshotOnly }) => {
        reads.push({ path, tid, snapshotOnly });
        return [{ columnName: "ID" }];
      },
      postSilently: () => assert.fail("existing columns must not trigger physical collection"),
    },
  };
  const loadFields = loadFunction("loadFields", scope);
  await loadFields({ tid: "table-a", tableName: "A" });
  assert.deepEqual(reads, [
    { path: "/dst/database/metadata/columns", tid: "table-a", snapshotOnly: true },
  ]);
  assert.deepEqual(scope.fieldMap.A, [{ columnName: "ID" }]);
  assert.equal(scope.fieldLoadingMap.A, false);
  governance.resolve();
});

function previewScope() {
  const scope = {
    activeTable: { value: { tid: "table-a", tableName: "A" } },
    activeTableName: { value: "A" },
    activeFields: { value: [{ columnName: "ID" }] },
    dbId: { value: "source-a" },
    dictionaryPreviewRequestVersion: { value: 0 },
    connectionErrorMap: {},
    fieldErrorMap: {},
    dictionaryPreview: {
      loading: false,
      loaded: false,
      tableName: "",
      columns: [],
      rows: [],
      total: 0,
    },
    isStructuredSource: () => false,
    normalizePreviewResult: (value) => value,
    loadDictionaryGroupedValues: async () => {},
    loadForceStandardElements: async () => {},
    ensureDictionaryCategoryRows: () => [],
    tryAutoForceStandardMatch: () => {},
    console: { warn() {} },
  };
  loadFunction("connectionFailureMessage", scope);
  loadFunction("reportConnectionFailure", scope);
  return scope;
}
const sample = (id) => ({ columns: ["ID"], rows: [{ ID: id }], total: 1 });

test("preview rows leave the skeleton before grouped/enrichment queries finish", async () => {
  const grouped = deferred(),
    scope = previewScope();
  scope.$common = { postSilently: async () => sample("A") };
  scope.loadDictionaryGroupedValues = () => grouped.promise;
  const pending = loadFunction("loadDictionaryPreview", scope)();
  await tick();
  assert.equal(scope.dictionaryPreview.loaded, true);
  assert.equal(scope.dictionaryPreview.loading, false);
  assert.equal(scope.dictionaryPreview.rows[0].ID, "A");
  grouped.resolve();
  await pending;
});

test("a stale preview response cannot overwrite the newly selected table", async () => {
  const old = deferred(),
    current = deferred(),
    scope = previewScope();
  scope.$common = {
    postSilently: (_path, body) => (body.tableId === "table-a" ? old.promise : current.promise),
  };
  const loadPreview = loadFunction("loadDictionaryPreview", scope);
  const first = loadPreview();
  scope.activeTable.value = { tid: "table-b", tableName: "B" };
  scope.activeTableName.value = "B";
  scope.dictionaryPreviewRequestVersion.value += 1;
  scope.dictionaryPreview.loading = false;
  const second = loadPreview();
  current.resolve(sample("B"));
  await second;
  old.resolve(sample("A"));
  await first;
  assert.equal(scope.dictionaryPreview.rows[0].ID, "B");
  assert.equal(scope.dictionaryPreview.tableName, "B");
});

test("preview errors remain local and do not erase configured fields", async () => {
  const scope = previewScope();
  scope.$common = {
    postSilently: async () => {
      throw new Error("ORA-00942: 表或视图不存在");
    },
  };
  await loadFunction("loadDictionaryPreview", scope)();
  assert.equal(scope.dictionaryPreview.loading, false);
  assert.equal(scope.dictionaryPreview.loaded, true);
  assert.equal(scope.dictionaryPreview.initialPage, null);
  assert.match(scope.dictionaryPreview.message, /ORA-00942/);
  assert.deepEqual(scope.activeFields.value, [{ columnName: "ID" }]);
});

test("an auxiliary grouping failure preserves successfully loaded preview rows", async () => {
  const scope = previewScope();
  scope.$common = { postSilently: async () => sample("A") };
  scope.loadDictionaryGroupedValues = async () => {
    throw new Error("grouping unavailable");
  };
  await loadFunction("loadDictionaryPreview", scope)();
  assert.equal(scope.dictionaryPreview.rows[0].ID, "A");
  assert.equal(scope.dictionaryPreview.loading, false);
});

test("dictionary field loading no longer replaces the whole configuration panel", () => {
  assert.doesNotMatch(source, /v-else-if="activeFieldLoading && isActiveDictionaryTable"/);
  assert.match(source, /v-if="dictionaryPreview.loading && !dictionaryPreview.loaded"/);
  assert.match(source, /governanceLoadedMap\[table.tid\] = true/);
});

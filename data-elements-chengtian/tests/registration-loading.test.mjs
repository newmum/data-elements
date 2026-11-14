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

const sourceRegistrationScript = parse(fs.readFileSync(new URL(
  "../../lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue",
  import.meta.url
), "utf8")).descriptor.scriptSetup.content.replace(/\r\n/g, "\n");

function applicationOptionsFixture(read) {
  const calls = [], errors = [];
  const rule = { field: "appId", options: [] };
  const applicationSettings = { brandTenantId: "tenant-a" };
  const applicationUser = { userInfo: { userId: "user-a", orgId: "org-a" } };
  const scope = {
    applicationSettings,
    applicationUser,
    applicationOptions: { value: [] },
    applicationOptionCatalog: { value: [] },
    formRules: { value: [rule] },
    formRef: { value: { updateFieldOptions: (_key, rows) => { rule.options = rows; } } },
    localStorage: { getItem: () => null },
    sessionStorage: { getItem: () => "session-a" },
    watch: (_get, invalidate) => { scope.invalidate = invalidate; },
    console: { warn: (_message, error) => errors.push(error) },
    $common: {
      post: (url, body) => {
        calls.push([url, Object.keys(body)]);
        return read();
      },
    },
  };
  const state = sourceRegistrationScript.slice(
    sourceRegistrationScript.indexOf("let applicationOptionSession;"),
    sourceRegistrationScript.indexOf("const currentDid = ref")
  );
  const implementation = sourceRegistrationScript.slice(
    sourceRegistrationScript.indexOf("const applicationOptionValue ="),
    sourceRegistrationScript.indexOf("const upsertApplicationOption =")
  );
  const lifecycle = sourceRegistrationScript.match(
    /onBeforeUnmount\(\(\) => \{\n  \+\+datasourceListSequence;\n  clearInterval\(connectionTestTimer\);([\s\S]*?)\n\}\);/
  );
  assert.ok(lifecycle, "登记组件解除挂载时清理候选请求");
  const actions = vm.runInNewContext(
    `${state}\n${implementation}\n({ loadApplicationOptions, searchApplicationOptions, findApplicationOption,
      dispose: () => {${lifecycle[1]}}
    })`, scope
  );
  return {
    ...actions, calls, errors, scope, applicationSettings,
    options: () => Array.from(rule.options, (row) => row.value),
  };
}

test("20/100 个所属系统候选一次读取，在浏览器按最新名称或 ID 查找", async () => {
  for (const count of [20, 100]) {
    const response = deferred();
    const view = applicationOptionsFixture(() => response.promise);
    const pending = view.loadApplicationOptions();
    assert.equal(view.loadApplicationOptions(), pending, "打开下拉与预加载共用在途请求");
    view.searchApplicationOptions("没结果");
    view.searchApplicationOptions(" APP-001 ");
    await tick();
    assert.deepEqual(view.calls, [["/dst/application/register-options", []]]);
    response.resolve(Array.from({ length: count }, (_, index) => ({
      value: `app-${String(index).padStart(3, "0")}`, label: index === 2 ? "有效系统（历史）" : `验证系统 ${index}`,
    })));
    await pending;
    assert.deepEqual(view.options(), ["app-001"]);
    assert.equal(view.findApplicationOption("app-002").label, "有效系统", "查找已选值不受当前关键词影响");
    view.searchApplicationOptions("有效系统");
    assert.deepEqual(view.options(), ["app-002"]);
    view.searchApplicationOptions("无匹配");
    assert.deepEqual(view.options(), []);
    view.searchApplicationOptions("");
    assert.equal(view.options().length, count);
    await view.loadApplicationOptions();
    assert.equal(view.calls.length, 1);
    assert.deepEqual(view.errors, []);
  }
});

test("所属系统请求失败后可重试，空数组成功后仍复用", async () => {
  let attempts = 0;
  const view = applicationOptionsFixture(() => {
    if (++attempts === 1) return Promise.reject(new Error("查询失败"));
    if (attempts === 2) return Promise.resolve(null);
    return Promise.resolve([]);
  });
  await view.loadApplicationOptions();
  view.searchApplicationOptions("验证");
  assert.equal(view.calls.length, 1, "输入只做本地匹配，打开下拉时允许重试");
  await view.loadApplicationOptions();
  await view.loadApplicationOptions();
  await view.loadApplicationOptions();
  assert.equal(view.calls.length, 3);
  assert.equal(view.errors.length, 2);
  assert.deepEqual(view.options(), []);
});

test("行业会话变化或表单卸载后不接受旧请求的所属系统", async () => {
  const old = deferred(), current = deferred(), later = deferred();
  let attempts = 0;
  const view = applicationOptionsFixture(() => [old, current, later][attempts++].promise);
  const oldRequest = view.loadApplicationOptions();
  await tick();
  view.applicationSettings.brandTenantId = "tenant-b";
  view.scope.invalidate();
  const currentRequest = view.loadApplicationOptions();
  await tick();
  old.resolve([{ label: "旧租户", value: "old-app" }]);
  await oldRequest;
  assert.equal(view.loadApplicationOptions(), currentRequest);
  assert.deepEqual(view.options(), []);
  current.resolve([{ label: "当前租户", value: "current-app" }]);
  await currentRequest;
  assert.deepEqual(view.options(), ["current-app"]);
  view.applicationSettings.brandTenantId = "tenant-c";
  view.scope.invalidate();
  assert.deepEqual(view.options(), []);
  const closing = view.loadApplicationOptions();
  await tick();
  view.dispose();
  later.resolve([{ label: "关闭表单", value: "closed-app" }]);
  await closing;
  assert.equal(view.loadApplicationOptions(), undefined);
  assert.deepEqual(view.options(), []);
  assert.equal(view.calls.length, 3);
});

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

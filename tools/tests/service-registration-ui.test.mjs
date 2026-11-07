import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import test from "node:test";
import vm from "node:vm";
const root = fileURLToPath(new URL("../../", import.meta.url));
const require = createRequire(path.join(root, "data-elements-chengtian/package.json"));
const vue = require("vue"),
  ts = require("typescript");
const actions = {};
const actionSource = fs.readFileSync(
  path.join(root, "data-elements-chengtian/src/components/business/registration-actions.ts"),
  "utf8"
);
new Function("exports", ts.transpileModule(actionSource, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText)(actions);
const contentPath =
  "lowcode/06.数据服务/数据服务(data-serve)/服务组件/api注册(api-register-content).vue";
const compiled = JSON.parse(
  execFileSync(process.execPath, ["tools/compile-lowcode.mjs", contentPath], {
    cwd: root,
    encoding: "utf8",
  })
);
const bundle = JSON.parse(fs.readFileSync(path.join(root, compiled.compiled), "utf8"));
const definitionSource = fs.readFileSync(
  path.join(root, "docs/assets/data-elements-chengtian/forms/登记API服务.js"),
  "utf8"
);
const definitionRules = () =>
  vm.runInNewContext(definitionSource, { $dict: { getDictItems: () => [] } });
function contentFixture(post, loadDefinition = async () => ({ rules: definitionRules() }), confirm = async () => {}) {
  let refIndex = 0,
    model = {};
  const feedback = [],
    events = [];
  let api, rulesRef;
  const form = {
    validate: async () => true,
    getFormData: async () => ({ ...model }),
    setValue: (value) => {
      model = { ...value };
    },
  };
  const runtime = {
    ...vue,
    ref: (value) => {
      const result = value === undefined && refIndex++ === 0 ? vue.ref(form) : vue.ref(value);
      if (Array.isArray(value)) rulesRef = result;
      return result;
    },
  };
  const messages = {
    success: (value) => feedback.push(value),
    error: (value) => feedback.push(value),
    warning: (value) => feedback.push(value),
  };
  const libraries = {
    vue: runtime,
    "element-plus": { ElMessage: messages, ElMessageBox: { confirm } },
    "@/store": { useDictStore: () => ({ getDictItems: () => [] }) },
    "lodash-es": { cloneDeep: (value) => structuredClone(value) },
  };
  const component = new Function("___magic__import__", "$common", "$form", bundle.compileJs)(
    (name, key) => libraries[name]?.[key],
    { post },
    { loadDefinition }
  );
  component.setup(
    { context: "modal", data: {} },
    {
      expose: (value) => {
        api = value;
      },
      emit: (...value) => events.push(value),
    }
  );
  return {
    get rules() { return rulesRef.value; },
    get api() {
      return api;
    },
    get model() {
      return model;
    },
    setFields: (value) => {
      model = { ...model, ...value };
    },
    feedback,
    events,
  };
}
test("registration uses current department, preserves examples and retains returned ID for later saves", async () => {
  const requests = [];
  const f = contentFixture(async (url, body) => {
    requests.push({ url, body });
    return url === "/sym/user/me"
      ? { id: "user", orgId: "org", orgName: "测试部门" }
      : { id: "saved-service" };
  });
  await f.api.setFormData({
    extendInfo: {
      requestExample: '{"page":1}',
      responseExample: '{"data":[]}',
    },
  });
  f.setFields({
    name: "登记服务",
    address: "https://example.test/api",
    publishAddress: "/api",
    dataDomain: "domain",
    dataCategory: "category",
    dataLevel: "level",
    returnType: "JSON",
    shareType: "share",
    dataUpdateFreq: "daily",
  });
  assert.equal(await f.api.handleSave(), true);
  assert.equal(await f.api.handleSave(), true);
  const saves = requests.filter((request) => request.url.endsWith("/save"));
  assert.equal(saves.length, 2);
  assert.equal(saves[0].body.extendInfo.orgId, "org");
  assert.equal(saves[0].body.extendInfo.requestExample, '{"page":1}');
  assert.equal(saves[1].body.id, "saved-service");
  assert.equal(requests.filter((request) => request.url === "/sym/user/me").length, 1);
  assert.equal((await f.api.getFormData()).id, "saved-service");
});
test("simplified registration derives a new publish path and retains hidden legacy values", async () => {
  const f = contentFixture(async () => ({ orgId: "org", orgName: "部门" }));
  await f.api.setFormData({});
  f.setFields({ address: "https://example.test/service" });
  assert.equal((await f.api.getPayload()).publishConfig.publishAddress, "https://example.test/service");
  await f.api.setFormData({ id: "existing", publishConfig: { publishAddress: "/published" },
    extendInfo: { dataDomain: "domain", dataCategory: "category", dataLevel: "level",
      shareType: "shared", dataUpdateFreq: "daily", apiDescription: "legacy" } });
  f.setFields({ address: "https://example.test/updated" });
  const payload = await f.api.getPayload();
  assert.equal(payload.publishConfig.publishAddress, "/published");
  for (const [key, value] of Object.entries({ dataDomain: "domain", dataCategory: "category",
    dataLevel: "level", shareType: "shared", dataUpdateFreq: "daily", apiDescription: "legacy" }))
    assert.equal(payload.extendInfo[key], value);
});

test("validation and upstream failure never report a completed registration", async () => {
  const f = contentFixture(async (url) =>
    url === "/sym/user/me"
      ? { id: "user", orgId: "org", orgName: "部门" }
      : Promise.reject(new Error("保存不可用"))
  );
  await f.api.setFormData({});
  f.setFields({ name: "测试", address: "invalid" });
  assert.equal(await f.api.handleSave(), false);
  assert.equal(f.events.filter(([name]) => name === "saved").length, 0);
  f.setFields({ address: "https://example.test/query" });
  assert.equal(await f.api.handleSave(), false);
  assert.equal(f.events.filter(([name]) => name === "saved").length, 0);
});
test("a failed department initialization can be retried without retaining a rejected request", async () => {
  let attempts = 0;
  const f = contentFixture(async (url) => {
    if (url === "/sym/user/me" && ++attempts === 1) throw new Error("部门信息暂时不可用");
    return { id: "user", orgId: "org", orgName: "部门" };
  });
  await f.api.setFormData({});
  await f.api.setFormData({});
  assert.equal((await f.api.getFormData()).orgId, "org");
  assert.equal(attempts, 2);
});
const source = fs
  .readFileSync(
    path.join(
      root,
      "lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue"
    ),
    "utf8"
  )
  .match(/<script setup>([\s\S]*?)<\/script>/)[1];
const parsed = ts.createSourceFile(
  "proxy.js",
  source,
  ts.ScriptTarget.Latest,
  true,
  ts.ScriptKind.JS
);
const declaration = (name) =>
  parsed.statements
    .find(
      (node) =>
        ts.isVariableStatement(node) &&
        node.declarationList.declarations.some((item) => item.name.getText(parsed) === name)
    )
    .getText(parsed);
test("selecting proxy access does not activate database or spreadsheet metadata configuration", () => {
  const selection = parsed.statements
    .find((node) => ts.isFunctionDeclaration(node) && node.name?.text === "selectAccessMode")
    .getText(parsed);
  const accessMode = vue.ref("explore");
  let metadataReads = 0;
  const select = new Function(
    "accessModeOptions",
    "accessMode",
    "activateMetadataImportForMode", "sourceType", "connectionEnabled", "formRef",
    `${selection}\nreturn selectAccessMode;`
  )([{ value: "proxy" }], accessMode, () => metadataReads++, vue.ref("mysql"), vue.ref("1"), vue.ref());
  select("proxy");
  assert.equal(accessMode.value, "proxy");
  assert.equal(metadataReads, 0);
});
function proxyFixture(datasource) {
  const visible = vue.ref(false);
  const refreshed = [];
  const table = vue.ref({ refresh: (resetPage) => refreshed.push(resetPage) });
  const api = new Function(
    "computed", "currentDb", "proxyServiceRegistrationVisible", "proxyServiceTableRef",
    `${declaration("proxyDatasourceId")}\n${declaration("goToServiceRegistration")}\n${declaration("onProxyServiceRegistrationClosed")}\nreturn { proxyDatasourceId, goToServiceRegistration, onProxyServiceRegistrationClosed };`
  )(vue.computed, vue.ref(datasource), visible, table);
  return { api, visible, refreshed };
}
test("proxy datasource restoration never loads physical table metadata",async()=>{
  let reads=0;
  const restore=new Function("accessMode","$common",`${declaration("loadMetadataImportResult")}\nreturn loadMetadataImportResult;`)(vue.ref("proxy"),{get:()=>reads++});
  await restore("source-a");assert.equal(reads,0);
});
function datasourceSidebarFixture(respond) {
  const requests = [];
  const draft = { did: 'new', isDraft: true };
  const selected = { tid: 'source-current', did: 'current', dbName: '正在编辑' };
  const dbs = vue.ref([draft, selected]);
  const currentDid = vue.ref(selected.did);
  const api = new Function('reactive', '$common', 'dbs', 'currentDid', 'createDatasourceDraft', 'datasourceKey', 'toDatasourceItem',
    `${declaration('DATASOURCE_LIST_PAGE_SIZE')}\n${declaration('datasourceList')}\nlet datasourceListSequence = 0;\n`
    + `${declaration('loadAccessibleDatasources')}\n${declaration('searchAccessibleDatasources')}\n`
    + 'return { datasourceList, loadAccessibleDatasources, searchAccessibleDatasources };')(
    vue.reactive, { postSilently: async (url, body) => { requests.push({ url, body }); return respond(body); } },
    dbs, currentDid, () => draft, row => row.tid || row.id || row.did, row => ({ ...row, did: row.tid }));
  return { dbs, currentDid, requests, ...api };
}

test('registration sidebar reads a lightweight server page and preserves the current edit', async () => {
  const f = datasourceSidebarFixture(({ pageNum }) => ({
    total: 1200, pageNum, list: [{ tid: 'source-current', dbName: '服务端旧名称' }, { tid: `source-${pageNum}` }],
  }));
  await f.loadAccessibleDatasources(45);
  assert.deepEqual(f.requests, [{ url: '/dst/database/departmentDataSources',
    body: { mode: 'choices', pageNum: 45, pageSize: 24, keyword: '' } }]);
  assert.equal(f.datasourceList.total, 1200);
  assert.equal(f.datasourceList.page, 45);
  assert.equal(f.currentDid.value, 'current');
  assert.equal(f.dbs.value[1].dbName, '正在编辑');
  assert.equal(f.dbs.value[2].tid, 'source-45');
  await f.loadAccessibleDatasources(46);
  assert.equal(f.dbs.value.length, 3);
  assert.equal(f.dbs.value[2].tid, 'source-46');
});

test('registration sidebar ignores late pages, and retry preserves the submitted search', async () => {
  const pending = [];
  const f = datasourceSidebarFixture(() => new Promise((resolve, reject) => pending.push({ resolve, reject })));
  const older = f.loadAccessibleDatasources(8);
  f.datasourceList.keywordInput = ' Oracle ';
  const search = f.searchAccessibleDatasources();
  pending[1].reject(new Error('offline'));
  await search;
  assert.equal(f.datasourceList.error, true);
  assert.equal(f.dbs.value.length, 2);
  const retry = f.loadAccessibleDatasources(f.datasourceList.requestedPage);
  assert.deepEqual(f.requests[2].body, { mode: 'choices', pageNum: 1, pageSize: 24, keyword: 'Oracle' });
  pending[2].resolve({ list: [{ tid: 'oracle' }], total: 1, pageNum: 1 });
  await retry;
  pending[0].resolve({ list: [{ tid: 'old' }], total: 200, pageNum: 8 });
  await older;
  assert.equal(f.datasourceList.error, false);
  assert.equal(f.datasourceList.loading, false);
  assert.equal(f.datasourceList.total, 1);
  assert.deepEqual(f.dbs.value.map(row => row.did), ['new', 'current', 'oracle']);
});

function webServiceFixture(post = async () => ({})) {
  const messages = [], requests = [], cleared = [];
  const source = ["apiProtocolDrafts", "syncSoapRequestHeaders", "changeApiProtocol", "selectWebServiceOperation", "readWebServiceWsdl", "apiPullTestAuthPayload"].map(declaration).join("\n");
  const api = new Function("$common", "$message", "apiPullReadingWsdlItemId", "clearApiPullResponseExtraction", "obj", "kv", `${source}\nreturn { changeApiProtocol, selectWebServiceOperation, readWebServiceWsdl };`)(
    { post: async (...args) => { requests.push(args); return post(...args); } },
    { warning: value => messages.push(value), success: value => messages.push(value) },
    vue.ref(""), item => cleared.push(item.clientId), values => Object.fromEntries(values.map(value => [value.key, value.value])), value => ({ ...value })
  );
  return { api, messages, requests, cleared };
}

test("WebService protocol changes preserve separate request drafts and enforce POST/XML", () => {
  const f = webServiceFixture();
  const item = { clientId: "validation", apiProtocol: "SOAP", endpointMethod: "GET", requestBodyType: "json", requestTemplateJson: '{"page":1}', responseExtractPath: "data.list" };
  f.api.changeApiProtocol(item);
  assert.equal(item.endpointMethod, "POST");
  assert.equal(item.requestBodyType, "xml");
  assert.equal(JSON.parse(item.requestHeadersJson)["Content-Type"], "text/xml; charset=UTF-8");
  assert.equal(item.responseExtractPath, "");
  item.requestTemplateJson = "<SOAP-request/>";
  item.apiProtocol = "REST";
  f.api.changeApiProtocol(item);
  assert.equal(item.endpointMethod, "GET");
  assert.equal(item.requestTemplateJson, '{"page":1}');
  item.apiProtocol = "SOAP";
  f.api.changeApiProtocol(item);
  assert.equal(item.requestTemplateJson, "<SOAP-request/>");
});

test("WSDL metadata makes one request, supplies an XML operation draft and never marks a SOAP call successful", async () => {
  const operation = { name: "querySummary", namespace: "urn:validation", endpoint: "https://example.test/soap", action: "urn:query", version: "1.2" };
  const f = webServiceFixture(async () => ({ success: true, connected: false, metadataOnly: true, operations: [operation] }));
  const item = { clientId: "validation", apiProtocol: "SOAP", apiWsdlUrl: "https://example.test/service?wsdl", headerInputMode: "json", requestHeadersJson: "{}", requestTemplateJson: "", authType: "NONE" };
  await f.api.readWebServiceWsdl(item);
  assert.equal(f.requests.length, 1);
  assert.equal(f.requests[0][1].apiProbeAction, "wsdl");
  assert.equal(item.requestUrl, operation.endpoint);
  assert.equal(item.apiSoapVersion, "1.2");
  assert.equal(item.apiSoapAction, "urn:query");
  assert.equal(JSON.parse(item.requestHeadersJson)["Content-Type"], 'application/soap+xml; charset=UTF-8; action="urn:query"');
  assert.match(item.requestTemplateJson, /http:\/\/www.w3.org\/2003\/05\/soap-envelope/);
  assert.match(item.requestTemplateJson, /<m:querySummary/);
  assert.notEqual(item.testStatus, "success");
  assert.equal(f.cleared.length, 1);
});

test("a failed SOAP call retains the backend Fault and clears the prior connection success", async () => {
  const state = { status: "success" }, feedback = [], loading = vue.ref("");
  const run = new Function("normalizeApiPullRequestAddress", "req", "normalizeApiResponseExtractPath", "apiPullTestingItemId", "$common", "apiPullTestAuthPayload", "connectionTestState", "$message", `${declaration("testApiPullItem")}\nreturn testApiPullItem;`)(
    item => item, () => ({ headers: {}, body: "<SOAP-request/>" }), value => value || "", loading,
    { post: async () => ({ connected: false, success: false, error: "SOAP Fault: missing query condition" }) },
    () => ({ apiAuthType: "none" }), state, { error: value => feedback.push(value) }
  );
  const item = { clientId: "validation", apiProtocol: "SOAP", endpointMethod: "POST", requestUrl: "https://example.test/soap", apiSoapVersion: "1.1" };
  await run(item, 0);
  assert.equal(item.testStatus, "failed");
  assert.equal(state.status, "failed");
  assert.equal(item.testMessage, "SOAP Fault: missing query condition");
  assert.deepEqual(feedback, ["SOAP Fault: missing query condition"]);
  assert.equal(loading.value, "");
});
test("a saved proxy datasource opens its registration dialog and refreshes the shared list only after save", async () => {
  const f = proxyFixture({ tid: "source-a" });
  assert.equal(f.api.proxyDatasourceId.value, "source-a");
  assert.deepEqual(await f.api.goToServiceRegistration(), { navigationHandled: true });
  assert.equal(f.visible.value, true);
  f.api.onProxyServiceRegistrationClosed("cancel");
  assert.deepEqual(f.refreshed, []);
  f.api.onProxyServiceRegistrationClosed("success");
  assert.deepEqual(f.refreshed, [true]);
});
test("drafts and unavailable datasource details cannot open service registration", async () => {
  for (const datasource of [{ tid: "draft-id", isDraft: true }, { tid: "missing-id", detailMissing: true }]) {
    const f = proxyFixture(datasource);
    assert.equal(f.api.proxyDatasourceId.value, "");
    await assert.rejects(f.api.goToServiceRegistration(), /先保存/);
    assert.equal(f.visible.value, false);
  }
});

function footerFixture(next) {
  const events = [],
    loading = [],
    warnings = [];
  const loadStatus = new Proxy({}, {
    set(state, action, value) {
      loading.push([action, value]);
      return Reflect.set(state, action, value);
    },
  });
  const step = { next };
  const runner = actions.createRegistrationActionRunner({
    store: { state: { currentStep: 0, loadStatus, steps: [] }, data: {}, registerClass: "db", setCurrentStep() {} },
    getStep: () => step,
    nextTick: async () => {},
    delay: async () => {},
    emit: (result) => events.push(["finishTriggerFn", result]),
    warning: (message) => warnings.push(message),
    success() {},
    reportError() {},
    showDatasourceSuccess: true,
  });
  return { next: () => runner.handleAction("next"), events, loading, warnings };
}

test("original next button advances ordinary steps and suppresses advancement after a service redirect", async () => {
  const ordinary = footerFixture(async () => undefined);
  await ordinary.next();
  assert.deepEqual(ordinary.events, [["finishTriggerFn", "next"]]);
  const proxy = footerFixture(async () => ({ navigationHandled: true }));
  await proxy.next();
  assert.deepEqual(proxy.events, [["finishTriggerFn", null]]);
  assert.deepEqual(proxy.loading, [
    ["next", true],
    ["next", false],
  ]);
});

test("original next button retains the step and releases loading when navigation fails", async () => {
  const f = footerFixture(async () => {
    throw new Error("服务登记暂时无法打开，请重试");
  });
  await f.next();
  assert.deepEqual(f.events, [["finishTriggerFn", null]]);
  assert.deepEqual(f.warnings, ["服务登记暂时无法打开，请重试"]);
  assert.deepEqual(f.loading, [
    ["next", true],
    ["next", false],
  ]);
});

test("registration route stays intact until the dialog closes, preserving a page keyed by its full URL", async () => {
  const script = fs
    .readFileSync(path.join(root, "lowcode/06.数据服务/数据服务(data-serve).vue"), "utf8")
    .match(/<script setup>([\s\S]*?)<\/script>/)[1];
  const ast = ts.createSourceFile(
    "data-serve.js",
    script,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.JS
  );
  const close = ast.statements
    .find(
      (node) =>
        ts.isVariableStatement(node) &&
        node.declarationList.declarations.some((item) => item.name.getText(ast) === "onModalClose")
    )
    .getText(ast);
  const watchRoute = ast.statements
    .find(
      (node) =>
        ts.isExpressionStatement(node) &&
        ts.isCallExpression(node.expression) &&
        node.expression.expression.getText(ast) === "watch"
    )
    .getText(ast);
  const routes = [],
    route = {
      path: "/deal/data-server/list",
      query: { register: "1", keyword: "保留上下文" },
    };
  let opened = 0;
  const closeDialog = new Function(
    "route",
    "router",
    "openRegister",
    "watch",
    "loadGroups",
    "tableRef",
    `${close}\n${watchRoute}\nreturn onModalClose;`
  )(
    route,
    { replace: async (value) => routes.push(value) },
    () => opened++,
    (read, callback) => callback(read()),
    async () => {},
    { value: { refresh: async () => {} } }
  );
  assert.equal(opened, 1);
  assert.equal(routes.length, 0);
  await closeDialog("cancel");
  assert.deepEqual(routes, [{ path: route.path, query: { keyword: "保留上下文" } }]);
});

function workspaceFixture(post = async () => ({ list: [], total: 0 }), datasourceId = "") {
  const script = fs
    .readFileSync(
      path.join(
        root,
        "lowcode/06.数据服务/数据服务(data-serve)/服务组件/服务注册弹窗(api-register-modal).vue"
      ),
      "utf8"
    )
    .match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
  const ast = ts.createSourceFile("workspace.ts", script, ts.ScriptTarget.Latest, true);
  const body = ast.statements
    .filter((node) => !ts.isImportDeclaration(node))
    .map((node) => node.getText(ast))
    .join("\n");
  const js = ts.transpileModule(body, {
    compilerOptions: { target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const requests = [],
    events = [],
    confirmations = [],
    messages = [];
  const f = { model: {}, save: async () => false, confirm: async () => true };
  const api = new Function(
    "ref",
    "computed",
    "watch",
    "onBeforeUnmount",
    "defineProps",
    "withDefaults",
    "defineEmits",
    "$common",
    "ElMessage",
    "ElMessageBox",
    `${js}\nreturn {startWorkspace,loadServices,selectService,selectEntry,addDraft,saveCurrent,onSaved,captureCurrent,onEditorReady,requestClose,removeDraft,canClose,entries,activeKey,editorData,editorReady,contentRef,services,total,listError,listLoading,keyword,displayedServiceCount};`
  )(
    vue.ref,
    vue.computed,
    () => {},
    () => {},
    () => ({ modelValue: true, apiDetail: { groupId: "group" }, datasourceId, datasourceName: datasourceId ? "验证数据源" : "" }),
    (value) => value,
    () =>
      (...args) =>
        events.push(args),
    {
      post: async (url, payload) => {
        requests.push({ url, payload });
        return post(url, payload);
      },
    },
    {
      error: (msg) => messages.push(msg),
      warning: (msg) => messages.push(msg),
    },
    {
      confirm: async (...args) => {
        confirmations.push(args);
        if (!(await f.confirm())) throw new Error("cancel");
      },
    }
  );
  api.contentRef.value = {
    getPayload: async () => structuredClone(f.model),
    handleSave: () => f.save(),
  };
  return {
    ...f,
    api,
    requests,
    events,
    confirmations,
    messages,
    setModel(value) {
      f.model = structuredClone(value);
    },
    setSave(fn) {
      f.save = fn;
    },
    setConfirm(fn) {
      f.confirm = fn;
    },
    async ready() {
      f.model = structuredClone(vue.toRaw(api.editorData.value));
      await api.onEditorReady(true);
    },
  };
}

for (const count of [20, 100])
  test(`workspace ${count} rows use one bounded department request without detail fan-out`, async () => {
    const f = workspaceFixture(async () => ({
      list: Array.from({ length: count }, (_, i) => ({
        id: `s${i}`,
        serviceName: `服务${i}`,
      })),
      total: 200,
    }));
    await f.api.loadServices(2, count);
    assert.equal(f.requests.length, 1);
    assert.equal(f.requests[0].payload.departmentOnly, true);
    assert.equal(f.requests[0].payload.serviceCreateType, "api");
    assert.equal(f.requests[0].payload.pageSize, count);
    assert.equal(f.api.services.value.length, count);
    assert.equal(f.api.total.value, 200);
  });

test("workspace badge counts displayed drafts and services without counting opened services twice", async () => {
  const f = workspaceFixture(async () => ({ list: [{ id: "saved", serviceName: "已登记服务" }], total: 1 }));
  f.api.startWorkspace();
  await f.api.loadServices(1);
  assert.equal(f.api.displayedServiceCount.value, 2);
  f.api.onSaved({ id: "saved", name: "已登记服务" });
  assert.equal(f.api.displayedServiceCount.value, 1);
  await f.ready();
  await f.api.addDraft();
  assert.equal(f.api.displayedServiceCount.value, 2);
});

test("workspace keeps each service's unsaved fields, parameters and examples while switching", async () => {
  const f = workspaceFixture();
  f.api.startWorkspace();
  await f.ready();
  const first = f.api.entries.value[0];
  f.setModel({
    name: "第一条",
    publishParams: [{ paramName: "page" }],
    extendInfo: { requestExample: "first" },
  });
  await f.api.addDraft();
  await f.ready();
  const second = f.api.entries.value[1];
  f.setModel({
    name: "第二条",
    providerConfig: { address: "https://example.test/second" },
  });
  await f.api.selectEntry(first);
  await f.ready();
  assert.equal(f.api.editorData.value.name, "第一条");
  assert.equal(f.api.editorData.value.publishParams[0].paramName, "page");
  assert.equal(f.api.editorData.value.extendInfo.requestExample, "first");
  assert.equal(first.dirty, true);
  await f.api.selectEntry(second);
  await f.ready();
  assert.equal(f.api.editorData.value.name, "第二条");
  assert.equal(f.api.editorData.value.providerConfig.address, "https://example.test/second");
  assert.equal(f.requests.filter((r) => r.url.endsWith("/detail")).length, 0);
});

test("workspace saves one service, retains its returned ID and adds another only after success", async () => {
  const f = workspaceFixture();
  f.api.startWorkspace();
  await f.ready();
  f.setModel({ name: "已保存服务" });
  f.setSave(async () => {
    const saved = { name: "已保存服务", id: "saved" };
    f.setModel(saved);
    f.api.onSaved(saved);
    return true;
  });
  await f.api.saveCurrent(true);
  assert.equal(f.api.entries.value.length, 2);
  assert.equal(f.api.entries.value[0].id, "saved");
  assert.equal(f.api.entries.value[0].dirty, false);
  assert.notEqual(f.api.activeKey.value, f.api.entries.value[0].key);
  assert.equal(f.events.length, 0);
  await f.ready();
  f.setSave(async () => false);
  await f.api.saveCurrent(true);
  assert.equal(f.api.entries.value.length, 2);
  assert.equal(f.api.entries.value[1].id, "");
});

test("workspace confirms abandoning dirty drafts and preserves them when close is cancelled", async () => {
  const f = workspaceFixture();
  f.api.startWorkspace();
  await f.ready();
  f.setModel({ name: "尚未保存" });
  f.setConfirm(async () => false);
  await f.api.requestClose();
  assert.equal(f.confirmations.length, 1);
  assert.equal(f.events.length, 0);
  assert.equal(f.api.entries.value[0].record.name, "尚未保存");
  f.setConfirm(async () => true);
  await f.api.requestClose();
  assert.deepEqual(f.events, [["update:modelValue", false]]);
});

test("workspace fetches detail only on user selection and preserves the editor on detail failure", async () => {
  const f = workspaceFixture(async (url, body) => {
    if (url.endsWith("/detail")) {
      if (body.apiId === "denied") throw new Error("无权访问该服务");
      return { id: body.apiId, name: "已有服务" };
    }
    return { list: [], total: 0 };
  });
  f.api.startWorkspace();
  await f.ready();
  f.setModel({ name: "未保存内容" });
  await f.api.selectService({ id: "s1", name: "已有服务" });
  await f.ready();
  const selected = f.api.activeKey.value;
  await f.api.selectService({ id: "denied", name: "拒绝对象" });
  assert.equal(f.api.activeKey.value, selected);
  assert.equal(f.api.entries.value[0].record.name, "未保存内容");
  assert.equal(f.api.entries.value.length, 2);
  assert.deepEqual(f.messages, ["无权访问该服务"]);
  assert.equal(f.requests.filter((r) => r.url.endsWith("/detail")).length, 2);
});

test("workspace ignores an older list response and exposes retryable list errors", async () => {
  let oldResolve;
  const f = workspaceFixture(async (url, body) => {
    if (body.pageNum === 1)
      return new Promise((resolve) => {
        oldResolve = resolve;
      });
    if (body.pageNum === 3) throw new Error("服务列表暂时不可用");
    return { list: [{ id: "new", name: "新结果" }], total: 1 };
  });
  const older = f.api.loadServices(1);
  await f.api.loadServices(2);
  oldResolve({ list: [{ id: "old" }], total: 100 });
  await older;
  assert.equal(f.api.services.value[0].id, "new");
  await f.api.loadServices(3);
  assert.equal(f.api.listError.value, "服务列表暂时不可用");
  assert.equal(f.api.listLoading.value, false);
});

test("registration reads configured rules once per workspace and keeps parameter and example fields in the form model", async () => {
  let definitions = 0;
  const f = contentFixture(
    async (url) => (url === "/sym/user/me" ? { orgId: "org" } : { id: "saved" }),
    async (name) => {
      definitions++;
      assert.equal(name, "登记API服务");
      return { rules: definitionRules() };
    }
  );
  await f.api.setFormData({
    publishParams: [{ paramName: "page", paramType: "string" }],
    extendInfo: { requestExample: "one" },
  });
  assert.equal((await f.api.getPayload()).publishParams[0].paramName, "page");
  f.setFields({
    requestExample: "two",
    responseExample: "response",
    publishParams: [{ paramName: "size", paramType: "number" }],
  });
  assert.equal((await f.api.getPayload()).extendInfo.requestExample, "two");
  assert.equal((await f.api.getPayload()).extendInfo.responseExample, "response");
  assert.equal((await f.api.getPayload()).publishParams[0].paramName, "size");
  await f.api.setFormData({ name: "another" });
  assert.equal(definitions, 1);
  assert.equal((await f.api.getPayload()).publishParams.length, 0);
});
test("a missing or invalid dynamic form prevents saving and supports a fresh retry", async () => {
  let attempts = 0,
    saves = 0;
  const f = contentFixture(
    async (url) => {
      if (url.endsWith("/save")) saves++;
      return { orgId: "org" };
    },
    async () => {
      if (++attempts === 1) throw new Error("未找到登记API服务配置");
      return { rules: definitionRules() };
    }
  );
  await f.api.setFormData({});
  assert.equal(await f.api.handleSave(), false);
  assert.equal(saves, 0);
  await f.api.setFormData({});
  assert.equal((await f.api.getFormData()).orgId, "org");
  assert.equal(attempts, 2);
});
function formUtilsFixture(post, setting = { systemCode: "公安" }) {
  const source = fs.readFileSync(
    path.join(root, "data-elements-chengtian/src/utils/form.ts"),
    "utf8"
  );
  const ast = ts.createSourceFile("form.ts", source, ts.ScriptTarget.Latest, true);
  const body = ast.statements
    .filter((node) => !ts.isImportDeclaration(node))
    .map((node) => node.getText(ast))
    .join("\n")
    .replace("export class FormUtils", "class FormUtils");
  const js = ts.transpileModule(body, {
    compilerOptions: { target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const calls = [];
  const Class = new Function(
    "formCreate",
    "get",
    "set",
    "$setting",
    "$common",
    "$dict",
    `${js}\nreturn FormUtils;`
  )(
    {},
    undefined,
    undefined,
    setting,
    {
      post: async (url, payload) => {
        calls.push({ url, payload });
        return post(payload);
      },
    },
    { $dict: {}, getDictItems: () => [] }
  );
  return { Class, instance: new Class(), calls, setting };
}
test("trusted form parser accepts native layout and custom fields, and rejects duplicate nested fields", () => {
  const { Class } = formUtilsFixture(async () => []);
  assert.equal(
    Class.parseRules('[{type:"ElTabs",children:[{type:"CodeEditor",field:"example"}]}]').length,
    1
  );
  assert.throws(() => Class.parseRules("{}"), /规则数组/);
  assert.throws(
    () =>
      Class.parseRules(
        '[{type:"input",field:"name"},{type:"ElTabs",children:[{type:"input",field:"name"}]}]'
      ),
    /字段重复/
  );
  assert.throws(() => Class.parseRules('[{field:"name"}]'), /type/);
});
test("definition lookup uses the active environment, reads fresh rules, and does not accept a client tenant ID", async () => {
  let title = "first";
  const f = formUtilsFixture(async (payload) => [
    {
      tid: "form",
      formName: payload.formName,
      env: payload.env,
      formConfig: "preserved",
      formJson: `[{type:"input",field:"name",title:"${title}"}]`,
    },
  ]);
  const one = await f.instance.loadDefinition("登记API服务");
  assert.equal(one.rules[0].title, "first");
  title = "changed";
  f.setting.systemCode = "海渔";
  const two = await f.instance.loadDefinition("登记API服务");
  assert.equal(two.rules[0].title, "changed");
  assert.equal(two.meta.formConfig, "preserved");
  assert.deepEqual(
    f.calls.map((c) => c.payload),
    [
      { formName: "登记API服务", env: "公安" },
      { formName: "登记API服务", env: "海渔" },
    ]
  );
});
test("missing, duplicate, and malformed definitions fail closed", async () => {
  const missing = formUtilsFixture(async () => []);
  await assert.rejects(missing.instance.loadDefinition("登记API服务"), /未找到/);
  const duplicate = formUtilsFixture(async () =>
    Array.from({ length: 2 }, () => ({
      formName: "登记API服务",
      env: "公安",
      formJson: "[]",
    }))
  );
  await assert.rejects(duplicate.instance.loadDefinition("登记API服务"), /重复/);
  const malformed = formUtilsFixture(async () => ({ unexpected: true }));
  await assert.rejects(malformed.instance.loadDefinition("登记API服务"), /格式/);
});
test("configured defaults apply to new services while persisted method and version keep their values", async () => {
  const rules = definitionRules();
  rules.find((r) => r.field === "httpMethod").value = "PATCH";
  rules.find((r) => r.field === "version").value = "2.4";
  const f = contentFixture(
    async () => ({ orgId: "org" }),
    async () => ({ rules })
  );
  await f.api.setFormData({});
  assert.equal((await f.api.getFormData()).httpMethod, "PATCH");
  assert.equal((await f.api.getFormData()).version, "2.4");
  await f.api.setFormData({
    id: "existing",
    version: "1.8",
    providerConfig: { httpMethod: "POST" },
  });
  assert.equal((await f.api.getFormData()).httpMethod, "POST");
  assert.equal((await f.api.getFormData()).version, "1.8");
});
function formTableFixture() {
  const source = fs
    .readFileSync(
      path.join(root, "data-elements-chengtian/src/components/form/form-table.vue"),
      "utf8"
    )
    .match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
  const ast = ts.createSourceFile("adapter.ts", source, ts.ScriptTarget.Latest, true);
  const body = ast.statements
    .filter((node) => !ts.isImportDeclaration(node))
    .map((node) => node.getText(ast))
    .join("\n");
  const js = ts.transpileModule(body, {
    compilerOptions: { target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const props = vue.reactive({ modelValue: [] }),
    data = vue.ref([]),
    events = [];
  const factory = new Function(
    "computed",
    "nextTick",
    "ref",
    "watch",
    "cloneDeep",
    "defineProps",
    "withDefaults",
    "defineEmits",
    "defineExpose",
    `${js}\nreturn tableRef;`
  );
  const table = factory(
    vue.computed,
    vue.nextTick,
    vue.ref,
    vue.watch,
    (v) => structuredClone(vue.toRaw(v)),
    () => props,
    (v) => v,
    () => (name, rows) => {
      events.push(name);
      if (name === "update:modelValue") props.modelValue = rows;
    },
    () => {}
  );
  table.value = {
    getData: () => data.value.map((row) => ({ ...row })),
    setData: (rows) => {
      data.value = rows;
    },
  };
  return {
    props,
    data,
    events,
    flush: async () => {
      await vue.nextTick();
      await vue.nextTick();
      await vue.nextTick();
    },
  };
}
for (const count of [20, 100])
  test(`form table synchronizes ${count} imported rows, edits, removals and external draft changes without losing model values`, async () => {
    const f = formTableFixture();
    await f.flush();
    f.data.value = Array.from({ length: count }, (_, i) => ({
      paramName: `p${i}`,
      paramType: "string",
    }));
    await f.flush();
    assert.equal(f.props.modelValue.length, count);
    f.data.value[0].paramName = "edited";
    await f.flush();
    assert.equal(f.props.modelValue[0].paramName, "edited");
    f.data.value.splice(0, 1);
    await f.flush();
    assert.equal(f.props.modelValue.length, count - 1);
    f.props.modelValue = [{ paramName: "other draft", paramType: "number" }];
    await f.flush();
    assert.equal(f.data.value[0].paramName, "other draft");
    assert.equal(f.events.filter((e) => e === "update:modelValue").length, 3);
  });

test("parameter imports normalize request/response labels, retain valid values and default missing legacy direction", async () => {
  const f = formTableFixture();
  const flatten = (rules) => rules.flatMap((rule) => [rule, ...flatten(rule.children || [])]);
  const { props } = flatten(definitionRules()).find((rule) => rule.field === "publishParams");
  Object.assign(f.props, props);
  await f.flush();
  f.data.value = [
    { paramName: "page", paramScope: "入参", paramType: "number" },
    { paramName: "total", paramScope: "出参", paramType: "number" },
    { paramName: "legacy", paramType: "string" },
    { paramName: "data", paramScope: "response", paramType: "array" },
    { paramName: "invalid", paramScope: "unknown", paramType: "string" },
    { paramName: "old Chinese header", paramScope: "number" },
  ];
  await f.flush();
  assert.deepEqual(f.props.modelValue.map((row) => row.paramScope), ["request", "response", "request", "response", "unknown", "request"]);
  assert.deepEqual(f.data.value.map((row) => row.paramScope), ["request", "response", "request", "response", "unknown", "request"]);
  assert.equal(f.props.modelValue[5].paramType, "number");
  assert.equal(props.options.columns[1].field, "paramScope");
  assert.equal(props.options.columns.find((column) => column.field === "paramType").title, "数据类型");
});

test("CSV parameter import preserves quoted JSON, leading zeros and line breaks without interpreting markup", () => {
  const source = fs.readFileSync(path.join(root, "data-elements-chengtian/src/components/_utils/csv-import.ts"), "utf8");
  const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } });
  const exports = {};
  vm.runInNewContext(outputText, { exports });
  const json = JSON.stringify(Array.from({ length: 20 }, (_, i) => ({ id: `00${i}`, name: "示例数据" })));
  const quote = (value) => '"' + value.replaceAll('"', '""') + '"';
  const result = exports.csvToTableHtml('\uFEFF参数类型,参数名称,示例值\r\n出参,data,' + quote(json) + '\r\n入参,name,' + quote('001\r\n<img src=x onerror=alert(1)>'));
  assert.ok(result.includes(json.replaceAll('"', '&quot;')));
  assert.ok(result.includes('001&#13;&#10;&lt;img src=x onerror=alert(1)&gt;'));
  assert.doesNotMatch(result, /<img|#\d+@\d+/);
  assert.throws(() => exports.csvToTableHtml('参数名称,示例值\na,"missing'), /未闭合/);
  assert.throws(() => exports.csvToTableHtml('参数名称\na,b'), /超过表头/);
});

test("required values normalize boolean and legacy dictionary codes to the persisted binary contract", async () => {
  const values = [true, false, "true", "false", "1", "0", 1, 0, "是", "否", "必填", "可选"];
  const expected = values.map((_, index) => index % 2 === 0 ? "1" : "0");
  const f = contentFixture(async () => ({ orgId: "org" }));
  await f.api.setFormData({ publishParams: values.map((required, i) => ({ paramName: `p${i}`, paramType: "string", required })) });
  assert.deepEqual((await f.api.getParamsData()).map((row) => row.required), expected);
  const table = formTableFixture();
  const flatten = (rules) => rules.flatMap((rule) => [rule, ...flatten(rule.children || [])]);
  Object.assign(table.props, flatten(definitionRules()).find((rule) => rule.field === "publishParams").props);
  await table.flush();
  table.data.value = values.map((required, i) => ({ paramName: `p${i}`, paramType: "string", required }));
  await table.flush();
  assert.deepEqual(table.props.modelValue.map((row) => row.required), expected);
});

test("one configured generation action follows the active tab and leaves the other example intact", async () => {
  const f = contentFixture(async () => ({ orgId: "org" }));
  await f.api.setFormData({ publishParams: [
    { paramName: "q", paramScope: "request", paramType: "string", showcaseValue: "search" },
    { paramName: "total", paramScope: "response", paramType: "number", showcaseValue: "12" },
  ], extendInfo: { requestExample: "manual request", responseExample: "manual response" } });
  const flatten = (rules) => rules.flatMap((rule) => [rule, ...flatten(rule.children || [])]);
  const rules = flatten(f.rules);
  const actions = rules.filter((rule) => rule.name === "generateActiveExample");
  assert.equal(actions.length, 1);
  assert.equal(rules.filter((rule) => /generate(Request|Response)Example/.test(rule.name || "")).length, 0);
  await actions[0].on.click();
  assert.deepEqual(JSON.parse(await f.api.getRequestExample()), { q: "search" });
  assert.equal(await f.api.getResponseExample(), "manual response");
  await rules.find((rule) => rule.name === "serviceExamples").on.tabChange("response");
  await actions[0].on.click();
  assert.deepEqual(JSON.parse(await f.api.getResponseExample()), { total: 12 });
  assert.deepEqual(JSON.parse(await f.api.getRequestExample()), { q: "search" });
});

test("initial examples use only their own direction and preserve numeric, boolean, structured and string values", async () => {
  const f = contentFixture(async () => ({ orgId: "org" }));
  await f.api.setFormData({ publishParams: [
    { paramName: "page", paramScope: "request", paramType: "number", defaultValue: "20" },
    { paramName: "id", paramScope: "request", paramType: "string", showcaseValue: "001" },
    { paramName: "ok", paramScope: "response", paramType: "boolean", showcaseValue: "false" },
    { paramName: "items", paramScope: "response", paramType: "array", showcaseValue: '[{"id":1}]' },
    { paramName: "meta", paramScope: "response", paramType: "object" },
  ] });
  assert.deepEqual(JSON.parse(await f.api.getRequestExample()), { page: 20, id: "001" });
  assert.deepEqual(JSON.parse(await f.api.getResponseExample()), { ok: false, items: [{ id: 1 }], meta: {} });
  const original = await f.api.getRequestExample();
  f.setFields({ publishParams: [{ paramName: "changed", paramScope: "request", paramType: "number" }] });
  assert.equal(await f.api.generateExample("request", false), false);
  assert.equal(await f.api.getRequestExample(), original);
});

test("explicit generation confirms replacement, updates only the chosen example and keeps other fields", async () => {
  let allow = false, confirms = 0;
  const f = contentFixture(async () => ({ orgId: "org" }), undefined, async () => {
    confirms++; if (!allow) throw "cancel";
  });
  await f.api.setFormData({ name: "保留服务名", publishParams: [
    { paramName: "count", paramScope: "response", paramType: "number", showcaseValue: "12" },
  ], extendInfo: { requestExample: "manual request", responseExample: "manual response" } });
  assert.equal(await f.api.generateExample("response"), false);
  assert.equal(await f.api.getResponseExample(), "manual response");
  allow = true;
  assert.equal(await f.api.generateExample("response"), true);
  assert.deepEqual(JSON.parse(await f.api.getResponseExample()), { count: 12 });
  assert.equal(await f.api.getRequestExample(), "manual request");
  assert.equal((await f.api.getFormData()).name, "保留服务名");
  assert.equal(confirms, 2);
});

test("first editor visit generates once, and a deliberately cleared saved example stays empty on reload", async () => {
  const f = contentFixture(async () => ({ orgId: "org" }));
  await f.api.setFormData({});
  f.setFields({ publishParams: [{ paramName: "q", paramScope: "request", paramType: "string", showcaseValue: "search" }] });
  assert.equal(await f.api.generateExample("request", false), true);
  assert.deepEqual(JSON.parse(await f.api.getRequestExample()), { q: "search" });
  f.setFields({ requestExample: "" });
  const saved = await f.api.getPayload();
  saved.extendInfo.exampleInitialization = JSON.stringify(saved.extendInfo.exampleInitialization);
  await f.api.setFormData(saved);
  assert.equal(await f.api.getRequestExample(), "");
  assert.equal(await f.api.generateExample("request", false), false);
});

test("a generation confirmation cannot overwrite a newly selected service", async () => {
  let release;
  const f = contentFixture(async () => ({ orgId: "org" }), undefined, () => new Promise((resolve) => { release = resolve; }));
  await f.api.setFormData({ id: "a", extendInfo: { requestExample: "manual A" } });
  const pending = f.api.generateExample("request");
  await vue.nextTick();
  await f.api.setFormData({ id: "b", extendInfo: { requestExample: "manual B" } });
  release();
  assert.equal(await pending, false);
  assert.equal(await f.api.getRequestExample(), "manual B");
});

for (const count of [20, 100]) test(`${count} mixed parameter rows save with one write request and no per-row reads`, async () => {
  const requests = [];
  const f = contentFixture(async (url, body) => {
    requests.push({ url, body });
    return url === "/sym/user/me" ? { orgId: "org" } : { id: "saved" };
  });
  await f.api.setFormData({ publishParams: Array.from({ length: count }, (_, i) => ({
    paramName: `p${i}`, paramType: "string", paramScope: i % 2 ? "response" : "request",
  })) });
  f.setFields({ name: "参数验证", address: "/validation/parameters" });
  assert.equal(await f.api.handleSave(), true);
  assert.equal(requests.length, 2);
  const saved = requests[1].body.publishParams;
  assert.equal(saved.length, count);
  assert.equal(saved.filter((row) => row.res === 1).length, count / 2);
  assert.equal(saved.filter((row) => row.res === 0).length, count / 2);
});
function formEditorFixture() {
  const source = fs
    .readFileSync(path.join(root, "lowcode/09.系统管理/1.3 表单配置页(form-config).vue"), "utf8")
    .match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
  const ast = ts.createSourceFile("editor.ts", source, ts.ScriptTarget.Latest, true);
  const body = ast.statements
    .filter((node) => !ts.isImportDeclaration(node))
    .map((node) => node.getText(ast))
    .join("\n");
  const js = ts.transpileModule(body, {
    compilerOptions: { target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const rows = [
    {
      tid: "original",
      formName: "登记API服务",
      env: "公安",
      formJson: '[{type:"input",field:"name",title:"原始标题"}]',
      formConfig: '{"owned":"tenant"}',
    },
  ];
  const requests = [],
    feedback = [];
  let cacheClears = 0;
  const factory = new Function(
    "ref",
    "reactive",
    "computed",
    "onMounted",
    "onBeforeUnmount",
    "cloneDeep",
    "FormUtils",
    "$setting",
    "$common",
    "$message",
    "$dialog",
    "$form",
    `${js}\nreturn {loadForms,handleFormChange,handleSave,handleRenameForm,handleAddForm,currentForm,currentFormName,currentJson,isModified,formList,saving};`
  );
  const api = factory(
    vue.ref,
    vue.reactive,
    vue.computed,
    () => {},
    () => {},
    (v) => structuredClone(vue.toRaw(v)),
    formUtilsFixture(async () => []).Class,
    { systemCode: "公安" },
    {
      post: async (url, payload) => {
        requests.push({ url, payload: structuredClone(payload) });
        if (url.endsWith("action=get"))
          return rows
            .filter((row) => !payload.tid || payload.tid === row.tid)
            .map((row) => ({ ...row }));
        if (url.endsWith("action=save")) {
          let row = rows.find((row) => row.tid === payload.tid);
          if (!row) {
            row = { tid: "new-form" };
            rows.push(row);
          }
          Object.assign(row, payload);
          return row.tid;
        }
        throw new Error("unexpected request");
      },
    },
    { success: (v) => feedback.push(v), error: (v) => feedback.push(v) },
    { confirm: async () => {}, prompt: async () => ({ value: "新配置" }) },
    { clearCache: () => cacheClears++ }
  );
  return {
    api,
    rows,
    requests,
    feedback,
    get cacheClears() {
      return cacheClears;
    },
  };
}
test("form editor preserves auxiliary metadata and canonical whitespace on repeated saves", async () => {
  const f = formEditorFixture();
  await f.api.loadForms();
  f.api.currentJson.value = '[{type:"input",field:"name",title:"新标题"}]\n';
  f.api.isModified.value = true;
  await f.api.handleSave();
  assert.equal(f.rows[0].formConfig, '{"owned":"tenant"}');
  assert.equal(f.api.isModified.value, false);
  assert.equal(f.cacheClears, 1);
  f.api.currentJson.value = '[{type:"input",field:"name",title:"再次修改"}]\n';
  f.api.isModified.value = true;
  await f.api.handleSave();
  assert.equal(f.requests.filter((r) => r.url.endsWith("action=save")).length, 2);
  assert.equal(f.rows[0].formJson, f.api.currentJson.value);
  assert.equal(f.api.saving.value, false);
});
test("form editor refuses a stale overwrite and preserves unsaved text", async () => {
  const f = formEditorFixture();
  await f.api.loadForms();
  f.rows[0].formJson = '[{type:"input",field:"name",title:"其他编辑者"}]';
  f.api.currentJson.value = '[{type:"input",field:"name",title:"尚未保存"}]';
  f.api.isModified.value = true;
  await f.api.handleSave();
  assert.equal(f.requests.filter((r) => r.url.endsWith("action=save")).length, 0);
  assert.match(f.feedback[0], /其他编辑者/);
  assert.match(f.api.currentJson.value, /尚未保存/);
  assert.equal(f.api.isModified.value, true);
});
test("form rename sends the complete definition and retains the current unsaved edit", async () => {
  const f = formEditorFixture();
  await f.api.loadForms();
  f.api.currentJson.value = '[{type:"input",field:"name",title:"尚未保存"}]';
  f.api.isModified.value = true;
  await f.api.handleRenameForm(f.api.currentForm.value);
  const request = f.requests.find((r) => r.url.endsWith("action=save"));
  assert.equal(request.payload.formName, "新配置");
  assert.match(request.payload.formJson, /原始标题/);
  assert.equal(request.payload.formConfig, '{"owned":"tenant"}');
  assert.equal(f.api.currentFormName.value, "新配置");
  assert.equal(f.api.isModified.value, true);
  assert.match(f.api.currentJson.value, /尚未保存/);
});
test("creating a configuration selects the new record ID before subsequent editing", async () => {
  const f = formEditorFixture();
  await f.api.loadForms();
  await f.api.handleAddForm();
  assert.equal(f.api.currentForm.value.tid, "new-form");
  assert.equal(f.api.currentFormName.value, "新配置");
  f.api.currentJson.value = '[{type:"input",field:"name",title:"新增后编辑"}]';
  f.api.isModified.value = true;
  await f.api.handleSave();
  const save = f.requests.filter((r) => r.url.endsWith("action=save")).at(-1);
  assert.equal(save.payload.tid, "new-form");
  assert.match(f.rows[1].formJson, /新增后编辑/);
});

for(const count of [20,100])test(`datasource workspace ${count} rows preserve the source for list, detail and every new draft`,async()=>{
  const f=workspaceFixture(async(url,body)=>url.endsWith('/detail')?{id:body.apiId,datasourceId:'source-a',name:'已登记服务'}:{list:Array.from({length:count},(_,i)=>({id:'s'+i,serviceName:'服务'+i})),total:count},'source-a');
  await f.api.loadServices(1,count);assert.equal(f.requests.length,1);assert.equal(f.requests[0].payload.datasourceId,'source-a');assert.equal(f.requests[0].payload.departmentOnly,false);
  f.api.startWorkspace();await f.ready();assert.equal(f.api.editorData.value.datasourceId,'source-a');
  await f.api.addDraft();assert.equal(f.api.editorData.value.datasourceId,'source-a');
  await f.api.selectService({id:'s0'});assert.equal(f.requests.filter(r=>r.url.endsWith('/detail')).length,1);assert.equal(f.requests.find(r=>r.url.endsWith('/detail')).payload.datasourceId,'source-a');
});

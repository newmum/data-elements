import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";
import { execFileSync } from "node:child_process";
import test from "node:test";
const root = fileURLToPath(new URL("../../", import.meta.url));
const require = createRequire(path.join(root, "data-elements-chengtian/package.json"));
const vue = require("vue"),
  ts = require("typescript");
const contentPath =
  "lowcode/06.数据服务/数据服务(data-serve)/服务组件/api注册(api-register-content).vue";
const compiled = JSON.parse(
  execFileSync(process.execPath, ["tools/compile-lowcode.mjs", contentPath], {
    cwd: root,
    encoding: "utf8",
  })
);
const bundle = JSON.parse(fs.readFileSync(path.join(root, compiled.compiled), "utf8"));
function contentFixture(post) {
  let refIndex = 0,
    model = {},
    parameters = [];
  const feedback = [],
    events = [];
  let api;
  const form = {
    validate: async () => true,
    getFormData: async () => ({ ...model }),
    setValue: (value) => {
      model = { ...value };
    },
  };
  const parameterApi = {
    validate: async () => true,
    getData: () => parameters,
    setData: (value) => {
      parameters = value;
    },
  };
  const runtime = {
    ...vue,
    ref: (value) =>
      value === undefined && refIndex++ === 0
        ? vue.ref(form)
        : value === undefined && refIndex === 2
          ? vue.ref(parameterApi)
          : vue.ref(value),
  };
  const messages = {
    success: (value) => feedback.push(value),
    error: (value) => feedback.push(value),
    warning: (value) => feedback.push(value),
  };
  const libraries = {
    vue: runtime,
    "element-plus": { ElMessage: messages },
    "@/store": { useDictStore: () => ({ getDictItems: () => [] }) },
    "@/utils/form": { FormUtils: { transFormText: (value) => value } },
  };
  const component = new Function("___magic__import__", "$common", bundle.compileJs)(
    (name, key) => libraries[name]?.[key],
    { post }
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
    "activateMetadataImportForMode",
    `${selection}\nreturn selectAccessMode;`
  )([{ value: "proxy" }], accessMode, () => metadataReads++);
  select("proxy");
  assert.equal(accessMode.value, "proxy");
  assert.equal(metadataReads, 0);
});
function proxyFixture(post, push) {
  const context = { open: vue.ref(true) },
    errors = [];
  const create = new Function(
    "$common",
    "serviceRouter",
    "registerModalContext",
    "$message",
    "proxyServiceKeyword",
    "proxyNavigating",
    "currentDb",
    "accessMode",
    `${declaration("fetchDepartmentServices")}\n${declaration("goToServiceRegistration")}\n${declaration("next")}\nreturn {fetchDepartmentServices,next,goToServiceRegistration};`
  );
  return {
    api: create(
      { post },
      { push },
      context,
      { error: (value) => errors.push(value) },
      vue.ref("服务"),
      vue.ref(false),
      vue.ref({}),
      vue.ref("proxy")
    ),
    context,
    errors,
  };
}
for (const count of [20, 100])
  test(`${count} department services use one paged request, then next navigates without creating a database`, async () => {
    const requests = [],
      routes = [];
    const f = proxyFixture(
      async (url, body) => {
        requests.push({ url, body });
        return {
          list: Array.from({ length: count }, (_, i) => ({ tid: `s${i}` })),
          total: 150,
        };
      },
      async (route) => {
        routes.push(route);
      }
    );
    const result = await f.api.fetchDepartmentServices({
      pageNo: 2,
      pageSize: count,
      sortField: "serviceName",
      sortDir: "asc",
    });
    assert.equal(result.list.length, count);
    assert.equal(result.total, 150);
    assert.equal(requests.length, 1);
    assert.equal(requests[0].body.departmentOnly, true);
    assert.equal(requests[0].body.pageNum, 2);
    assert.equal((await f.api.next()).navigationHandled, true);
    assert.deepEqual(routes, [{ path: "/deal/data-server/list", query: { register: "1" } }]);
    assert.equal(f.context.open.value, false);
    assert.equal(requests.length, 1);
  });
test("failed navigation preserves registration context", async () => {
  const f = proxyFixture(
    async () => ({}),
    async () => {
      throw new Error("路由暂不可用");
    }
  );
  await assert.rejects(f.api.next(), /路由暂不可用/);
  assert.equal(f.context.open.value, true);
});

function footerFixture(next) {
  const script = fs
    .readFileSync(
      path.join(root, "data-elements-chengtian/src/components/business/register-body.vue"),
      "utf8"
    )
    .match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
  const ast = ts.createSourceFile("register-body.ts", script, ts.ScriptTarget.Latest, true);
  const declarations = ["customApi", "nextApi"].map((name) =>
    ast.statements
      .find(
        (node) =>
          ts.isVariableStatement(node) &&
          node.declarationList.declarations.some((item) => item.name.getText(ast) === name)
      )
      .getText(ast)
  );
  const js = ts.transpileModule(declarations.join("\n"), {
    compilerOptions: { target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const events = [],
    loading = [],
    warnings = [];
  const api = new Function(
    "actionPending",
    "store",
    "emit",
    "callCurrentStepMethod",
    "nextTick",
    "window",
    "$message",
    "console",
    `${js}\nreturn nextApi;`
  )(
    { value: false },
    { setLoadStatus: (...args) => loading.push(args) },
    (...args) => events.push(args),
    next,
    async () => {},
    { setTimeout: (callback) => callback() },
    { warning: (message) => warnings.push(message) },
    { error: () => {} }
  );
  return { api, events, loading, warnings };
}

test("original next button advances ordinary steps and suppresses advancement after a service redirect", async () => {
  const ordinary = footerFixture(async () => undefined);
  await ordinary.api();
  assert.deepEqual(ordinary.events, [["finishTriggerFn", "next"]]);
  const proxy = footerFixture(async () => ({ navigationHandled: true }));
  await proxy.api();
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
  await f.api();
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
    route = { path: "/deal/data-server/list", query: { register: "1", keyword: "保留上下文" } };
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

function workspaceFixture(post = async () => ({ list: [], total: 0 })) {
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
    `${js}\nreturn {startWorkspace,loadServices,selectService,selectEntry,addDraft,saveCurrent,onSaved,captureCurrent,onEditorReady,requestClose,removeDraft,canClose,entries,activeKey,editorData,editorReady,contentRef,services,total,listError,listLoading,keyword};`
  )(
    vue.ref,
    vue.computed,
    () => {},
    () => {},
    () => ({ modelValue: true, apiDetail: { groupId: "group" } }),
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
    { error: (msg) => messages.push(msg), warning: (msg) => messages.push(msg) },
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
      list: Array.from({ length: count }, (_, i) => ({ id: `s${i}`, serviceName: `服务${i}` })),
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
  f.setModel({ name: "第二条", providerConfig: { address: "https://example.test/second" } });
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

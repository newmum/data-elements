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
function contentFixture(post, loadDefinition = async () => ({ rules: definitionRules() })) {
  let refIndex = 0,
    model = {};
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
  const runtime = {
    ...vue,
    ref: (value) => (value === undefined && refIndex++ === 0 ? vue.ref(form) : vue.ref(value)),
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

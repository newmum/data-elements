import assert from "node:assert/strict";
import fs from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import vm from "node:vm";

const require = createRequire(new URL("../../data-elements-chengtian/package.json", import.meta.url));
const ts = require("typescript");
const { ref } = require("vue");
const { parse } = require("vue/compiler-sfc");
const base = "../../lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)";
const read = (path) => fs.readFileSync(new URL(path, import.meta.url), "utf8");
const actions = {};
new Function("exports", ts.transpileModule(
  read("../../data-elements-chengtian/src/components/business/registration-actions.ts"),
  { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }
).outputText)(actions);
const review = read(`${base}/04.登记内容审查(register-confirm).vue`);
const registration = read(`${base}/01.登记数据源(register-db).vue`);
const list = read(`${base}.vue`);

test("proxy review delegates its source-scoped read-only list to the shared component", () => {
  const root = parse(review).descriptor.template.ast.children.find((node) => node.tag === "div");
  const serviceSection = root.children.find((node) => node.tag === "section" && node.props.some((prop) => prop.name === "if" && prop.exp?.content === "isProxyAccess"));
  assert.ok(serviceSection, "service review must be a direct child of the shared review page");
  const panel = serviceSection.children.find((node) => node.tag === "section");
  const component = panel.children.find((node) => node.tag === "ProxyDatasourceServices");
  assert.ok(component);
  assert.equal(component.props.find((prop) => prop.name === "mode")?.value?.content, "review");
  assert.equal(component.props.find((prop) => prop.name === "bind" && prop.arg?.content === "source-id")?.exp?.content, "sourceTid");
  assert.equal(component.props.find((prop) => prop.name === "bind" && prop.arg?.content === "source-loading")?.exp?.content, "sourceDetailLoading");
  assert.equal(component.props.find((prop) => prop.name === "bind" && prop.arg?.content === "source-error")?.exp?.content, "reviewSummaryError");
  assert.equal(component.props.find((prop) => prop.name === "on" && prop.arg?.content === "retry-source")?.exp?.content, "retryReviewSummary");
  assert.doesNotMatch(review, /\bfetchProxyServices\b|\bproxyServiceKeyword\b|\bproxyServiceTotal\b/);
});

function declaration(source, name) {
  const script = source.match(/<script[^>]*>([\s\S]*?)<\/script>/)[1];
  const ast = ts.createSourceFile("component.ts", script, ts.ScriptTarget.Latest, true);
  for (const statement of ast.statements) {
    if (ts.isFunctionDeclaration(statement) && statement.name?.text === name) return statement.getText(ast);
    if (ts.isVariableStatement(statement)) {
      const variable = statement.declarationList.declarations.find((item) => item.name.getText(ast) === name);
      if (variable) return `const ${variable.getText(ast)};`;
    }
  }
  throw new Error(`Missing declaration: ${name}`);
}

function completionFixture(post) {
  const calls = [];
  const summaryRequests = [];
  const scope = {
    sourceTid: ref("source-a"),
    source: ref({ tid: "source-a", dbName: "代理验证" }),
    isProxyAccess: ref(true),
    pushIsAttachmentMode: ref(false),
    isDataPushSource: ref(false),
    reviewLoading: ref(false),
    reviewSummaryError: ref(""),
    sourceDetailRequestId: 0,
    refreshReviewSummary: async (...args) => { summaryRequests.push(args); },
    $common: { post: async (url, body) => {
      calls.push([url, JSON.parse(JSON.stringify(body))]);
      return post(url, body);
    } },
  };
  vm.createContext(scope);
  const code = ["ensureReady", "finish", "persistCompletion", "commit"].map((name) => declaration(review, name)).join("\n");
  vm.runInContext(`${code}\nObject.assign(this, { finish, commit });`, scope);
  return { scope, calls, summaryRequests };
}

function useActualProxySummary({ scope, summaryRequests }) {
  Object.assign(scope, {
    sources: ref([{ tid: "source-a", poolCfg: { accessMode: "proxy" } }]),
    sourceDetail: ref(null),
    sourceDetailLoading: ref(false),
    tableListLoading: ref(false),
    realtimeTables: ref([]),
    physicalTableTotal: ref(0),
    tableChangeSnapshot: ref(null),
    requestWithTimeout: (task) => task(),
    console: { warn() {} },
  });
  scope.$common.get = () => { throw new Error("Unexpected metadata read"); };
  const refresh = vm.runInContext(
    `${declaration(review, "isProxyAccessSource")}\n${declaration(review, "refreshReviewSummary")}\nrefreshReviewSummary;`,
    scope
  );
  scope.refreshReviewSummary = (...args) => {
    summaryRequests.push(args);
    return refresh(...args);
  };
}

const sourceDetail = "/dst/database/detail";
const serviceList = "/dws/flowserve/gateway/api/list";
const completionUrl = "/dst/database/saveOrUpdate";
const unfilteredQuery = { datasourceId: "source-a", pageNum: 1, pageSize: 1, keyword: "" };

test("completion verifies actual services before saving even when the list has no visible matches", async () => {
  const { scope, calls, summaryRequests } = completionFixture(async (url) => {
    if (url === serviceList) return { data: { list: [{ tid: "valid" }], total: 3 } };
    assert.equal(url, completionUrl);
    return { assetStatus: 2 };
  });
  assert.equal((await scope.commit())[0].assetStatus, 2);
  assert.deepEqual(summaryRequests, [["source-a", 1, true]]);
  assert.deepEqual(calls, [
    [serviceList, unfilteredQuery],
    [completionUrl, { tid: "source-a", propList: { tid: "source-a", dbName: "代理验证", completeOnly: true } }],
  ]);
});

test("the shared action runner passes the proxy review result to commit without repeating checks", async (t) => {
  const fixture = completionFixture(async (url) => {
    if (url === sourceDetail) return { tid: "source-a", poolCfg: { accessMode: "proxy" } };
    if (url === serviceList) return { list: [{ tid: "valid" }], total: 1 };
    assert.equal(url, completionUrl);
    return { assetStatus: 2 };
  });
  useActualProxySummary(fixture);
  let reviewedItems;
  const committedItems = [];
  const completed = [];
  const errors = [];
  const step = {
    async finish() {
      reviewedItems = await fixture.scope.finish();
      return reviewedItems;
    },
    async commit(items) {
      committedItems.push(items);
      return fixture.scope.commit(items);
    },
  };
  const runner = actions.createRegistrationActionRunner({
    store: {
      state: { loadStatus: {}, steps: [], currentStep: 4 }, data: {}, registerClass: "db",
      setCurrentStep: () => assert.fail("A completed datasource must close its registration"),
    },
    getStep: () => step,
    nextTick: async () => {},
    delay: async () => {},
    emit: (result) => completed.push(result),
    warning: (message) => errors.push(message),
    success() {},
    reportError: (error) => errors.push(error),
    showDatasourceSuccess: false,
  });
  t.after(runner.dispose);
  await runner.handleAction("finish");

  assert.deepEqual(errors, []);
  assert.deepEqual(completed, ["completed"]);
  assert.equal(committedItems.length, 1);
  assert.equal(committedItems[0], reviewedItems);
  assert.equal(reviewedItems[0].assetStatus, 2);
  assert.deepEqual(fixture.summaryRequests, [["source-a", 1, true]]);
  assert.deepEqual(fixture.calls, [
    [sourceDetail, { tid: "source-a" }],
    [serviceList, unfilteredQuery],
    [completionUrl, { tid: "source-a", propList: { tid: "source-a", dbName: "代理验证", completeOnly: true } }],
  ]);
});

test("no authorized services and permission failures prevent completion writes", async () => {
  const empty = completionFixture(async () => ({ list: [], total: 0 }));
  await assert.rejects(empty.scope.commit(), /返回第一步登记至少一项数据服务/);
  assert.deepEqual(empty.calls, [[serviceList, unfilteredQuery]]);

  const accessError = new Error("当前账号无权访问此数据源的服务");
  const denied = completionFixture(async () => { throw accessError; });
  await assert.rejects(denied.scope.commit(), (error) => error === accessError);
  assert.deepEqual(denied.calls, [[serviceList, unfilteredQuery]]);
});

test("switching sources during the existence check cannot complete the previous source", async () => {
  let resolveList;
  let start;
  const started = new Promise((resolve) => { start = resolve; });
  const { scope, calls } = completionFixture(() => new Promise((resolve) => {
    resolveList = resolve;
    start();
  }));
  const pending = scope.commit();
  await started;
  scope.sourceTid.value = "source-b";
  resolveList({ list: [{ tid: "old-service" }], total: 1 });
  await assert.rejects(pending, /数据源已切换/);
  assert.deepEqual(calls, [[serviceList, unfilteredQuery]]);
});

test("proxy review loads only the authorized source detail and never requests table metadata", async () => {
  const requests = [];
  const scope = {
    sources: ref([{ tid: "source-a", poolCfg: { accessMode: "proxy" } }]), sourceTid: ref("source-a"),
    sourceDetail: ref(null), sourceDetailLoading: ref(false), tableListLoading: ref(false),
    reviewSummaryError: ref(""), realtimeTables: ref([]), physicalTableTotal: ref(0), tableChangeSnapshot: ref(null),
    sourceDetailRequestId: 1, requestWithTimeout: (task) => task(), console: { warn() {} },
    $common: { post: async (url) => { requests.push(url); return { tid: "source-a", poolCfg: { accessMode: "proxy" } }; }, get: () => { throw new Error("Unexpected metadata read"); } },
  };
  vm.createContext(scope);
  vm.runInContext(`${declaration(review, "isProxyAccessSource")}\n${declaration(review, "refreshReviewSummary")}\nthis.refresh = refreshReviewSummary;`, scope);
  await scope.refresh("source-a", 1, true);
  assert.deepEqual(requests, ["/dst/database/detail"]);
  assert.equal(scope.sourceDetail.value.tid, "source-a");
  assert.equal(scope.sourceDetailLoading.value, false);
  assert.equal(scope.tableListLoading.value, false);
  assert.equal(scope.reviewSummaryError.value, "");
});

test("proxy next remains in registration rather than reopening service registration", async () => {
  const scope = { accessMode: ref("proxy"), isFilePush: ref(false), proxyDatasourceId: ref("source-a"), currentDb: ref({ tid: "source-a" }) };
  vm.createContext(scope);
  vm.runInContext(`${declaration(registration, "next")}\nthis.nextStep = next;`, scope);
  assert.equal(await scope.nextStep(), undefined);
  scope.proxyDatasourceId.value = "";
  await assert.rejects(scope.nextStep(), /请先保存/);
});

test("proxy card detail reuses step five with its original source context", () => {
  const calls = [], routes = [], row = { tid: "source-a", accessMode: "proxy" };
  const scope = { hasReportFiles: () => false, isProxyDatasource: () => true, openRegisterModal: (value) => calls.push(value), router: { push: (value) => routes.push(value) } };
  vm.createContext(scope);
  vm.runInContext(`${declaration(list, "openDatasourceDetail")}\nthis.open = openDatasourceDetail;`, scope);
  scope.open(row);
  assert.equal(calls[0].registerData.currentStep, 4);
  assert.equal(calls[0].registerData.db[0], row);
  assert.ok(!('datasourceOptions' in calls[0].registerData));
  assert.equal(routes.length, 0);
});

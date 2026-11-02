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
const review = read(`${base}/04.登记内容审查(register-confirm).vue`);
const registration = read(`${base}/01.登记数据源(register-db).vue`);
const list = read(`${base}.vue`);

test("proxy service review remains visible outside connection and push-only panels", () => {
  const root = parse(review).descriptor.template.ast.children.find((node) => node.tag === "div");
  const serviceSection = root.children.find((node) => node.tag === "section" && node.props.some((prop) => prop.name === "if" && prop.exp?.content === "isProxyAccess"));
  assert.ok(serviceSection, "service review must be a direct child of the shared review page");
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

function serviceFixture(post) {
  const scope = {
    sourceTid: ref("source-a"), isProxyAccess: ref(true), proxyServiceKeyword: ref("服务"),
    proxyServiceTotal: ref(0), proxyServiceError: ref(""), proxyServiceRequestVersion: 0,
    $common: { post },
  };
  vm.createContext(scope);
  vm.runInContext(`${declaration(review, "fetchProxyServices")}\nthis.fetch = fetchProxyServices;`, scope);
  return scope;
}

for (const count of [20, 100]) test(`${count} review services use one source-scoped page request`, async () => {
  let requests = 0;
  const scope = serviceFixture(async (url, body) => {
    requests++;
    assert.equal(url, "/dws/flowserve/gateway/api/list");
    assert.equal(body.datasourceId, "source-a");
    assert.equal(body.pageSize, count);
    assert.equal(body.keyword, "服务");
    assert.equal(body.sortField, "serviceName");
    return { list: Array.from({ length: count }, (_, index) => ({ tid: `service-${index}` })), total: count };
  });
  const result = await scope.fetch({ pageNo: 1, pageSize: count, sortField: "serviceName", sortDir: "asc" });
  assert.equal(requests, 1);
  assert.equal(result.list.length, count);
  assert.equal(result.total, count);
});

test("late responses from another source cannot populate the current service list", async () => {
  let resolve;
  const scope = serviceFixture(() => new Promise((callback) => { resolve = callback; }));
  const pending = scope.fetch();
  scope.sourceTid.value = "source-b";
  resolve({ list: [{ tid: "old" }], total: 1 });
  assert.equal((await pending).list.length, 0);
  assert.equal(scope.proxyServiceTotal.value, 0);
});

test("service read failures expose a retryable error and recover without stale state", async () => {
  let failing = true;
  const scope = serviceFixture(async () => {
    if (failing) throw new Error("当前服务清单读取超时");
    return { list: [{ tid: "valid" }], total: 1 };
  });
  await scope.fetch();
  assert.match(scope.proxyServiceError.value, /读取超时/);
  failing = false;
  assert.equal((await scope.fetch()).list.length, 1);
  assert.equal(scope.proxyServiceError.value, "");
});

test("completion verifies actual services independently of an empty filtered table and preserves search", async () => {
  const scope = {
    sourceTid: ref("source-a"), source: ref({ tid: "source-a", dbName: "代理验证" }), isProxyAccess: ref(true),
    proxyServiceKeyword: ref("服务"), proxyServiceTotal: ref(0), sourceDetailRequestId: 0,
    refreshReviewSummary: async () => {}, ensureReady() {},
    $common: { post: async (_url, body) => {
      assert.equal(body.keyword, "");
      assert.equal(body.datasourceId, "source-a");
      return { list: [{ tid: "valid" }], total: 3 };
    } },
  };
  vm.createContext(scope);
  vm.runInContext(`${declaration(review, "finish")}\nthis.finishReview = finish;`, scope);
  assert.equal((await scope.finishReview())[0].assetStatus, 2);
  assert.equal(scope.proxyServiceTotal.value, 0);
  assert.equal(scope.proxyServiceKeyword.value, "服务");
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
  const scope = { hasReportFiles: () => false, isProxyDatasource: () => true, state: { list: [row] }, openRegisterModal: (value) => calls.push(value), router: { push: (value) => routes.push(value) } };
  vm.createContext(scope);
  vm.runInContext(`${declaration(list, "openDatasourceDetail")}\nthis.open = openDatasourceDetail;`, scope);
  scope.open(row);
  assert.equal(calls[0].registerData.currentStep, 4);
  assert.equal(calls[0].registerData.db[0], row);
  assert.equal(calls[0].registerData.datasourceOptions, scope.state.list);
  assert.equal(routes.length, 0);
});

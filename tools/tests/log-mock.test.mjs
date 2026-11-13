import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import { createRequire } from "node:module";
import { pathToFileURL } from "node:url";
import { ChengtianMockRuntime } from "../../mock/core/runtime.mjs";
import { createScenario, scenarios } from "../../mock/scenarios/index.mjs";
import { tenantConfiguration } from "../../mock/scenarios/configuration.mjs";
import { captureStatuses, logKinds } from "../../mock/scenarios/logs.mjs";
import { parseMockBody, mockResponse } from "../../mock/core/transport.mjs";
import { validateShape, checkFunctionDependency, repository } from "../check-mock-contracts.mjs";

const now = Date.parse("2026-11-13T08:00:00Z"), day = 86400000;
const range = { startTime: new Date(now - 89 * day).toISOString(), endTime: new Date(now).toISOString() };
function memory() {
  const values = new Map();
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key), values };
}
function setup(role = "admin", scenario = "police", storage = memory()) {
  let current = now;
  const runtime = new ChengtianMockRuntime({ role, scenario, storage, sessionStorage: memory(), clock: () => current });
  runtime.login(scenario);
  return { runtime, storage, advance: (value) => { current += value; } };
}
const call = (runtime, route, query = {}, body = {}, method = "GET") => runtime.handle({ path: route.startsWith("/") ? route : `/sym/logs/${route}`, method, query, body, token: runtime.session?.token || "" });
async function ok(runtime, route, query, body, method) {
  const response = await call(runtime, route, query, body, method);
  assert.equal(response.status, 200);
  assert.equal(response.body.code, 0, JSON.stringify(response.body));
  assert.equal(response.body.success, true);
  return response.body.data;
}
async function refused(runtime, route, code, query, body, method) {
  const response = await call(runtime, route, query, body, method);
  assert.equal(response.body.code, code);
  assert.equal(response.body.success, false);
  assert.equal(response.body.data, null);
  return response;
}
const seeds = (runtime, kind = "operations") => runtime.logs.rows(runtime.database).filter((row) => row.kind === kind);
async function all(runtime, kind, filter = {}) {
  let result = [];
  for (let page = 1; ; page++) {
    const data = await ok(runtime, kind, { ...range, ...filter, page, size: 100 });
    result.push(...data.list);
    if (result.length >= data.total) return result;
    assert(page < 60);
  }
}
async function exported(runtime, input) {
  const created = await ok(runtime, "exports", {}, input, "POST");
  assert.equal(created.status, "PENDING");
  let listing = await ok(runtime, "exports");
  assert.equal(listing.list.find((job) => job.tid === created.id).status, "RUNNING");
  listing = await ok(runtime, "exports");
  const job = listing.list.find((job) => job.tid === created.id);
  assert.equal(job.status, "SUCCESS");
  return { id: created.id, job, file: await call(runtime, `exports/${created.id}/download`) };
}

for (const code of Object.keys(scenarios)) {
  test(`${code}：每行业5760条虚构事实、不造配置，四模块20/100行各固定一次请求`, async () => {
    const { runtime } = setup("admin", code);
    const configBefore = structuredClone(tenantConfiguration(code));
    assert.equal(runtime.logs.rows(runtime.database).length, 5760);
    assert.deepEqual(await ok(runtime, "capabilities"), [...logKinds, "exports", "requestParameters"]);
    for (const kind of logKinds) {
      for (const size of [20, 100]) {
        const before = runtime.requests.length;
        const data = await ok(runtime, kind, { size });
        assert.equal(runtime.requests.length - before, 1);
        assert.equal(data.list.length, size);
        assert.equal(data.total, 105);
        assert.equal(data.page, 1); assert.equal(data.size, size);
        assert.equal(data.appliedScopeSummary, "ALL");
        assert(data.list.every((row) => row.actor_name && row.operation_name &&
          !["details", "items", "parameter_snapshot", "parameter_summary", "request_parameters", "parameter_search_text"].some((key) => key in row)));
        assert(data.list.every((row) => kind === "auth" ? !("parameter_capture_status" in row) : captureStatuses.includes(row.parameter_capture_status)));
        assert(JSON.stringify(data.list).length < size * 2200);
      }
    }
    assert.deepEqual(tenantConfiguration(code), configBefore);
    assert.deepEqual(runtime.database.organizations.map((row) => row.id), (configBefore.organizations || []).filter((row) => row.id !== "ROOT").map((row) => row.id));
  });
}

for (const role of ["admin", "department", "registrar"]) {
  test(`${role}：统计、详情、批量、明细逐对象范围校验且混越权整批拒绝`, async () => {
    const { runtime } = setup(role);
    for (const kind of logKinds) {
      const rows = await all(runtime, kind);
      assert(rows.length > 100);
      assert(rows.every((row) => role === "admin" || role === "department" ? role === "admin" || row.actor_org_id === runtime.session.orgId : row.actor_id === runtime.session.userId));
      const stat = await ok(runtime, `${kind}/statistics`, range);
      assert.equal(stat.total, rows.length);
      assert.equal(stat.success_count, rows.filter((row) => row.result === "SUCCESS").length);
      const narrowed = await ok(runtime, kind, { ...range, actorId: "not-current", orgId: "not-current", scope: "ALL", tenantId: "other" });
      assert.equal(narrowed.total, 0);
      for (const size of [20, 100]) {
        const ids = rows.slice(0, size).map((row) => row.tid);
        const before = runtime.requests.length;
        const batch = await ok(runtime, `${kind}/batch`, {}, ids, "POST");
        assert.equal(batch.length, size);
        assert.equal(runtime.requests.length - before, 1);
        assert(batch.every((row) => !("parameter_snapshot" in row)));
      }
      assert.equal((await ok(runtime, `${kind}/${rows.at(-1).tid}`)).tid, rows.at(-1).tid);
      assert.equal((await ok(runtime, `${kind}/${rows[0].tid}/items`, { size: 1 })).list.length, 1);
      if (role !== "admin") {
        const outside = seeds(runtime, kind).find((row) => role === "department" ? row.actor_org_id !== runtime.session.orgId : row.actor_id !== runtime.session.userId);
        await refused(runtime, `${kind}/${outside.tid}`, 403);
        await refused(runtime, `${kind}/${outside.tid}/items`, 403);
        await refused(runtime, `${kind}/batch`, 403, {}, [rows[0].tid, outside.tid], "POST");
      }
      await refused(runtime, `${kind}/batch`, 403, {}, [rows[0].tid, `broadcasting-${kind}-00001`], "POST");
      for (const ids of [[], [rows[0].tid, rows[0].tid], [null], [""], ["   "], Array.from({ length: 101 }, (_, i) => `${i}`)])
        await refused(runtime, `${kind}/batch`, 500, {}, ids, "POST");
    }
  });
}

test("无组织行业ORG不补权限；四行业切换、本人角色及模块撤权仍隔离", async () => {
  for (const code of ["broadcasting", "sea-fishery", "chengtian"]) {
    const { runtime } = setup("department", code);
    assert.equal(runtime.session.orgId, "");
    assert.equal((await ok(runtime, "operations", range)).total, 0);
    await refused(runtime, "operations/batch", 403, {}, [seeds(runtime)[0].tid], "POST");
  }
  const { runtime } = setup("registrar");
  const id = (await ok(runtime, "operations")).list[0].tid;
  await call(runtime, "/sym/tenant/switch", {}, { tenantId: tenantConfiguration("broadcasting").tenant.tid }, "POST");
  await refused(runtime, `operations/${id}`, 403);
  const rows = (await ok(runtime, "operations")).list;
  assert(rows.every((row) => row.actor_id === "broadcasting-user-0"));
  runtime.session.logCapabilities = ["auth", "exports"];
  assert.deepEqual(await ok(runtime, "capabilities"), ["auth", "exports"]);
  await refused(runtime, "operations", 403);
  await refused(runtime, "exports", 403, {}, { kind: "operations" }, "POST");
});

test("单完整关键词检索中文嵌套/数组/false/0/null/字面%_，不拆词、不匹配空占位", async () => {
  const { runtime } = setup();
  for (const kind of ["operations", "requests"]) {
    for (const parameterKeyword of ["中文嵌套", "混合数组", "中文数组", "false", "0", "null", "Literal%_Case", "literal%_case", "单完整字面 A B %_ !"]) {
      const filtered = await all(runtime, kind, { parameterKeyword });
      assert(filtered.length > 0, parameterKeyword);
      for (const row of filtered) {
        const value = await ok(runtime, `${kind}/${row.tid}`);
        const snapshot = value.parameter_snapshot;
        const content = { path: snapshot.path, query: snapshot.query, body: snapshot.body, verified: snapshot.verified };
        assert(JSON.stringify(content).toLowerCase().includes(parameterKeyword.toLowerCase()));
        assert(["CAPTURED", "PARTIAL"].includes(snapshot.status));
      }
    }
    assert.equal((await ok(runtime, kind, { ...range, parameterKeyword: "  " })).total, 0);
    assert.equal((await ok(runtime, kind, { ...range, parameterKeyword: "单完整字面 B A" })).total, 0);
    assert.equal((await ok(runtime, kind, { ...range, parameterKeyword: "%不会当通配符_" })).total, 0);
    assert.equal((await ok(runtime, kind, { ...range, parameterKeyword: "策略省略" })).total, 0);
    assert.equal((await ok(runtime, kind, { ...range, parameterKeyword: "EMPTY" })).total, 0);
    const filtered = await all(runtime, kind, { parameterKeyword: "Literal%_Case", keyword: "删除任务", result: "SUCCESS" });
    assert(filtered.length > 0);
    const statistics = await ok(runtime, `${kind}/statistics`, { ...range, parameterKeyword: "Literal%_Case", keyword: "删除任务", result: "SUCCESS" });
    assert.equal(statistics.total, filtered.length);
    assert.equal(statistics.success_count, filtered.length);
    const zero = await ok(runtime, kind, { ...range, parameterKeyword: "Literal%_Case", keyword: "不可能的普通关键词" });
    assert.equal(zero.total, 0);
    const exact = await all(runtime, kind, { parameterName: "dryRun", parameterValue: "false" });
    assert(exact.length > 0);
    const verified = await all(runtime, kind, { parameterName: "deletedTaskName", parameterValue: seeds(runtime, kind).find((row) => row.request_parameters.deletedTaskName)?.request_parameters.deletedTaskName[0] });
    assert(verified.length > 0);
    assert.equal((await ok(runtime, kind, { ...range, parameterName: "taskName", parameterValue: "字面%_" })).total, 0);
  }
});

test("旧精确条件拒绝敏感原文，不能在查询时掩码为可匹配安全值", async () => {
  const { runtime } = setup();
  for (const parameterValue of ["联系13800138000", "validation@example.test", "password=validation-value", "https%3A%2F%2Fu%3Ap%40host"]) {
    await refused(runtime, "operations", 500, { ...range, parameterName: "taskName", parameterValue });
  }
  const safe = await ok(runtime, "operations", { ...range, parameterName: "taskName", parameterValue: "联系138****8000" });
  assert.equal(safe.total, 0);
});

test("六态详情对象、任意安全JSON值、历史兼容以及dryRun/执行事实不伪造", async () => {
  const { runtime } = setup();
  for (const status of captureStatuses) {
    const row = seeds(runtime).find((row) => row.parameter_capture_status === status);
    const value = await ok(runtime, `operations/${row.tid}`);
    assert.equal(value.parameter_capture_status, status);
    assert.equal(value.parameter_snapshot.status, status);
    assert.deepEqual(Object.keys(value.parameter_snapshot), ["version", "status", "path", "query", "body", "verified", "notices"]);
    assert.equal(value.parameter_snapshot.version, 1);
    assert(Array.isArray(value.parameter_snapshot.notices));
    if (status === "LEGACY") { assert.equal(value.legacy, true); assert.equal(value.event_time, null); }
  }
  const bodies = [];
  for (const row of seeds(runtime).filter((row) => ["CAPTURED", "PARTIAL"].includes(row.parameter_capture_status)).slice(0, 60)) bodies.push((await ok(runtime, `operations/${row.tid}`)).parameter_snapshot.body);
  assert(bodies.some(Array.isArray)); assert(bodies.some((v) => v === false)); assert(bodies.some((v) => v === 0)); assert(bodies.some((v) => v === null)); assert(bodies.some((v) => typeof v === "string"));
  for (const row of seeds(runtime)) {
    assert.equal(row.success_count, row.items.filter((item) => item.result === "SUCCESS").length);
    assert.equal(row.failure_count, row.items.filter((item) => item.result === "FAILED").length);
    if (row.details.dryRun || !["SUCCESS", "PARTIAL"].includes(row.result)) {
      assert.equal(row.details.executed, false);
      assert.equal(row.request_parameters.deletedTaskName, undefined);
      assert.deepEqual(row.parameter_snapshot.verified, {});
    }
    if (row.request_parameters.deletedTaskId) {
      const successful = row.items.filter((item) => item.result === "SUCCESS");
      assert.deepEqual(row.request_parameters.deletedTaskId, successful.map((item) => item.target_id));
      assert.deepEqual(row.request_parameters.deletedTaskName, successful.map((item) => item.target_name));
      if (["CAPTURED", "PARTIAL"].includes(row.parameter_capture_status))
        assert.deepEqual(row.parameter_snapshot.verified.deletedTaskId, row.request_parameters.deletedTaskId);
    }
    if (row.details.dryRun) assert(row.items.every((item) => item.action_code === "TASK_DELETE_PRECHECK"));
  }
  assert(seeds(runtime).some((row) => row.details.executed && row.request_parameters.deletedTaskName));
  const failed = seeds(runtime, "requests").find((row) => row.result === "FAILED");
  assert.equal((await ok(runtime, `requests/${failed.tid}`)).http_status, 200);
  const content = JSON.stringify(runtime.logs.rows(runtime.database));
  assert(content.includes('"password":"[REDACTED]"'));
  assert(content.includes('"accessToken":"[REDACTED]"'));
  assert(!content.includes(runtime.session.token));
});

test("分页前过滤、大数据末页、稳定排序及90天/100行边界明确校验", async () => {
  const { runtime } = setup();
  const records = await all(runtime, "operations");
  assert.equal(records.length, 1335);
  assert.equal(new Set(records.map((row) => row.tid)).size, records.length);
  for (let i = 1; i < records.length; i++) {
    const left = records[i - 1], right = records[i], a = left.event_time ?? left.created_time, b = right.event_time ?? right.created_time;
    assert(a > b || a === b && left.tid < right.tid);
  }
  assert.equal((await ok(runtime, "operations", { ...range, page: 100, size: 100 })).list.length, 0);
  for (const filter of [{ size: 101 }, { size: 0 }, { page: 0 }, { page: 10001 }, { sortField: "password" }, { sortDir: "DESC" }, { startTime: "2026-01-01" }, { startTime: new Date(now - 91 * day).toISOString(), endTime: new Date(now).toISOString() }, { startTime: new Date(now).toISOString(), endTime: new Date(now).toISOString() }, { parameterName: "taskId" }, { parameterName: "password", parameterValue: "x" }, { parameterKeyword: "x".repeat(201) }, { result: "not-real" }]) {
    const error = await refused(runtime, "operations", 500, filter);
    assert.equal(error.body.error.code, "API-PARAM-001");
  }
  for (const kind of ["auth", "security"]) await refused(runtime, kind, 500, { parameterKeyword: "x" });
});

test("CSV/JSONL导出与全量匹配选择一致、状态真实推进、文件防公式且无快照泄露", async () => {
  const { runtime } = setup();
  const filter = { ...range, parameterKeyword: "Literal%_Case", keyword: "删除任务", result: "SUCCESS" };
  const expected = await all(runtime, "operations", filter);
  for (const format of ["CSV", "JSONL"]) {
    const { job, file } = await exported(runtime, { kind: "operations", format, ...filter, size: 1, page: 99 });
    assert.equal(job.row_count, expected.length);
    assert.equal(file.raw, true); assert.equal(file.status, 200);
    assert.equal(file.headers["Cache-Control"], "no-store");
    assert(!("content" in job)); assert(!("query" in job)); assert(!("scope" in job));
    if (format === "JSONL") {
      const rows = file.body.trim().split("\n").map(JSON.parse);
      assert.deepEqual(rows.map((row) => row.tid), [...expected].reverse().sort((a, b) => (a.event_time ?? a.created_time).localeCompare(b.event_time ?? b.created_time) || a.tid.localeCompare(b.tid)).map((row) => row.tid));
      assert(rows.every((row) => typeof row.parameter_snapshot === "object"));
    } else {
      assert(file.body.startsWith("\ufeff发生时间,"));
      assert.equal(file.body.split("\n").length - 2, expected.length);
      assert(file.body.includes('"\'=SUM(1,2)"'));
      assert(!file.body.includes("parameter_snapshot"));
    }
    const response = mockResponse(file);
    assert.equal(response.headers.get("content-disposition"), file.headers["Content-Disposition"]);
    assert.equal(response.headers.get("cache-control"), "no-store");
    assert.equal((await response.arrayBuffer()).byteLength, new TextEncoder().encode(file.body).byteLength);
  }
  const limited = await exported(runtime, { kind: "operations", format: "JSONL", ...range, rowLimit: 501 });
  assert.equal(limited.job.row_count, 501);
  const empty = await exported(runtime, { kind: "requests", parameterKeyword: "绝无匹配", format: "JSONL" });
  assert.equal(empty.job.row_count, 0); assert.equal(empty.file.body, "");
});

test("导出本人/租户/范围/权限/过期/摘要约束及失败状态不伪造", async () => {
  const { runtime, storage, advance } = setup();
  const pending = await ok(runtime, "exports", {}, { kind: "operations", password: "validation-unknown-sensitive-value", token: runtime.session.token }, "POST");
  assert(!storage.getItem(runtime.logs.key(runtime.database)).includes("validation-unknown-sensitive-value"));
  assert(!storage.getItem(runtime.logs.key(runtime.database)).includes(runtime.session.token));
  await refused(runtime, `exports/${pending.id}/download`, 500);
  const { id } = await exported(runtime, { kind: "requests", format: "JSONL" });
  runtime.session.scope = "OWNER";
  await refused(runtime, `exports/${id}/download`, 403);
  runtime.session.scope = "ALL";
  runtime.session.userId = "police-user-1";
  assert.equal((await ok(runtime, "exports")).total, 0);
  await refused(runtime, `exports/${id}/download`, 403);
  runtime.session.userId = "police-user-0";
  const key = runtime.logs.key(runtime.database), state = JSON.parse(storage.getItem(key));
  state.jobs.find((job) => job.tid === id).content += "tampered";
  storage.setItem(key, JSON.stringify(state));
  await refused(runtime, `exports/${id}/download`, 500);
  const another = await ok(runtime, "exports", {}, { kind: "requests" }, "POST");
  runtime.session.logCapabilities = ["exports", "auth"];
  const failed = (await ok(runtime, "exports")).list.find((row) => row.tid === another.id);
  assert.equal(failed.status, "FAILED"); assert.equal(failed.last_error_code, "PERMISSION_CHANGED");
  runtime.session.logCapabilities = [...logKinds, "exports"];
  advance(25 * 3600000);
  await refused(runtime, `exports/${id}/download`, 500);
  assert.equal((await ok(runtime, "exports")).list.find((row) => row.tid === id).status, "EXPIRED");
  await call(runtime, "/sym/tenant/switch", {}, { tenantId: tenantConfiguration("chengtian").tenant.tid }, "POST");
  assert.equal((await ok(runtime, "exports")).total, 0);
  await refused(runtime, `exports/${id}/download`, 403);
});

test("未知动作/归档/策略/平台路径拒绝、无真实网络访问、不记录正文和会话", async () => {
  const { runtime } = setup();
  const previous = globalThis.fetch;
  globalThis.fetch = () => { throw new Error("不允许真实网络"); };
  try {
    for (const [route, method, body, query] of [["archives", "GET"], ["archives", "POST", { kind: "auth" }], ["archives/not-real", "POST", { action: "purge" }], ["policies", "PUT", {}], ["operations", "POST", {}], ["requests", "GET", {}, { action: "list" }], ["exports/invalid/download", "GET"], ["exports", "DELETE"], ["/idaas/logs/query", "GET"]]) {
      const response = await call(runtime, route, query, body, method);
      assert.equal(response.status, 501); assert.match(response.body.message, /阻止访问真实服务/);
    }
    await ok(runtime, "requests", { size: 20 });
  } finally { globalThis.fetch = previous; }
  assert(runtime.requests.every((row) => !row.includes(runtime.session.token)));
  const unauthenticated = await runtime.handle({ path: "/sym/logs/auth", token: "not-current" });
  assert.equal(unauthenticated.status, 401);
});

test("根传输适配保留裸批量数组与日志响应，其他路由仍拒绝数组", async () => {
  const { runtime } = setup();
  const id = (await ok(runtime, "auth")).list[0].tid;
  const body = parseMockBody("/dev-api/sym/logs/auth/batch", JSON.stringify([id]));
  assert.deepEqual(body, [id]);
  const result = await call(runtime, "auth/batch", {}, body, "POST");
  assert.equal((await mockResponse(result).json()).data[0].tid, id);
  assert.throws(() => parseMockBody("/sym/logs/exports", "[]"));
  assert.throws(() => parseMockBody("/sym/logs/auth/batch", "null"));
  assert.throws(() => parseMockBody("/sym/logs/auth/batch", "invalid"));
});

test("统一浏览器宿主实际复用传输入口，MSW保持批量、错误状态及CSV/JSONL原始字节", async () => {
  const resolveFromApp = createRequire(new URL("../../data-elements-chengtian/package.json", import.meta.url));
  const { HttpResponse } = await import(pathToFileURL(resolveFromApp.resolve("msw/http")).href);
  const source = await fs.readFile(new URL("../../data-elements-chengtian/src/mock/browser.ts", import.meta.url), "utf8");
  assert.match(source, /parseMockBody\(path, await request\.text\(\)\)/);
  assert.match(source, /mockResponse\(result, HttpResponse\)/);
  assert.doesNotMatch(source, /JSON\.parse\(|HttpResponse\.json\(result/);
  const { runtime } = setup();
  for (const prefix of ["", "/dev-api", "/prod-api"]) {
    const ids = seeds(runtime).slice(0, 100).map((row) => row.tid);
    const body = parseMockBody(`${prefix}/sym/logs/operations/batch`, JSON.stringify(ids));
    const response = mockResponse(await call(runtime, "operations/batch", {}, body, "POST"), HttpResponse);
    assert(response instanceof HttpResponse);
    assert.equal((await response.json()).data.length, 100);
  }
  for (const format of ["CSV", "JSONL"]) {
    const { file } = await exported(runtime, { kind: "operations", format, rowLimit: 2 });
    const response = mockResponse(file, HttpResponse);
    assert.deepEqual(new Uint8Array(await response.arrayBuffer()), new TextEncoder().encode(file.body));
    assert.equal(response.headers.get("content-type"), file.headers["Content-Type"]);
    assert.equal(response.headers.get("content-disposition"), file.headers["Content-Disposition"]);
    assert.equal(response.headers.get("cache-control"), "no-store");
  }
  const error = mockResponse({ status: 403, body: { code: 403 }, headers: { "Cache-Control": "no-store" } }, HttpResponse);
  assert.equal(error.status, 403); assert.deepEqual(await error.json(), { code: 403 });
  assert.equal(error.headers.get("cache-control"), "no-store");
});

test("参数关键词原样保留空白、UTF16长度200边界与类型，列表/统计/导出共选择", async () => {
  const { runtime } = setup();
  const row = seeds(runtime).find((row, i) => i > 300 && row.parameter_capture_status === "CAPTURED");
  for (const literal of ["  ", "  Literal%_Case  ", "x".repeat(200), "😀".repeat(100)]) {
    row.parameter_snapshot.body = { validation: literal };
    const filter = { ...range, parameterKeyword: literal, size: 1 };
    const listed = await ok(runtime, "operations", filter);
    assert.equal(listed.total, 1); assert.equal(listed.list[0].tid, row.tid);
    assert.equal((await ok(runtime, "operations/statistics", filter)).total, 1);
    const output = await exported(runtime, { kind: "operations", format: "JSONL", ...filter });
    assert.equal(output.job.row_count, 1); assert.equal(JSON.parse(output.file.body).tid, row.tid);
  }
  const baseline = (await ok(runtime, "operations", range)).total;
  for (const parameterKeyword of [undefined, null, ""])
    assert.equal((await ok(runtime, "operations", { ...range, parameterKeyword })).total, baseline);
  for (const parameterKeyword of [false, 0, {}, [], "x".repeat(201), "😀".repeat(101), " ".repeat(201)]) {
    await refused(runtime, "operations", 500, { parameterKeyword });
    await refused(runtime, "operations/statistics", 500, { parameterKeyword });
    await refused(runtime, "exports", 500, {}, { kind: "operations", parameterKeyword }, "POST");
  }
  for (const kind of ["auth", "security"]) await refused(runtime, kind, 500, { parameterKeyword: "  " });
});

test("参数条件在OWNER/ORG范围及分页之前，不因客户端租户/范围扩大权限", async () => {
  for (const role of ["department", "registrar"]) {
    const { runtime } = setup(role), values = seeds(runtime);
    const allowed = values.findLast((row) => (role === "department" ? row.actor_org_id === runtime.session.orgId : row.actor_id === runtime.session.userId) && row.event_time && Date.parse(row.event_time) >= now - 89 * day);
    const outside = values.find((row) => role === "department" ? row.actor_org_id !== runtime.session.orgId : row.actor_id !== runtime.session.userId);
    for (const row of [allowed, outside]) {
      row.parameter_snapshot.status = "CAPTURED";
      row.parameter_snapshot.body = { validation: "唯一完整范围关键词 %_!" };
    }
    const filter = { ...range, parameterKeyword: "唯一完整范围关键词 %_!", scope: "ALL", tenantId: "other", size: 1 };
    assert.deepEqual((await ok(runtime, "operations", filter)).list.map((row) => row.tid), [allowed.tid]);
    assert.equal((await ok(runtime, "operations/statistics", filter)).total, 1);
    assert.equal((await exported(runtime, { kind: "operations", format: "JSONL", ...filter })).job.row_count, 1);
  }
});

test("时间按同一时刻比较、严格日期和纳秒半开边界，双空历史时间不入选", async () => {
  const { runtime } = setup();
  runtime.logs.seeds.set(runtime.database.code, seeds(runtime).slice(0, 5));
  const rows = seeds(runtime);
  rows.forEach((row, i) => { row.event_time = [`2026-11-13T07:59:59.999999999Z`, `2026-11-13T08:00:00Z`, `2026-11-13T16:00:00.000000001+08:00`, null, `2026-11-13T16:00:00+08:00`][i]; row.created_time = null; });
  const filter = { startTime: "2026-11-13T16:00:00+08:00", endTime: "2026-11-13T08:00:00.000000001Z", sortDir: "asc" };
  assert.deepEqual((await ok(runtime, "operations", filter)).list.map((row) => row.tid), [rows[1].tid, rows[4].tid]);
  assert.equal((await ok(runtime, "operations", { startTime: " ", endTime: "\t" })).total, 4);
  for (const endTime of ["2026-02-30T00:00:00Z", "2026-11-13T24:01:00Z", "2026-11-13T08:00:00", "2026-11-13T08:00:00+18:01"])
    await refused(runtime, "operations", 500, { endTime });
  await ok(runtime, "operations", { startTime: "2026-08-15T08:00:00Z", endTime: "2026-11-13T08:00:00Z" });
  await refused(runtime, "operations", 500, { startTime: "2026-08-15T07:59:59.999999999Z", endTime: "2026-11-13T08:00:00Z" });
});

test("导出冻结的是时间/接收截止而非ID事务快照，默认起点不随执行漂移", async () => {
  const { runtime, advance } = setup();
  const rows = seeds(runtime).slice(0, 8);
  runtime.logs.seeds.set(runtime.database.code, rows);
  const times = [now - 7 * day, now - 7 * day - 1, now, now - 1, now - 2, now - 3, now + 1, now - 4];
  rows.forEach((row, i) => { row.event_time = new Date(times[i]).toISOString(); row.received_time = new Date(now).toISOString(); });
  rows[4].received_time = new Date(now + 1).toISOString(); rows[5].received_time = null;
  rows.pop(); // 此记录提交后才出现，但时间和接收截止均满足，真实条件仍允许入选。
  const late = seeds(runtime)[0];
  const added = { ...structuredClone(late), tid: "validation-late-visible", event_time: new Date(now - 4).toISOString(), received_time: null };
  const created = await ok(runtime, "exports", {}, { kind: "operations", format: "JSONL", endTime: new Date(now + day).toISOString() }, "POST");
  rows.push(added);
  advance(2 * day);
  await ok(runtime, "exports"); const listing = await ok(runtime, "exports");
  assert.equal(listing.list.find((job) => job.tid === created.id).status, "SUCCESS");
  const file = await call(runtime, `exports/${created.id}/download`);
  assert.deepEqual(new Set(file.body.trim().split("\n").map((line) => JSON.parse(line).tid)), new Set([rows[0].tid, rows[3].tid, rows[5].tid, added.tid]));
  const earlier = await exported(runtime, { kind: "operations", format: "JSONL", startTime: new Date(now - day).toISOString(), endTime: new Date(now - 3).toISOString() });
  assert.deepEqual(earlier.file.body.trim().split("\n").map((line) => JSON.parse(line).tid), [added.tid]);
});

test("CSV防前导空白公式注入，控制字符清洗/引号转义与历史时间不降级", async () => {
  const { runtime } = setup();
  const rows = seeds(runtime).slice(0, 7);
  runtime.logs.seeds.set(runtime.database.code, rows);
  const accounts = [" =1+1", "\t+SUM(1,2)", "\n@cmd", "\r-1+1", '  普通"中文"', "\u00a0=1", "\u0000=1"];
  rows.forEach((row, i) => { row.actor_account = accounts[i]; });
  rows[0].event_time = null;
  const output = await exported(runtime, { kind: "operations", format: "CSV" });
  const [, ...csv] = csvRows(output.file.body);
  for (const row of csv) {
    const original = rows.find((value) => value.request_id === row[10]);
    const cleaned = original.actor_account.replace(/[\x00-\x1f\x7f]/g, " ");
    assert.equal(row[2], /^\s*[=+@-]/.test(cleaned) ? `'${cleaned}` : cleaned);
    assert.equal(row[0], original.event_time ?? original.created_time);
  }
  assert.equal(csv.length, 7);
});

test("共享Magic函数依赖由检查器实际校验摘要、身份和路径，不增加HTTP项", async () => {
  const spec = JSON.parse(await fs.readFile(new URL("../../mock/contracts/chengtian.json", import.meta.url), "utf8"));
  const dependency = spec.dependencies.find((entry) => entry.source.id === "log_parameter_condition_v1");
  assert(dependency.operations.includes("GET /sym/logs/operations"));
  assert(dependency.operations.includes("POST /sym/logs/exports"));
  assert.deepEqual(await checkFunctionDependency(repository, dependency), []);
  for (const change of [{ reviewedSha256: "0".repeat(64) }, { id: "wrong-id" }, { path: "/wrong-path" }, { file: "magic/function/../../AGENTS.md" }])
    assert((await checkFunctionDependency(repository, { ...dependency, source: { ...dependency.source, ...change } })).length > 0);
  assert.equal(spec.interfaces.length, 56);
});

test("含引号/反斜杠的完整字面在分页之前检索，脱敏占位与历史不伪造新搜索内容", async () => {
  const { runtime } = setup();
  const row = seeds(runtime).find((row, i) => i > 200 && row.parameter_capture_status === "CAPTURED");
  const literal = '中文"引号" C:\\验证\\路径 %_';
  row.parameter_snapshot.body = { "名称": literal, "凭据": "[REDACTED]", "省略": "[OMITTED]" };
  const filtered = await ok(runtime, "operations", { ...range, parameterKeyword: literal, size: 1 });
  assert.equal(filtered.total, 1); assert.equal(filtered.list[0].tid, row.tid);
  assert.equal((await ok(runtime, "operations/statistics", { ...range, parameterKeyword: literal })).total, 1);
  const output = await exported(runtime, { kind: "operations", ...range, parameterKeyword: literal, format: "JSONL" });
  assert.equal(output.job.row_count, 1);
  assert.equal(JSON.parse(output.file.body).parameter_snapshot.body["名称"], literal);
  for (const parameterKeyword of ["[REDACTED]", "[OMITTED]"])
    assert.equal((await ok(runtime, "operations", { ...range, parameterKeyword })).total, 0);
  const legacy = seeds(runtime).find((value) => value.parameter_capture_status === "LEGACY");
  assert.equal((await ok(runtime, "operations", { ...range, traceId: legacy.trace_id, parameterKeyword: legacy.request_parameters.taskId[0] })).total, 0);
});

test("旧白名单布尔大小写与完整值精确匹配；旧新条件仍AND", async () => {
  const { runtime } = setup();
  for (const parameterName of ["dryRun", "batchDryRun", "deleteTargetTable", "confirmUnlinkedTarget", "checkPhysicalExistence"]) {
    await ok(runtime, "operations", { parameterName, parameterValue: "true" });
    await ok(runtime, "operations", { parameterName, parameterValue: "false" });
    for (const parameterValue of ["TRUE", "False", "1", "0"])
      await refused(runtime, "operations", 500, { parameterName, parameterValue });
  }
  const filter = { ...range, parameterName: "dryRun", parameterValue: "false", parameterKeyword: "Literal%_Case" };
  const rows = await all(runtime, "operations", filter);
  assert(rows.length > 0);
  for (const row of rows) assert.equal((await ok(runtime, `operations/${row.tid}`)).request_parameters.dryRun, "false");
  assert.equal((await ok(runtime, "operations", { ...filter, parameterKeyword: "绝不匹配" })).total, 0);
});

test("异步文件摘要期间并发提交不丢任务，同一Storage跨runtime也串行", async () => {
  const { runtime, storage } = setup();
  const first = await ok(runtime, "exports", {}, { kind: "operations" }, "POST");
  await ok(runtime, "exports");
  const secondRuntime = setup("admin", "police", storage).runtime;
  const [, second] = await Promise.all([ok(runtime, "exports"), ok(secondRuntime, "exports", {}, { kind: "requests" }, "POST")]);
  const listing = await ok(runtime, "exports");
  assert.equal(listing.total, 2);
  assert.equal(listing.list.find((job) => job.tid === first.id).status, "SUCCESS");
  assert(listing.list.some((job) => job.tid === second.id));
});

function csvRows(content) {
  return content.slice(1).trimEnd().split("\n").map((line) => {
    const cells = []; let cell = "", quoted = false;
    for (let i = 0; i < line.length; i++) {
      const char = line[i];
      if (char === '"') {
        if (quoted && line[i + 1] === '"') { cell += '"'; i++; }
        else quoted = !quoted;
      } else if (char === "," && !quoted) { cells.push(cell); cell = ""; }
      else cell += char;
    }
    cells.push(cell); return cells;
  });
}

test("ORG/OWNER导出与列表一致，CSV历史时间及实际字段不丢失", async () => {
  for (const role of ["department", "registrar"]) {
    const { runtime } = setup(role);
    const visible = await all(runtime, "operations");
    const byRequest = new Map(visible.map((row) => [row.request_id, row]));
    const output = await exported(runtime, { kind: "operations", ...range, format: "CSV" });
    const [header, ...rows] = csvRows(output.file.body);
    assert.equal(header.length, 12); assert.equal(rows.length, visible.length);
    let legacy = 0;
    for (const cells of rows) {
      const expected = byRequest.get(cells[10]); assert(expected);
      assert.equal(cells.length, 12);
      assert.equal(cells[0], expected.event_time ?? expected.created_time);
      assert.equal(cells[1], expected.actor_name);
      assert.equal(cells[2], expected.actor_account.startsWith("=") ? `'${expected.actor_account}` : expected.actor_account);
      assert.equal(cells[7], expected.target_name); assert.equal(cells[8], expected.result);
      if (expected.legacy) legacy++;
    }
    assert(legacy > 0);
    const json = await exported(runtime, { kind: "operations", ...range, format: "JSONL" });
    assert.equal(json.job.row_count, visible.length);
    assert.deepEqual(new Set(json.file.body.trim().split("\n").map((line) => JSON.parse(line).tid)), new Set(visible.map((row) => row.tid)));
  }
});

test("日志25项HTTP契约按真实实现登记且列表/详情/统计/batch/文件形状可验证", async () => {
  const spec = JSON.parse(await fs.readFile(new URL("../../mock/contracts/chengtian.json", import.meta.url), "utf8"));
  const contracts = spec.interfaces.filter((row) => row.operation.includes("/sym/logs/"));
  assert.equal(contracts.length, 25);
  assert(contracts.every((row) => row.source.state === "pending-magic"));
  const { runtime } = setup();
  const fixtures = [["GET /sym/logs/capabilities", {}, await ok(runtime, "capabilities")]];
  for (const kind of logKinds) {
    const listed = await ok(runtime, kind), id = listed.list[0].tid;
    fixtures.push([`GET /sym/logs/${kind}`, {}, listed], [`GET /sym/logs/${kind}/statistics`, {}, await ok(runtime, `${kind}/statistics`)],
      [`GET /sym/logs/${kind}/{id}`, { id }, await ok(runtime, `${kind}/${id}`)], [`POST /sym/logs/${kind}/batch`, [id], await ok(runtime, `${kind}/batch`, {}, [id], "POST")],
      [`GET /sym/logs/${kind}/{id}/items`, { id }, await ok(runtime, `${kind}/${id}/items`)]);
  }
  for (const [operation, input, data] of fixtures) {
    const contract = contracts.find((row) => row.operation === operation);
    validateShape(input, contract.request); validateShape(data, contract.response);
  }
});

import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import { ChengtianMockRuntime } from "../../mock/core/runtime.mjs";
import { createScenario, scenarios } from "../../mock/scenarios/index.mjs";
import { paginate } from "../../mock/core/data.mjs";
import { configuration, tenantConfiguration } from "../../mock/scenarios/configuration.mjs";
import { tableRows } from "../../mock/core/metadata.mjs";
import { mockResponse } from "../../mock/core/transport.mjs";
import { checkContracts, validateShape } from "../check-mock-contracts.mjs";

const memory = () => {
  const values = new Map();
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
    values,
  };
};
const request = (runtime, path, body = {}, query = {}, method = "POST") =>
  runtime.handle({
    path,
    body,
    query,
    method,
    token: runtime.session?.token || "",
  });
function setup(role = "admin", code = "police") {
  const storage = memory(),
    sessionStorage = memory();
  const runtime = new ChengtianMockRuntime({
    scenario: code,
    role,
    storage,
    sessionStorage,
  });
  runtime.login(code);
  return { runtime, storage, sessionStorage };
}

test("澄天契约指向真实实现及当前审阅版本", async () => {
  const result = await checkContracts();
  assert.equal(result.interfaces, 88);
  assert.deepEqual(result.errors, []);
});

test("采集启动预检、旧 POST 地址、任务轮询和改名拒绝使用同一来源范围", async () => {
  const { runtime } = setup("registrar");
  const own = runtime.database.sources.find((row) => row.createdBy === runtime.session.userId);
  const other = runtime.database.sources.find((row) => row.createdBy !== runtime.session.userId);
  const current = "/dst/collect/start";
  const legacy = "/dst/database/metadata/tables/collection/start";
  const preview = { dbId: own.tid, mode: "REEXPLORE", dryRun: true };
  assert.deepEqual(await request(runtime, legacy, preview), await request(runtime, current, preview));
  assert.equal(runtime.database.collectionJobs, undefined);
  assert.equal((await request(runtime, current, { dbId: other.tid })).body.code, 500);
  const result = (await request(runtime, current, { dbId: own.tid, mode: "REFRESH" })).body.data;
  assert.equal(result.phase, "QUEUED");
  const status = (await request(runtime, "/dst/collect/status", {}, { dbId: own.tid, jobId: result.jobId }, "GET")).body.data;
  assert.equal(status.status, "SUCCEEDED");
  assert.equal(status.jobId, result.jobId);
  const table = runtime.database.tables.find((row) => row.datasourceId === own.tid);
  const rename = { dbId: own.tid, tableId: table.tid, tableName: "validation-retained" };
  const expected = await request(runtime, "/dst/collect/rename", rename);
  assert.equal(expected.body.msg, "该表不在当前变更列表中，请重新探查后再修改");
  assert.deepEqual(await request(runtime, "/dst/database/metadata/tables/collection/rename-missing", rename), expected);
  assert.equal((await request(runtime, "/dst/collect/rename", { ...rename, dbId: other.tid })).body.msg,
    "登记表不存在或无访问权限");
});

test("采集快照和审查顺序使用同一来源，分页、越权与兼容方法遵守旧契约", async () => {
  const { runtime } = setup("registrar");
  const own = runtime.database.sources.find((row) => row.createdBy === runtime.session.userId && row.annotatedTableNum < 6);
  const other = runtime.database.sources.find((row) => row.createdBy !== runtime.session.userId);
  const get = (path, query) => request(runtime, path, {}, query, "GET");
  const snapshot = await get("/dst/collect/snapshot", { dbId: own.tid, pageSize: "2", offset: "1" });
  assert.equal(snapshot.body.code, 0);
  assert.equal(snapshot.body.data.offset, 1);
  assert.equal(snapshot.body.data.physicalTotal, 6);
  assert.deepEqual(await get("/dst/database/metadata/tables/snapshot", { "dbId[]": own.tid, pageSize: "2", offset: "1" }), snapshot);
  assert.equal((await get("/dst/collect/snapshot", { dbId: own.tid, offset: "n/a" })).body.code, 20002);
  assert.equal((await get("/dst/collect/snapshot", { dbId: other.tid, "dbId[]": own.tid })).body.code, 500);
  const changes = await get("/dst/collect/changes", { dbId: own.tid, pageSize: 100 });
  assert.deepEqual(changes.body.data, { pageNo: 1, pageSize: 100, total: 0, rows: [], list: [] });
  assert.equal((await request(runtime, "/dst/collect/changes", {}, { dbId: own.tid }, "HEAD")).body, null);

  const full = await request(runtime, "/dst/collect/order", { dbId: own.tid, allTables: true });
  assert.equal(full.body.data.total, 6);
  const names = full.body.data.keys.slice(0, 2);
  const only = { dbId: own.tid, names: names.toReversed() };
  const ordered = await request(runtime, "/dst/collect/order", only);
  assert.deepEqual(ordered.body.data, { total: 2, keys: names });
  assert.deepEqual(await request(runtime, "/dst/database/metadata/tables/review-order", only), ordered);
  const refused = await request(runtime, "/dst/collect/order", { dbId: own.tid, names: [names[0], "validation-missing"] });
  assert.equal(refused.body.code, 500);
  assert.equal((await request(runtime, "/dst/collect/order", { ...only, dbId: other.tid })).body.code, 500);
});

test("采集进度兼容原地址与 HEAD，并按当前数据源范围读取快照", async () => {
  const { runtime } = setup("registrar");
  const path = "/dst/collect/status";
  const old = "/dst/database/metadata/tables/collection/status";
  const own = runtime.database.sources.find((row) => row.createdBy === runtime.session.userId
    && runtime.database.tables.some((table) => table.datasourceId === row.tid));
  const other = runtime.database.sources.find((row) => row.createdBy !== runtime.session.userId);
  const get = (url, query) => request(runtime, url, {}, query, "GET");
  const result = await get(path, { dbId: own.tid, includeDeletedDetails: "on" });
  assert.equal(result.body.data.status, "SUCCEEDED");
  assert.equal(result.body.data.datasourceId, own.tid);
  assert.deepEqual(result.body.data.deleted, []);
  assert.deepEqual(await get(old, { "dbId[]": own.tid, includeDeletedDetails: "on" }), result);
  assert.equal((await get(path, { dbId: other.tid, "dbId[]": own.tid })).body.data.status, "NOT_COLLECTED");
  assert.equal((await get(path, { dbId: own.tid, jobId: "validation-unknown" })).body.data.status, "NOT_COLLECTED");
  assert.equal((await get(path, {})).body.code, 20001);
  assert.equal((await get(path, { dbId: own.tid, includeDeletedDetails: "wrong" })).body.code, 20002);
  const head = await request(runtime, old, {}, { dbId: own.tid }, "HEAD");
  assert.equal(head.status, 200);
  assert.equal(head.body, null);
});

test("所属系统选择项一次返回完整租户角色范围，保留线上字段和浏览器搜索所需候选", async () => {
  const route = "/dst/application/options";
  const contract = JSON.parse(await fs.readFile(new URL("../../mock/contracts/chengtian.json", import.meta.url), "utf8"));
  const schema = contract.interfaces.find((item) => item.operation === `POST ${route}`).response;
  for (const role of ["admin", "department", "registrar"]) {
    const { runtime } = setup(role);
    const orgId = runtime.session.orgId;
    const tenantId = runtime.session.tenantId;
    const row = (tid, appName, extra = {}) => ({ tid, appName, tenantId, orgId, ...extra });
    runtime.database.applications = [
      row("validation-z", "同名应用", { isDel: 0, createdBy: "validation-owner" }),
      row("validation-a", "同名应用", { isDel: null, createdBy: "validation-other" }),
      row("validation-null", null),
      row("validation-outside", "另一部门", { orgId: "outside-org" }),
      row("validation-deleted", "删除系统", { isDel: 1 }),
      row("validation-foreign", "跨租户", { tenantId: "foreign-tenant" }),
    ];
    runtime.persist(runtime.database);

    const before = runtime.requests.length;
    const actual = (await request(runtime, route)).body.data;
    assert.equal(runtime.requests.length - before, 1);
    validateShape(actual, schema);
    assert.deepEqual((await request(runtime, "/dst/application/register-options")).body.data, actual);
    assert.deepEqual(actual[0], { label: null, value: "validation-null", orgid: orgId, createdby: null });
    assert.deepEqual(actual.filter((item) => item.label === "同名应用"), [
      { label: "同名应用", value: "validation-a", orgid: orgId, createdby: "validation-other" },
      { label: "同名应用", value: "validation-z", orgid: orgId, createdby: "validation-owner" },
    ]);
    assert.equal(actual.some((item) => item.value === "validation-outside"), role === "admin");
    assert(actual.every((item) => !["validation-foreign", "validation-deleted"].includes(item.value)));
    const spoofed = (await request(runtime, route, {
      keyword: "缺少的系统", limit: 1, tenantId: "foreign-tenant", orgId: "outside-org", scope: "ALL",
    })).body.data;
    assert.deepEqual(spoofed, actual);
  }
  const { runtime } = setup("registrar");
  runtime.session.orgId = "";
  assert.deepEqual((await request(runtime, route)).body.data, []);
});

test("元数据探查保持独立读范围，刷新按父源角色校验并仅正式保存概览", async () => {
  const { runtime, storage } = setup("registrar");
  const tenant = runtime.session.tenantId;
  const sources = runtime.database.sources;
  const own = sources.find((row) => row.createdBy === runtime.session.userId);
  const other = sources.find((row) => row.createdBy !== runtime.session.userId);
  const table = runtime.database.tables.find((row) => row.datasourceId === own.tid && row.tenantId === tenant);
  const sourceBefore = structuredClone(own);
  const tableBefore = structuredClone(table);
  const probe = "/dst/db/info";
  const refresh = "/dst/db/refresh";
  const get = (path, query) => request(runtime, path, {}, query, "GET");

  assert.equal((await get(probe, { tid: other.tid })).status, 200);
  assert.equal((await get(refresh, { tid: other.tid, dryRun: "true" })).body.code, 403);
  assert.equal((await get(refresh, { tid: other.tid, tableId: table.tid })).body.message, "数据表不属于指定数据源");
  assert.equal((await get(refresh, { tid: own.tid, tableId: "unknown" })).body.code, 404);
  assert.equal((await get(refresh, {})).body.code, 400);
  const dryTable = await get(refresh, { tid: own.tid, tableId: table.tid, dryRun: true });
  assert.equal(dryTable.body.data.fieldCount, table.fieldCount);
  assert.equal(dryTable.body.data.ddlCreateTime, null);
  assert.equal(storage.values.size, 0);

  assert.deepEqual((await get(refresh, { tid: own.tid, tableId: table.tid })).body.data, dryTable.body.data);
  const savedTable = runtime.database.tables.find((row) => row.tid === table.tid);
  assert.equal(savedTable.createdBy, tableBefore.createdBy);
  assert.equal(savedTable.tableComment, tableBefore.tableComment);
  assert.notEqual(savedTable.updatedTime, tableBefore.updatedTime);
  const savedSource = (await get(refresh, { tid: own.tid })).body.data;
  assert.equal(savedSource.tid, own.tid);
  assert.equal(savedSource.table_num, savedSource.tableCount);
  assert.equal(savedSource.procedure_count, null);
  assert.equal(runtime.database.sources.find((row) => row.tid === own.tid).createdBy, sourceBefore.createdBy);
});

for (const code of Object.keys(scenarios)) {
  test(`${code}：场景关联完整且 20/100 行均为一次列表请求`, async () => {
    const { runtime } = setup("admin", code),
      data = runtime.database;
    assert.equal(data.sources.length, 120);
    assert.equal(data.tables.length, 720);
    assert(
      data.tables.every((row) =>
        data.sources.some(
          (source) =>
            source.tid === row.datasourceId && row.tenantId === source.tenantId,
        ),
      ),
    );
    for (const size of [20, 100]) {
      const before = runtime.requests.length;
      const result = await request(
        runtime,
        "/dst/db/dept",
        { mode: "cards", pageNum: 1, pageSize: size },
      );
      assert.equal(runtime.requests.length - before, 1);
      assert.equal(result.status, 200);
      assert.equal(result.body.data.list.length, size);
      assert.equal(result.body.data.total, 120);
      assert(
        result.body.data.list.every(
          (row) =>
            row.dbName &&
            row.appName &&
            (row.orgName || !data.organizations.length) &&
            row.createdBy &&
            !("poolCfg" in row),
        ),
      );
    }
  });
}

for (const role of ["admin", "department", "registrar"]) {
  test(`${role}：分页、统计、详情和写入使用相同数据范围`, async () => {
    const { runtime } = setup(role);
    const page = (
      await request(runtime, "/dst/db/srcpage", { pageSize: 100 })
    ).body.data;
    assert.equal(page.total, role === "admin" ? 120 : 40);
    const facets = (
      await request(runtime, "/dst/db/stats", { mode: "facets" })
    ).body.data;
    assert.equal(facets.total, page.total);
    const allowed = page.list[0];
    assert.equal(
      (await request(runtime, "/dst/db/srcdetail", { tid: allowed.tid }))
        .status,
      200,
    );
    const narrowed = (
      await request(runtime, "/dst/db/srcpage", {
        pageSize: 100,
        tenantId: tenantConfiguration("broadcasting").tenant.tid,
        orgIds: ["police-org-1"],
      })
    ).body.data;
    assert(
      narrowed.list.every((row) => row.tenantId === runtime.session.tenantId),
    );
    if (role !== "admin") {
      const forbidden = runtime.database.sources.find((row) =>
        role === "department"
          ? row.orgId !== runtime.session.orgId
          : row.createdBy !== runtime.session.userId,
      );
      assert.equal(
        (await request(runtime, "/dst/db/srcdetail", { tid: forbidden.tid }))
          .status,
        404,
      );
      assert.equal(
        (
          await request(runtime, "/dst/db/srcsave", {
            tid: forbidden.tid,
            propList: { dbName: "无权修改" },
          })
        ).status,
        403,
      );
      assert.equal(
        (
          await request(runtime, "/dst/db/srcdel", {
            tid: [allowed.tid, forbidden.tid],
          })
        ).status,
        404,
      );
      assert.equal(
        runtime.database.sources.find((row) => row.tid === allowed.tid).isDel,
        0,
      );
    }
    assert.equal(
      (
        await request(runtime, "/dst/db/srcdetail", {
          tid: "broadcasting-source-001",
        })
      ).status,
      404,
    );
  });
}

test("编辑保存、分页与详情回读一致，刷新后保留，且不覆盖租户和创建人", async () => {
  const { runtime, storage, sessionStorage } = setup("registrar");
  const id = runtime.database.sources[0].tid;
  const result = await request(runtime, "/dst/db/srcsave", {
    tid: id,
    tenantId: "other",
    propList: {
      dbName: "编辑后的场景库",
      assetDesc: "已修改",
      tenantId: "other",
      createdBy: "other",
      orgId: "other",
    },
  });
  assert.equal(result.status, 200);
  const second = new ChengtianMockRuntime({
    role: "registrar",
    storage,
    sessionStorage,
  });
  const detail = (await request(second, "/dst/db/srcdetail", { tid: id }))
    .body.data;
  assert.equal(detail.dbName, "编辑后的场景库");
  assert.equal(detail.tenantId, tenantConfiguration("police").tenant.tid);
  assert.equal(detail.createdBy, "police-user-0");
  const page = (
    await request(second, "/dst/db/srcpage", { keyword: "编辑后的场景库" })
  ).body.data;
  assert.equal(page.total, 1);
  assert.equal(page.list[0].tid, id);
});

test("本地存储失败不提交内存修改，dryRun 不持久化", async () => {
  const { runtime, storage } = setup();
  const original = runtime.database.sources[0].dbName;
  const id = runtime.database.sources[0].tid;
  for (const [dryRun, propList] of [
    [true, { dbName: "预检名称" }],
    [undefined, { dbName: "预检名称", dryRun: true }],
  ]) {
    const preview = await request(runtime, "/dst/db/srcsave", {
      tid: id, dryRun, propList, deferIndexRefresh: true,
    });
    assert.equal(preview.status, 200);
    assert.equal(preview.body.data.tid, id);
    assert.equal(preview.body.data.indexRefreshPending, false);
    assert.equal(runtime.database.sources[0].dbName, original);
  }
  const existingCount = runtime.database.sources.length;
  const newPreview = await request(runtime, "/dst/db/srcsave", {
    propList: { dbName: "拟新增来源", dryRun: true }, deferIndexRefresh: "1",
  });
  assert.equal(newPreview.status, 200);
  assert.equal(newPreview.body.data.assetStatus, 0);
  assert.equal(newPreview.body.data.indexRefreshPending, false);
  assert.equal(runtime.database.sources.length, existingCount);
  storage.setItem = () => {
    throw new Error("storage full");
  };
  assert.equal(
    (
      await request(runtime, "/dst/db/srcsave", {
        tid: id,
        propList: { dbName: "不应提交" },
      })
    ).status,
    507,
  );
  assert.equal(runtime.database.sources[0].dbName, original);
});

test("数据源保存复用索引延期参数，并拒绝覆盖已提交的场景版本", async () => {
  const { runtime, storage } = setup();
  const id = runtime.database.sources[0].tid;
  for (const flag of [true, 1, "1", false]) {
    const saved = await request(runtime, "/dst/db/srcsave", {
      tid: id, deferIndexRefresh: flag, propList: { dbName: "已保存名称" },
    });
    assert.equal(saved.status, 200);
    assert.equal(saved.body.data.indexRefreshPending, flag !== false);
  }
  const key = [...storage.values.keys()].find((name) => name.includes("chengtian:v3"));
  const newer = JSON.parse(storage.getItem(key));
  newer.sources[0].dbName = "另一窗口已经提交";
  newer.sources[0].versionNo++;
  const getItem = storage.getItem;
  let reads = 0;
  storage.getItem = (name) => {
    if (name === key && ++reads === 3) storage.setItem(key, JSON.stringify(newer));
    return getItem(name);
  };
  const conflict = await request(runtime, "/dst/db/srcsave", {
    tid: id, propList: { dbName: "不应覆盖" },
  });
  assert.equal(conflict.status, 409);
  assert.equal(JSON.parse(getItem(key)).sources[0].dbName, "另一窗口已经提交");
});

test("普通结束登记按有效同源表统计，并只保存登记状态", async () => {
  const { runtime } = setup();
  const source = runtime.database.sources[0];
  const tables = runtime.database.tables.filter((row) => row.datasourceId === source.tid);
  const registered = tables.filter((row) => row.assetStatus === 2).length;
  runtime.persist(runtime.database);
  const path = "/dst/db/srcsave";
  const payload = { tid: source.tid, propList: { completeOnly: true, dbName: "不应修改" } };
  const preview = await request(runtime, path, { ...payload, dryRun: true });
  assert.deepEqual(preview.body.data, {
    tid: source.tid, assetStatus: 1, displayStatus: 1, completionEligible: true, tableReviewReady: false,
    tableCount: tables.length, registeredTableCount: registered, pendingTableCount: tables.length - registered, dryRun: true,
  });
  assert.equal(runtime.database.sources[0].assetStatus, source.assetStatus);
  const saved = await request(runtime, path, payload);
  assert.equal(saved.status, 200);
  assert.equal(saved.body.data.assetStatus, 1);
  assert.equal(runtime.database.sources[0].flowStatus, 2);
  assert.equal(runtime.database.sources[0].dbName, source.dbName);
});

test("已登记数据源关闭连接时，预检和正式保存都返回待登记状态", async () => {
  const { runtime } = setup();
  const source = runtime.database.sources.find((row) => row.assetStatus === 2 && row.showConnect === 1);
  const payload = { tid: source.tid, propList: { showConnect: 0 } };
  const preview = await request(runtime, "/dst/db/srcsave", { ...payload, dryRun: true });
  assert.equal(preview.body.data.assetStatus, 0);
  assert.equal(runtime.database.sources.find((row) => row.tid === source.tid).assetStatus, 2);
  const saved = await request(runtime, "/dst/db/srcsave", payload);
  assert.equal(saved.body.data.assetStatus, 0);
  assert.equal(runtime.database.sources.find((row) => row.tid === source.tid).flowStatus, 0);
});

for (const role of ["admin", "department", "registrar"]) {
  test(`${role}：代理完成登记按可见服务预检并在提交时复核`, async () => {
    const { runtime, storage } = setup(role);
    const source = runtime.database.sources[0];
    source.accessMode = "proxy";
    const witness = { tid: "validation-service", tenantId: runtime.session.tenantId,
      datasourceId: source.tid, orgId: runtime.session.orgId, createdBy: runtime.session.userId, isDel: 0 };
    runtime.database.services = [witness,
      { ...witness, tenantId: "another-tenant" }, { ...witness, datasourceId: "another-source" },
      { ...witness, isDel: 1 },
      ...(role === "admin" ? [] : [{ ...witness, orgId: "another-org", createdBy: "another-user" }]),
    ];
    runtime.persist(runtime.database);
    const payload = { tid: source.tid, propList: { completeOnly: true, dbName: "不应覆盖配置" } };
    const preview = await request(runtime, "/dst/db/srcsave", { ...payload, dryRun: true });
    assert.deepEqual(preview.body.data, {
      tid: source.tid, assetStatus: 2, completionEligible: true, tableReviewReady: true,
      serviceCount: 1, tableCount: 0, registeredTableCount: 0, pendingTableCount: 0,
    });
    const legacyPreview = await request(runtime, "/dst/db/srcsave", {
      tid: source.tid, propList: { asset_status: 2, dryRun: true },
    });
    assert.deepEqual(legacyPreview.body.data, preview.body.data);
    const key = [...storage.values.keys()].find((name) => name.includes("chengtian:v3"));
    const original = storage.getItem(key);
    const withoutWitness = JSON.parse(original);
    withoutWitness.services[0].isDel = 1;
    let reads = 0;
    const getItem = storage.getItem;
    storage.getItem = (name) => {
      if (name === key && ++reads === 3) storage.setItem(key, JSON.stringify(withoutWitness));
      return getItem(name);
    };
    const conflict = await request(runtime, "/dst/db/srcsave", payload);
    assert.equal(conflict.status, 409);
    assert.equal(conflict.body.message, "服务清单已变化，请重新确认后结束登记");
    assert.equal(JSON.parse(getItem(key)).sources[0].assetStatus, source.assetStatus);
    storage.getItem = getItem;
    assert.equal((await request(runtime, "/dst/db/srcsave", payload)).status, 400);
    storage.setItem(key, original);
    const finished = await request(runtime, "/dst/db/srcsave", payload);
    assert.equal(finished.status, 200);
    assert.equal(finished.body.data.assetStatus, 2);
    assert.equal(finished.body.data.indexSync, "status-patch");
    assert.equal(runtime.database.sources[0].dbName, source.dbName);
  });
}

test("分页边界、排序和缺少身份均明确失败", async () => {
  const { runtime } = setup();
  assert.equal(
    (await request(runtime, "/dst/db/srcpage", { pageSize: 101 })).status,
    400,
  );
  assert.equal(
    (await request(runtime, "/dst/db/srcpage", { sortField: "password" }))
      .status,
    400,
  );
  const empty = (
    await request(runtime, "/dst/db/srcpage", {
      keyword: "不存在的场景数据",
    })
  ).body.data;
  assert.equal(empty.total, 0);
  assert.deepEqual(empty.list, []);
  const result = paginate(
    [
      { tid: "2", updatedTime: null },
      { tid: "1", updatedTime: "x" },
    ],
    { sortDir: "desc" },
  );
  assert.equal(result.list[1].tid, "2");
  assert.equal(
    (
      await runtime.handle({
        method: "POST",
        path: "/dst/db/srcpage",
        token: "real-session",
      })
    ).status,
    401,
  );
});

test("数据探查的 conditions 契约与卡片筛选一致", async () => {
  const { runtime } = setup();
  const cards = (
    await request(runtime, "/dst/db/dept", {
      mode: "cards",
      keyword: "预警",
      pageSize: 100,
    })
  ).body.data;
  const table = (
    await request(runtime, "/dst/db/srcpage", {
      conditions: [
        { field: "assetType", value: "db", type: "match" },
        { field: "keyword", value: "预警", type: "match" },
      ],
      pageSize: 100,
    })
  ).body.data;
  assert.equal(cards.total, 20);
  assert.deepEqual(
    table.list.map((row) => row.tid),
    cards.list.map((row) => row.tid),
  );
});

test("未覆盖接口拒绝并且不发网络请求，不记录请求正文和令牌", async () => {
  const { runtime } = setup();
  const result = await request(runtime, "/ods/unsupported", {
    password: "不得记录",
  });
  assert.equal(result.status, 501);
  assert.match(result.body.message, /阻止访问真实服务/);
  assert(
    runtime.requests.every(
      (row) =>
        !row.includes("不得记录") && !row.includes(runtime.session.token),
    ),
  );
});

test("本地账号拒绝跨行业切换，重新登录后旧租户对象不可访问", async () => {
  const { runtime } = setup();
  const id = runtime.database.sources[0].tid;
  const tenantId = tenantConfiguration("broadcasting").tenant.tid;
  const before = runtime.session.tenantId;
  assert.equal((await request(runtime, "/portal/sso/switch", { tenantId })).body.code, 403);
  assert.equal(runtime.session.tenantId, before);
  assert.equal((await request(runtime, "/portal/sso/switch", { tenantId: before })).body.code, 0);
  const session = (await request(runtime, "/portal/sso/session", {}, {}, "GET")).body.data;
  assert.equal(session.accountSource, "LOCAL");
  assert.deepEqual((await request(runtime, "/portal/sso/tenants", {}, {}, "GET")).body.data, session.available);
  assert.deepEqual(session.available.map(item => item.tid), [before]);
  assert.equal((await request(runtime, "/portal/sso/login", { account:"demo", password:"mock-password", tenantId })).body.code, 0);
  assert.equal(
    (await request(runtime, "/dst/db/srcdetail", { tid: id })).status,
    404,
  );
  assert.equal(runtime.database.tenantId, tenantConfiguration("broadcasting").tenant.tid);
});

test("登录选项、行业品牌及配置类型使用当前数据库公开配置镜像", async () => {
  const { runtime } = setup();
  assert.deepEqual(runtime.tenantOptions(), configuration.tenants.map((item) => item.tenant));
  assert.deepEqual((await request(runtime, "/portal/main/index", {}, {}, "GET")).body.data, configuration.globalSettings);
  const head = mockResponse(await runtime.handle({ method: "HEAD", path: "/portal/main/index" }));
  assert.equal(head.status, 200);
  assert.equal(head.headers.get("content-type"), "application/json");
  assert.match(head.headers.get("cache-control"), /no-store/);
  assert.equal(await head.text(), "");
  for (const item of configuration.tenants) {
    const result = await request(runtime, "/portal/sso/settings", {}, { tenantId: item.tenant.tid }, "GET");
    assert.deepEqual(result.body.data, item.settings);
    assert.equal(typeof result.body.data.showShoppingCar, "boolean");
    assert.equal(result.body.data.logo.startsWith("data:image/"), true);
  }
  assert.equal((await request(runtime, "/portal/sso/settings", {}, {tenantId:"demo-police"}, "GET")).body.code, 403);
  assert.equal((await request(runtime, "/portal/sso/settings", {}, {}, "GET")).body.code, 20001);
  for (const suffix of ["options", "settings", "session", "tenants"]) {
    const head = mockResponse(await runtime.handle({ method: "HEAD", path: `/portal/sso/${suffix}` }));
    assert.equal(head.status, 200);
    assert.equal(head.headers.get("content-type"), "application/json");
    assert.equal(await head.text(), "");
  }
});

test("使用原行业选择接口登录其他行业后，刷新仍恢复该行业会话与数据", async () => {
  const storage = memory(), sessionStorage = memory();
  const runtime = new ChengtianMockRuntime({storage, sessionStorage});
  const tenantId = tenantConfiguration("broadcasting").tenant.tid;
  const login = await request(runtime, "/portal/sso/login", {account:"demo", password:"mock-password", tenantId});
  assert.equal(login.status, 200);
  const restored = new ChengtianMockRuntime({storage, sessionStorage});
  assert.equal(restored.session.tenantId, tenantId);
  assert.equal(restored.session.code, "broadcasting");
  assert.equal(restored.database.tenantId, tenantId);
  assert(restored.database.sources.every((row) => row.tenantId === tenantId));
});

test("退出返回原接口的登录路径并清理模拟会话", async () => {
  const { runtime, sessionStorage } = setup();
  const result = await request(runtime, "/portal/sso/logout");
  assert.equal(result.body.data, "/login");
  assert.equal(runtime.session, null);
  assert.equal(new ChengtianMockRuntime({sessionStorage}).session, null);
  assert.equal((await request(runtime, "/portal/sso/session", {}, {}, "GET")).body.code, 100120);
});

test("菜单及组织树保持当前数据库层级，空租户不复制公安配置", async () => {
  for (const item of configuration.tenants) {
    const {runtime}=setup("admin",item.scenario);
    const tree=(await request(runtime,"/sym/org/tree")).body.data;
    assert.equal((await request(runtime,"/sym/org/getOrgTree")).status, 501);
    assert.deepEqual((await request(runtime,"/sym/org/tree",{parentId:"validation-missing"})).body.data, []);

    const flatten=(nodes)=>nodes.flatMap(node=>[node,...flatten(node.children||[])]);
    const actual=flatten(tree),expected=item.organizations.filter(row=>row.id!=="ROOT");
    assert.deepEqual(actual.map(row=>row.id).sort(),expected.map(row=>row.id).sort());
    const byId=new Map(actual.map(row=>[row.id,row]));
    for(const row of expected){assert.equal(byId.get(row.id).name,row.name);assert.equal(byId.get(row.id).parentId,row.parentId);}
    for(const node of actual)assert((node.children||[]).every(child=>child.parentId===node.id));
    if (tree.length) {
      const root = tree[0];
      assert.deepEqual((await request(runtime,"/sym/org/tree",{parentId:root.id})).body.data, root.children);
    }
    if(item.scenario==="police") {
      const sourceMenu=runtime.routes().find(row=>row.path==="/datasource-manage");
      assert(sourceMenu.children.every(row=>row.meta.hidden));
      assert.deepEqual(sourceMenu.children.map(row=>row.path),["/index","/detail"]);
    } else {
      assert.equal(tree.length,0);
      assert.equal(runtime.routes().length,0);
    }
  }
});

test("两套工作区回读同一份持久场景，不缓存另一份数据源", async () => {
  const {runtime,storage,sessionStorage}=setup();
  const peer=new ChengtianMockRuntime({storage,sessionStorage});
  const tid=runtime.database.sources[0].tid;
  await request(peer,"/dst/db/srcdetail",{tid});
  await request(runtime,"/dst/db/srcsave",{tid,propList:{dbName:"共享数据源名称"}});
  assert.equal((await request(peer,"/dst/db/srcdetail",{tid})).body.data.dbName,"共享数据源名称");
});

test("20/100 行表预览各一次请求，结构与预览共用字段，视图来自同一基础表", async () => {
  const {runtime}=setup();const source=runtime.database.sources[0];
  const table=runtime.database.tables.find(row=>row.datasourceId===source.tid&&row.objectType==="TABLE");
  const fields=(await request(runtime,"/dst/db/fields",{}, {tid:table.tid},"GET")).body.data;
  for (const size of [20,100]) {
    const before=runtime.requests.length;
    const response=await request(runtime,"/dst/db/preview",{tableId:table.tid,pageSize:size});
    assert.equal(runtime.requests.length-before,1);
    assert.equal(response.body.data.rows.length,size);
    assert.deepEqual(response.body.data.columns,fields.map(row=>row.columnName));
    assert.equal(response.body.data.total,table.recordCount);
  }
  const view=runtime.database.tables.find(row=>row.datasourceId===source.tid&&row.objectType==="VIEW");
  const viewData=(await request(runtime,"/dst/db/preview",{tableId:view.tid,pageSize:100})).body.data;
  assert.deepEqual(viewData.rows,tableRows(table).filter(row=>row.status==="active"));
  const sample=(await request(runtime,"/dst/db/sample",{dbId:source.tid,tableName:table.tableName,sampleSize:10})).body.data;
  assert.equal(sample.rows.length,10);
});

test("字段与预览按每个表关联的数据源校验租户和角色范围", async () => {
  for(const role of ["department","registrar"]) {
    const {runtime}=setup(role);
    const outside=runtime.database.sources.find(row=>role==="department"?row.orgId!==runtime.session.orgId:row.createdBy!==runtime.session.userId);
    const table=runtime.database.tables.find(row=>row.datasourceId===outside.tid);
    assert.equal((await request(runtime,"/dst/db/fields",{},{tid:table.tid},"GET")).status,404);
    assert.equal((await request(runtime,"/dst/db/preview",{tableId:table.tid,tenantId:outside.tenantId})).status,404);
    const foreign=createScenario("broadcasting").tables[0];
    assert.equal((await request(runtime,"/dst/db/preview",{tableId:foreign.tid})).status,404);
  }
});

test("模拟响应遵守已登记的分页、详情及登录结构", async () => {
  const spec = JSON.parse(
    await fs.readFile(
      new URL("../../mock/contracts/chengtian.json", import.meta.url),
      "utf8",
    ),
  );
  const { runtime } = setup();
  for (const [operation, body] of [
    ["POST /dst/db/srcpage", { pageSize: 20 }],
    [
      "POST /dst/db/dept",
      { mode: "cards", pageSize: 20 },
    ],
    ["POST /dst/db/srcdetail", { tid: runtime.database.sources[0].tid }],
    [
      "POST /dst/db/srcsave",
      {
        tid: runtime.database.sources[0].tid,
        propList: { dbName: "结构校验" },
      },
    ],
  ]) {
    const result = await request(runtime, operation.slice(5), body);
    assert.equal(result.status, 200);
    validateShape(
      body,
      spec.interfaces.find((row) => row.operation === operation).request,
    );
    validateShape(
      result.body.data,
      spec.interfaces.find((row) => row.operation === operation).response,
    );
  }
});

import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs/promises";
import { ChengtianMockRuntime } from "../../mock/core/runtime.mjs";
import { createScenario, scenarios } from "../../mock/scenarios/index.mjs";
import { paginate } from "../../mock/core/data.mjs";
import { configuration, tenantConfiguration } from "../../mock/scenarios/configuration.mjs";
import { tableRows } from "../../mock/core/metadata.mjs";
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

test("56 项契约指向真实实现及当前审阅版本", async () => {
  const result = await checkContracts();
  assert.equal(result.interfaces, 56);
  assert.deepEqual(result.errors, []);
});

test("所属系统选择项一次返回完整租户角色范围，保留线上字段和浏览器搜索所需候选", async () => {
  const route = "/dst/application/register-options";
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
  const probe = "/dst/database/metadata/dbInfo";
  const refresh = "/dst/database/metadata/refreshDbInfo";
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
        "/dst/database/departmentDataSources",
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
      await request(runtime, "/dst/database/page", { pageSize: 100 })
    ).body.data;
    assert.equal(page.total, role === "admin" ? 120 : 40);
    const facets = (
      await request(runtime, "/dst/database/treeStatistics", { mode: "facets" })
    ).body.data;
    assert.equal(facets.total, page.total);
    const allowed = page.list[0];
    assert.equal(
      (await request(runtime, "/dst/database/detail", { tid: allowed.tid }))
        .status,
      200,
    );
    const narrowed = (
      await request(runtime, "/dst/database/page", {
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
        (await request(runtime, "/dst/database/detail", { tid: forbidden.tid }))
          .status,
        404,
      );
      assert.equal(
        (
          await request(runtime, "/dst/database/saveOrUpdate", {
            tid: forbidden.tid,
            propList: { dbName: "无权修改" },
          })
        ).status,
        403,
      );
      assert.equal(
        (
          await request(runtime, "/dst/database/delete", {
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
        await request(runtime, "/dst/database/detail", {
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
  const result = await request(runtime, "/dst/database/saveOrUpdate", {
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
  const detail = (await request(second, "/dst/database/detail", { tid: id }))
    .body.data;
  assert.equal(detail.dbName, "编辑后的场景库");
  assert.equal(detail.tenantId, tenantConfiguration("police").tenant.tid);
  assert.equal(detail.createdBy, "police-user-0");
  const page = (
    await request(second, "/dst/database/page", { keyword: "编辑后的场景库" })
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
    const preview = await request(runtime, "/dst/database/saveOrUpdate", {
      tid: id, dryRun, propList, deferIndexRefresh: true,
    });
    assert.equal(preview.status, 200);
    assert.equal(preview.body.data.tid, id);
    assert.equal(preview.body.data.indexRefreshPending, false);
    assert.equal(runtime.database.sources[0].dbName, original);
  }
  const existingCount = runtime.database.sources.length;
  const newPreview = await request(runtime, "/dst/database/saveOrUpdate", {
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
      await request(runtime, "/dst/database/saveOrUpdate", {
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
    const saved = await request(runtime, "/dst/database/saveOrUpdate", {
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
  const conflict = await request(runtime, "/dst/database/saveOrUpdate", {
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
  const path = "/dst/database/saveOrUpdate";
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
  const preview = await request(runtime, "/dst/database/saveOrUpdate", { ...payload, dryRun: true });
  assert.equal(preview.body.data.assetStatus, 0);
  assert.equal(runtime.database.sources.find((row) => row.tid === source.tid).assetStatus, 2);
  const saved = await request(runtime, "/dst/database/saveOrUpdate", payload);
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
    const preview = await request(runtime, "/dst/database/saveOrUpdate", { ...payload, dryRun: true });
    assert.deepEqual(preview.body.data, {
      tid: source.tid, assetStatus: 2, completionEligible: true, tableReviewReady: true,
      serviceCount: 1, tableCount: 0, registeredTableCount: 0, pendingTableCount: 0,
    });
    const legacyPreview = await request(runtime, "/dst/database/saveOrUpdate", {
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
    const conflict = await request(runtime, "/dst/database/saveOrUpdate", payload);
    assert.equal(conflict.status, 409);
    assert.equal(conflict.body.message, "服务清单已变化，请重新确认后结束登记");
    assert.equal(JSON.parse(getItem(key)).sources[0].assetStatus, source.assetStatus);
    storage.getItem = getItem;
    assert.equal((await request(runtime, "/dst/database/saveOrUpdate", payload)).status, 400);
    storage.setItem(key, original);
    const finished = await request(runtime, "/dst/database/saveOrUpdate", payload);
    assert.equal(finished.status, 200);
    assert.equal(finished.body.data.assetStatus, 2);
    assert.equal(finished.body.data.indexSync, "status-patch");
    assert.equal(runtime.database.sources[0].dbName, source.dbName);
  });
}

test("分页边界、排序和缺少身份均明确失败", async () => {
  const { runtime } = setup();
  assert.equal(
    (await request(runtime, "/dst/database/page", { pageSize: 101 })).status,
    400,
  );
  assert.equal(
    (await request(runtime, "/dst/database/page", { sortField: "password" }))
      .status,
    400,
  );
  const empty = (
    await request(runtime, "/dst/database/page", {
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
        path: "/dst/database/page",
        token: "real-session",
      })
    ).status,
    401,
  );
});

test("数据探查的 conditions 契约与卡片筛选一致", async () => {
  const { runtime } = setup();
  const cards = (
    await request(runtime, "/dst/database/departmentDataSources", {
      mode: "cards",
      keyword: "预警",
      pageSize: 100,
    })
  ).body.data;
  const table = (
    await request(runtime, "/dst/database/page", {
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

test("租户切换后旧租户对象不可访问，源数据不串场景", async () => {
  const { runtime } = setup();
  const id = runtime.database.sources[0].tid;
  assert.equal(
    (
      await request(runtime, "/sym/tenant/switch", {
        tenantId: tenantConfiguration("broadcasting").tenant.tid,
      })
    ).status,
    200,
  );
  assert.equal(
    (await request(runtime, "/dst/database/detail", { tid: id })).status,
    404,
  );
  assert.equal(runtime.database.tenantId, tenantConfiguration("broadcasting").tenant.tid);
});

test("登录选项、行业品牌及配置类型使用当前数据库公开配置镜像", async () => {
  const { runtime } = setup();
  assert.deepEqual(runtime.tenantOptions(), configuration.tenants.map((item) => item.tenant));
  assert.deepEqual((await request(runtime, "/portal/index", {}, {}, "GET")).body.data, configuration.globalSettings);
  for (const item of configuration.tenants) {
    const result = await request(runtime, "/sym/tenant/login-settings", {}, { tenantId: item.tenant.tid }, "GET");
    assert.deepEqual(result.body.data, item.settings);
    assert.equal(typeof result.body.data.showShoppingCar, "boolean");
    assert.equal(result.body.data.logo.startsWith("data:image/"), true);
  }
  assert.equal((await request(runtime, "/sym/tenant/login-settings", {}, {tenantId:"demo-police"}, "GET")).status, 400);
});

test("使用原行业选择接口登录其他行业后，刷新仍恢复该行业会话与数据", async () => {
  const storage = memory(), sessionStorage = memory();
  const runtime = new ChengtianMockRuntime({storage, sessionStorage});
  const tenantId = tenantConfiguration("broadcasting").tenant.tid;
  const login = await request(runtime, "/portal/login", {account:"demo", password:"mock-password", tenantId});
  assert.equal(login.status, 200);
  const restored = new ChengtianMockRuntime({storage, sessionStorage});
  assert.equal(restored.session.tenantId, tenantId);
  assert.equal(restored.session.code, "broadcasting");
  assert.equal(restored.database.tenantId, tenantId);
  assert(restored.database.sources.every((row) => row.tenantId === tenantId));
});

test("退出返回原接口的登录路径并清理模拟会话", async () => {
  const { runtime, sessionStorage } = setup();
  const result = await request(runtime, "/portal/logout");
  assert.equal(result.body.data, "/login");
  assert.equal(runtime.session, null);
  assert.equal(new ChengtianMockRuntime({sessionStorage}).session, null);
  assert.equal((await request(runtime, "/sym/tenant/current", {}, {}, "GET")).status, 401);
});

test("菜单及组织树保持当前数据库层级，空租户不复制公安配置", async () => {
  for (const item of configuration.tenants) {
    const {runtime}=setup("admin",item.scenario);
    const tree=(await request(runtime,"/sym/org/getOrgTree")).body.data;

    const flatten=(nodes)=>nodes.flatMap(node=>[node,...flatten(node.children||[])]);
    const actual=flatten(tree),expected=item.organizations.filter(row=>row.id!=="ROOT");
    assert.deepEqual(actual.map(row=>row.id).sort(),expected.map(row=>row.id).sort());
    const byId=new Map(actual.map(row=>[row.id,row]));
    for(const row of expected){assert.equal(byId.get(row.id).name,row.name);assert.equal(byId.get(row.id).parentId,row.parentId);}
    for(const node of actual)assert((node.children||[]).every(child=>child.parentId===node.id));
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
  await request(peer,"/dst/database/detail",{tid});
  await request(runtime,"/dst/database/saveOrUpdate",{tid,propList:{dbName:"共享数据源名称"}});
  assert.equal((await request(peer,"/dst/database/detail",{tid})).body.data.dbName,"共享数据源名称");
});

test("20/100 行表预览各一次请求，结构与预览共用字段，视图来自同一基础表", async () => {
  const {runtime}=setup();const source=runtime.database.sources[0];
  const table=runtime.database.tables.find(row=>row.datasourceId===source.tid&&row.objectType==="TABLE");
  const fields=(await request(runtime,"/dst/database/metadata/columns",{}, {tid:table.tid},"GET")).body.data;
  for (const size of [20,100]) {
    const before=runtime.requests.length;
    const response=await request(runtime,"/dst/database/metadata/table/preview-data",{tableId:table.tid,pageSize:size});
    assert.equal(runtime.requests.length-before,1);
    assert.equal(response.body.data.rows.length,size);
    assert.deepEqual(response.body.data.columns,fields.map(row=>row.columnName));
    assert.equal(response.body.data.total,table.recordCount);
  }
  const view=runtime.database.tables.find(row=>row.datasourceId===source.tid&&row.objectType==="VIEW");
  const viewData=(await request(runtime,"/dst/database/metadata/table/preview-data",{tableId:view.tid,pageSize:100})).body.data;
  assert.deepEqual(viewData.rows,tableRows(table).filter(row=>row.status==="active"));
  const sample=(await request(runtime,"/dst/database/metadata/table/sample-data",{dbId:source.tid,tableName:table.tableName,sampleSize:10})).body.data;
  assert.equal(sample.rows.length,10);
});

test("字段与预览按每个表关联的数据源校验租户和角色范围", async () => {
  for(const role of ["department","registrar"]) {
    const {runtime}=setup(role);
    const outside=runtime.database.sources.find(row=>role==="department"?row.orgId!==runtime.session.orgId:row.createdBy!==runtime.session.userId);
    const table=runtime.database.tables.find(row=>row.datasourceId===outside.tid);
    assert.equal((await request(runtime,"/dst/database/metadata/columns",{},{tid:table.tid},"GET")).status,404);
    assert.equal((await request(runtime,"/dst/database/metadata/table/preview-data",{tableId:table.tid,tenantId:outside.tenantId})).status,404);
    const foreign=createScenario("broadcasting").tables[0];
    assert.equal((await request(runtime,"/dst/database/metadata/table/preview-data",{tableId:foreign.tid})).status,404);
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
    ["POST /dst/database/page", { pageSize: 20 }],
    [
      "POST /dst/database/departmentDataSources",
      { mode: "cards", pageSize: 20 },
    ],
    ["POST /dst/database/detail", { tid: runtime.database.sources[0].tid }],
    [
      "POST /dst/database/saveOrUpdate",
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

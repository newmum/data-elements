import { createScenario, scenarios } from "../scenarios/index.mjs";
import { organizationTree, menuRoutes } from "./configuration.mjs";
import { requireTable, tableColumns, previewTable } from "./metadata.mjs";
import { detailForms } from "../scenarios/forms.mjs";
import {
  configuration,
  tenantConfiguration,
  scenarioForTenantId,
} from "../scenarios/configuration.mjs";
import {
  assert,
  MockError,
  visibleSources,
  requireSources,
  paginate,
  filterSources,
} from "./data.mjs";

const clone = (value) => structuredClone(value);
const now = () => new Date().toISOString().replace("T", " ").slice(0, 19);

/** 应用层接口模拟。状态只存在于专用浏览器键，不读取数据库或执行 Magic/Java。 */
export class ChengtianMockRuntime {
  /** @param {{scenario?:string,role?:string,storage?:Storage,sessionStorage?:Storage,components?:Array<Record<string, unknown>>}} options */
  constructor({
    scenario = "police",
    role = "admin",
    storage,
    sessionStorage,
    components = [],
  } = {}) {
    assert(Object.hasOwn(scenarios, scenario), 400, "未知 mock 场景");
    assert(
      ["admin", "department", "registrar"].includes(role),
      400,
      "未知 mock 角色",
    );
    this.code = scenario;
    this.role = role;
    this.storage = storage;
    this.sessionStorage = sessionStorage;
    this.components = components;
    this.databases = new Map();
    this.requests = [];
    this.session = null;
    const saved = sessionStorage?.getItem("data-elements:mock:session:v3");
    if (saved) {
      try {
        const value = JSON.parse(saved);
        if (
          Object.hasOwn(scenarios, value.code) &&
          value.tenantId === tenantConfiguration(value.code).tenant.tid &&
          value.role === role &&
          value.token?.startsWith("mock.")
        )
          this.session = value;
      } catch {
        /* 损坏的模拟会话只要求重新登录，不操作真实会话。 */
      }
    }
  }

  get database() {
    const code = this.session?.code || this.code;
    if (!this.databases.has(code)) {
      const key = `data-elements:mock:chengtian:v3:${code}`;
      const source = this.storage?.getItem(key);
      let database = createScenario(code);
      if (source) {
        try {
          const candidate = JSON.parse(source);
          assert(
            candidate.version === 3 &&
              candidate.code === code &&
              candidate.tenantId === database.tenantId &&
              Array.isArray(candidate.sources) &&
              candidate.sources.length <= 1000 &&
              candidate.sources.every(
                (row) => row.tenantId === database.tenantId,
              ) &&
              Array.isArray(candidate.tables),
            422,
            "本地场景数据格式无效",
          );
          database = {
            ...candidate,
            organizations: database.organizations,
            scene: database.scene,
          };
        } catch {
          throw new MockError(
            422,
            "本地场景数据格式无效，请清理该场景的 Mock 本地缓存",
          );
        }
      }
      this.databases.set(code, database);
    }
    return this.databases.get(code);
  }

  persist(database) {
    try {
      this.storage?.setItem(
        `data-elements:mock:chengtian:v3:${database.code}`,
        JSON.stringify({
          ...database,
          organizations: undefined,
          scene: undefined,
        }),
      );
    } catch {
      throw new MockError(507, "浏览器存储不足，操作未保存");
    }
    this.databases.set(database.code, database);
  }

  login(code, role = this.role) {
    assert(Object.hasOwn(scenarios, code), 403, "不能进入未定义的行业场景");
    const token = `mock.${crypto.randomUUID()}`;
    this.session = {
      code,
      role,
      token,
      tenantId: tenantConfiguration(code).tenant.tid,
      scope: { admin: "ALL", department: "ORG", registrar: "OWNER" }[role],
      userId: `${code}-user-0`,
      orgId: createScenario(code).applications[0]?.orgId || "",
    };
    this.sessionStorage?.setItem(
      "data-elements:mock:session:v3",
      JSON.stringify(this.session),
    );
    return {
      token,
      tenantId: this.session.tenantId,
      url: "/datasource-manage",
    };
  }

  settings(code = this.session?.code || this.code) {
    assert(Object.hasOwn(scenarios, code), 404, "行业场景不存在");
    return clone(tenantConfiguration(code).settings);
  }

  tenantOptions() {
    return configuration.tenants
      .map(({ tenant }) => clone(tenant))
      .sort(
        (a, b) =>
          Number(b.tid === tenantConfiguration(this.code).tenant.tid) -
          Number(a.tid === tenantConfiguration(this.code).tenant.tid),
      );
  }

  tenant() {
    return {
      ...this.tenantOptions().find((row) => row.tid === this.session.tenantId),
      available: this.tenantOptions(),
    };
  }

  user() {
    const db = this.database;
    return {
      id: this.session.userId,
      userId: this.session.userId,
      userName: "demo",
      realName: {
        admin: "数据管理员",
        department: "部门管理员",
        registrar: "登记人员",
      }[this.session.role],
      orgId: this.session.orgId,
      orgName:
        db.organizations.find((row) => row.id === this.session.orgId)?.name ||
        "",
      orgRootId: "ROOT",
      orgRootSerialNumber: "001",
      serialNumber: "001000",
      orgPath: this.session.orgId,
      roles: [this.session.role === "admin" ? "admin" : this.session.role],
      perms: ["*:*:*"],
    };
  }

  routes() {
    return menuRoutes(
      tenantConfiguration(this.session?.code || this.code).menus || [],
    );
  }

  statistics(params) {
    const rows = filterSources(
      visibleSources(this.database, this.session),
      params,
    );
    const orgCounts = {},
      orgApps = {},
      dbTypes = {},
      typeCounts = {},
      assetStatuses = {};
    for (const row of rows) {
      orgCounts[row.orgId] = (orgCounts[row.orgId] || 0) + 1;
      dbTypes[row.dbType] = (dbTypes[row.dbType] || 0) + 1;
      typeCounts[row.dbType] = (typeCounts[row.dbType] || 0) + 1;
      assetStatuses[row.assetStatus] =
        (assetStatuses[row.assetStatus] || 0) + 1;
      const apps = (orgApps[row.orgId] ||= []);
      let app = apps.find((item) => item.value === row.appId);
      if (!app) {
        app = {
          value: row.appId,
          label: row.appName,
          count: 0,
          type: "app",
          datasources: [],
        };
        apps.push(app);
      }
      app.count++;
      app.datasources.push({
        value: row.tid,
        label: row.dbName,
        count: 1,
        dbType: row.dbType,
        type: "datasource",
      });
    }
    return {
      complete: true,
      changesAvailable: true,
      total: rows.length,
      assignedTotal: rows.length,
      unassignedTotal: 0,
      orgCounts,
      orgApps,
      dbTypes,
      typeCounts,
      assetStatuses,
    };
  }

  sourcePage(params) {
    const allowed = visibleSources(this.database, this.session);
    const rows = filterSources(allowed, params);
    const page = paginate(rows, params);
    // 列表一次提供展示字段；连接详情只在用户主动打开单条记录时提供。
    page.list = page.list.map(({ host, port, ...row }) => ({
      ...row,
      description: row.assetDesc || "",
    }));
    return {
      ...page,
      dataScope: this.session.scope,
      orgId: this.session.orgId,
      orgName: this.user().orgName,
      changesAvailable: true,
      nextCursor:
        page.pageNum * page.pageSize < page.total
          ? {
              sortKey: page.list.at(-1)?.updatedTime,
              tid: page.list.at(-1)?.tid,
            }
          : null,
      summary: {
        sourceCount: allowed.length,
        tableCount: allowed.reduce((sum, row) => sum + row.tableNum, 0),
        fieldCount: allowed.reduce((sum, row) => sum + row.fieldCount, 0),
        collectedCount: allowed.length,
        attentionCount: allowed.filter(
          (row) => row.connectionStatus === "failed",
        ).length,
      },
      distributions: [],
    };
  }

  sourceDetail(id) {
    const [row] = requireSources(this.database, this.session, id);
    return {
      ...clone(row),
      dbTypeName:
        {
          mysql: "MySQL",
          postgresql: "PostgreSQL",
          oracle: "Oracle",
          kingbase8: "Kingbase",
          dameng: "Dameng",
        }[row.dbType] || row.dbType,
      poolCfg: {
        database: row.database,
        host: row.host,
        port: row.port,
        accessMode: row.accessMode,
      },
      found: true,
    };
  }

  saveSource(body) {
    const database = clone(this.database),
      props = body.propList;
    assert(
      props && typeof props === "object" && !Array.isArray(props),
      400,
      "缺少数据源 propList",
    );
    const existing = body.tid
      ? requireSources(database, this.session, body.tid)[0]
      : null;
    assert(
      props.dbName === undefined ||
        (typeof props.dbName === "string" &&
          props.dbName.trim().length > 0 &&
          props.dbName.length <= 255),
      400,
      "数据源名称须为 1–255 字",
    );
    const fields = [
      "dbName",
      "assetDesc",
      "database",
      "schema",
      "host",
      "port",
      "dbType",
      "storageDomain",
      "showConnect",
    ];
    const changes = Object.fromEntries(
      fields
        .filter((key) => props[key] !== undefined)
        .map((key) => [key, props[key]]),
    );
    assert(existing || changes.dbName, 400, "请输入数据源名称");
    const row = existing || {
      tid: `${database.code}-source-${crypto.randomUUID()}`,
      tenantId: this.session.tenantId,
      orgId: this.session.orgId,
      orgName: this.user().orgName,
      createdBy: this.session.userId,
      createdByName: this.user().realName,
      createdTime: now(),
      tableNum: 0,
      fieldCount: 0,
      assetStatus: 0,
      flowStatus: 0,
      isDel: 0,
      versionNo: 0,
    };
    if (props.appId && props.appId !== row.appId) {
      const app = database.applications.find(
        (item) => item.tid === props.appId,
      );
      assert(
        app &&
          (this.session.scope === "ALL" || app.orgId === this.session.orgId),
        403,
        "业务系统不在当前数据范围",
      );
      Object.assign(row, { appId: app.tid, appName: app.appName });
    }
    Object.assign(row, changes, {
      id: row.tid,
      updatedTime: now(),
      versionNo: row.versionNo + 1,
    });
    if (!existing) database.sources.push(row);
    if (!body.dryRun && !props.dryRun) this.persist(database);
    return {
      tid: row.tid,
      assetStatus: row.assetStatus,
      indexRefreshPending: body.deferIndexRefresh === true,
    };
  }

  tables(params) {
    const id = params.datasourceId || params.dbId;
    const [source] = requireSources(this.database, this.session, id);
    const allRows = this.database.tables.filter(
      (row) =>
        row.datasourceId === source.tid &&
        row.tenantId === this.session.tenantId,
    );
    let rows = allRows;
    const keyword = String(
      params.keyword || params.tableName || params.tableNames || "",
    ).toLowerCase();
    if (keyword)
      rows = rows.filter((row) =>
        `${row.tableName} ${row.tableComment}`.toLowerCase().includes(keyword),
      );
    if (params.businessType)
      rows = rows.filter((row) => row.businessType === params.businessType);
    const page = paginate(rows, params, [
      "tableName",
      "tableComment",
      "columnNum",
      "updatedTime",
      "recordCount",
      "tableRemarks",
    ]);
    return {
      ...page,
      rows: page.list,
      tableCount: rows.length,
      businessStats: Object.fromEntries(
        ["业务表", "日志表", "字典表", "过程表", "备份表"].map((type) => [
          type,
          allRows.filter((row) => row.businessType === type).length,
        ]),
      ),
      businessTypeCounts: {
        business: rows.filter((row) => row.businessType === "业务表").length,
        dict: rows.filter((row) => row.businessType === "字典表").length,
        log: rows.filter((row) => row.businessType === "日志表").length,
      },
      collectionStatus: "SUCCEEDED",
    };
  }

  async handle({ method = "GET", path, query = {}, body = {}, token = "" }) {
    // 主页面与风驰的同源桥每次请求回读同一份场景状态，避免跨窗口缓存分叉。
    this.databases.clear();
    const key = `${method.toUpperCase()} ${path}`;
    // 只记录方法与路由，绝不记录密码、令牌或请求正文。
    this.requests.push(key);
    if (this.requests.length > 500) this.requests.shift();
    try {
      const data = await this.dispatch(key, query, body || {}, token);
      return { status: 200, body: { code: 0, message: "操作成功", data } };
    } catch (error) {
      if (!(error instanceof MockError)) throw error;
      return {
        status: error.status,
        body: {
          code: Number(error.status),
          message: error.message,
          data: null,
        },
      };
    }
  }

  dispatch(key, query, body, token) {
    if (key === "GET /portal/index") return clone(configuration.globalSettings);
    if (key === "GET /sym/tenant/login-options") return this.tenantOptions();
    if (key === "GET /sym/tenant/login-settings") {
      const code = scenarioForTenantId(query.tenantId);
      assert(code, 400, "行业租户不存在");
      return this.settings(code);
    }
    if (key === "POST /portal/login") {
      assert(
        typeof body.account === "string" &&
          body.account.trim() &&
          typeof body.password === "string" &&
          body.password,
        400,
        "请输入本地演示账号和密码",
      );
      const code = scenarioForTenantId(body.tenantId);
      assert(code, 403, "请选择有效的行业场景");
      return this.login(code);
    }
    assert(
      this.session && token === this.session.token,
      401,
      "本地场景会话无效，请重新登录",
    );
    if (key === "POST /portal/logout") {
      this.session = null;
      this.sessionStorage?.removeItem("data-elements:mock:session:v3");
      return "/login";
    }
    if (["GET /sym/tenant/current", "GET /sym/tenant/available"].includes(key))
      return key.endsWith("/available") ? this.tenantOptions() : this.tenant();
    if (key === "POST /sym/tenant/switch") {
      const code = scenarioForTenantId(body.tenantId);
      assert(Object.hasOwn(scenarios, code), 403, "不能进入未定义的行业场景");
      Object.assign(this.session, {
        code,
        tenantId: tenantConfiguration(code).tenant.tid,
        orgId: createScenario(code).applications[0]?.orgId || "",
        userId: `${code}-user-0`,
      });
      this.sessionStorage?.setItem(
        "data-elements:mock:session:v3",
        JSON.stringify(this.session),
      );
      return this.tenant();
    }
    if (key === "POST /sym/user/me") return this.user();
    if (key === "GET /sym/menu/routes") return this.routes();
    if (key === "GET /sym/dict") return query.code ? [] : {};
    if (key === "POST /dws/market/shoppingCardList") {
      assert(body.mode === "count", 501, "首阶段仅提供导航购物车计数");
      return { total: 0 };
    }
    if (key === "POST /sym/org/getOrgTree")
      return organizationTree(this.database.organizations);
    if (key === "POST /sym/component") {
      assert(
        ["list", "runtimeBatch"].includes(query.action),
        501,
        "首阶段仅支持本地组件读取",
      );
      return query.action === "list"
        ? this.components
        : { complete: true, scope: "all", components: this.components };
    }
    if (key === "POST /sym/form") {
      assert(
        !query.action || query.action === "get",
        501,
        "首阶段仅支持场景表单读取",
      );
      return detailForms(this.database).filter(
        (row) =>
          (!body.formName || row.formName === body.formName) &&
          (!body.env || row.env === body.env),
      );
    }
    if (key === "POST /dst/database/treeStatistics")
      return this.statistics(body);
    if (key === "POST /dst/database/page") return this.sourcePage(body);
    if (key === "POST /dst/database/departmentDataSources") {
      assert(
        ["cards", "choices", "tables", "summary"].includes(body.mode),
        501,
        "首阶段未覆盖此数据源读取模式",
      );
      if (body.mode === "tables") return this.tables(body);
      return this.sourcePage(body);
    }
    if (key === "POST /dst/database/detail") return this.sourceDetail(body.tid);
    if (key === "POST /dst/database/saveOrUpdate") return this.saveSource(body);
    if (key === "POST /dst/database/delete") {
      const database = clone(this.database),
        rows = requireSources(database, this.session, body.tid);
      rows.forEach((row) => {
        row.isDel = 1;
      });
      this.persist(database);
      return true;
    }
    if (key === "POST /dst/maintenance/refresh") {
      requireSources(this.database, this.session, body.tid);
      return { tid: body.tid, refreshed: true };
    }
    if (
      [
        "GET /dst/database/metadata/dbInfo",
        "GET /dst/database/metadata/refreshDbInfo",
      ].includes(key)
    )
      return this.sourceDetail(query.tid || query.dbId);
    if (key === "GET /dst/database/metadata/tables") {
      const result = this.tables(query);
      return query.paged || query.catalogPaged ? result : result.list;
    }
    if (key === "GET /dst/database/metadata/columns") {
      const table = requireTable(
        this.database,
        this.session,
        query.tid || query.tableId,
      );
      return tableColumns(table);
    }
    if (
      [
        "POST /dst/database/metadata/table/preview-data",
        "POST /dst/database/metadata/table/sample-data",
      ].includes(key)
    ) {
      const tableId =
        body.tableId ||
        body.tid ||
        this.database.tables.find(
          (row) =>
            row.datasourceId === body.dbId && row.tableName === body.tableName,
        )?.tid;
      const table = requireTable(this.database, this.session, tableId);
      return previewTable(
        table,
        key.endsWith("sample-data")
          ? { ...body, pageNo: 1, pageSize: body.sampleSize || 20 }
          : body,
      );
    }
    if (key === "GET /dst/database/metadata/tables/collection/status") {
      requireSources(this.database, this.session, query.dbId);
      return {
        status: "SUCCEEDED",
        progressPercent: 100,
        totalTables: 6,
        completedTables: 6,
      };
    }
    if (
      [
        "POST /dst/application/register-options",
        "POST /dst/application/detail",
      ].includes(key)
    ) {
      const rows = this.database.applications.filter(
        (row) =>
          this.session.scope === "ALL" || row.orgId === this.session.orgId,
      );
      if (key.endsWith("/detail"))
        return clone(
          rows.find(
            (row) => row.tid === body.tid || row.appName === body.appName,
          ) || null,
        );
      return clone(
        rows.filter(
          (row) => !body.keyword || row.appName.includes(body.keyword),
        ),
      );
    }
    throw new MockError(501, `首阶段尚未覆盖接口：${key}，已阻止访问真实服务`);
  }
}

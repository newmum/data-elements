import { createScenario, scenarios } from "../scenarios/index.mjs";
import { organizationTree, menuRoutes } from "./configuration.mjs";
import { requireTable, tableColumns, previewTable } from "./metadata.mjs";
import { detailForms } from "../scenarios/forms.mjs";
import { LogMockService, logErrorResponse } from "./logs.mjs";
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
  sourceTypeKey,
} from "./data.mjs";

const clone = (value) => structuredClone(value);
const now = () => new Date().toISOString().replace("T", " ").slice(0, 19);

/** 应用层接口模拟。状态只存在于专用浏览器键，不读取数据库或执行 Magic/Java。 */
export class ChengtianMockRuntime {
  /** @param {{scenario?:string,role?:string,storage?:Storage,sessionStorage?:Storage,components?:Array<Record<string, unknown>>,clock?:()=>number}} options */
  constructor({
    scenario = "police",
    role = "admin",
    storage,
    sessionStorage,
    components = [],
    clock = Date.now,
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
    this.logs = new LogMockService({ storage, clock });
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
      const typeKey = sourceTypeKey(row);
      typeCounts[typeKey] = (typeCounts[typeKey] || 0) + 1;
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
        apiPullItems: clone(row.apiPullItems || []),
      },
      found: true,
    };
  }

  metadataSummary(query, refresh) {
    const { tid, tableId, dryRun } = query;
    const database = this.database;
    const tenantId = this.session.tenantId;
    assert(String(tid ?? "").trim(), 400, "tid 不能为空");

    const table = String(tableId ?? "").trim()
      ? database.tables.find((row) => row.tid === tableId && row.tenantId === tenantId && !row.isDel)
      : null;
    if (String(tableId ?? "").trim()) {
      assert(table, 404, "数据表不存在或无访问权限");
      assert(table.datasourceId === tid, 403, "数据表不属于指定数据源");
    }
    const source = database.sources.find((row) => row.tid === tid && row.tenantId === tenantId && !row.isDel);
    assert(source, 404, "数据源资产不存在或无访问权限");
    if (refresh) {
      // 独立探查允许当前租户的有效资产，刷新入口使用数据源管理的写入范围。
      assert(visibleSources(database, this.session).includes(source), 403, "数据源不存在或无权修改");
    }

    const persist = refresh && dryRun !== true && dryRun !== "true";
    if (table) {
      const summary = {
        tid: table.tid,
        datasourceId: tid,
        recordCount: table.recordCount ?? null,
        fieldCount: table.fieldCount ?? null,
        storageSize: table.storageSize ?? null,
        totalSizeFormatted: table.totalSizeFormatted ?? null,
        totalSizeBytes: table.totalSizeBytes ?? null,
        ddlCreateTime: table.ddlCreateTime ?? null,
        ddlUpdateTime: table.ddlUpdateTime ?? null,
      };
      if (persist) {
        const updated = clone(database);
        Object.assign(updated.tables.find((row) => row.tid === table.tid), summary, { updatedTime: now() });
        this.persist(updated);
      }
      return summary;
    }

    const tables = database.tables.filter((row) => row.datasourceId === tid && row.tenantId === tenantId && !row.isDel);
    const summary = {
      dbVersion: source.dbVersion || "",
      db_version: source.dbVersion || "",
      dataSize: source.dataSize ?? "0 B",
      totalSize: source.totalSize ?? "0 B",
      tableCount: tables.filter((row) => row.objectType === "TABLE").length,
      viewCount: tables.filter((row) => row.objectType === "VIEW").length,
      procedureCount: source.procedureCount ?? null,
      userCount: source.userCount ?? null,
    };
    if (!persist) return summary;

    const updated = clone(database);
    Object.assign(updated.sources.find((row) => row.tid === tid), {
      dbVersion: summary.dbVersion,
      tableNum: summary.tableCount,
      viewCount: summary.viewCount,
      procedureCount: summary.procedureCount,
      userCount: summary.userCount,
      dataSize: summary.dataSize,
      totalSize: summary.totalSize,
      updatedTime: now(),
    });
    this.persist(updated);
    return {
      ...summary,
      tid,
      tableNum: summary.tableCount,
      table_num: summary.tableCount,
      view_count: summary.viewCount,
      procedure_count: summary.procedureCount,
      user_count: summary.userCount,
      data_size: summary.dataSize,
      total_size: summary.totalSize,
    };
  }

  saveSource(body) {
    const originalSnapshot = this.storage?.getItem(`data-elements:mock:chengtian:v3:${this.session.code}`);
    const current = this.database;
    const database = clone(current);
    const props = body.propList;
    assert(
      props && typeof props === "object" && !Array.isArray(props),
      400,
      "缺少数据源 propList",
    );
    const tid = String(body.tid ?? "").trim();
    const existing = tid ? database.sources.find((row) =>
      row.tid === tid && row.tenantId === this.session.tenantId && !row.isDel) : null;
    const requestedStatus = String(props.assetStatus ?? props.asset_status ?? "");
    const completeOnly = props.completeOnly === true;
    const dryRun = body.dryRun === true || props.dryRun === true;
    const wantsCompletion = completeOnly || ["1", "2"].includes(requestedStatus);
    if (wantsCompletion) assert(existing, 404, "数据源不存在或不属于当前租户");
    if (existing) {
      assert(visibleSources(database, this.session).includes(existing), 403, "数据源不存在或无权修改");
    } else if (tid) {
      assert(!database.sources.some((row) => row.tid === tid), 404, "数据源不存在或不属于当前租户");
    }
    const version = existing?.versionNo;
    const proxy = ["proxy", "agent"].includes(String(existing?.accessMode || "").toLowerCase());
    const review = wantsCompletion ? this.reviewSourceRegistration(database, existing, proxy) : null;
    if (review && dryRun) return review;
    if (completeOnly) {
      assert(review.completionEligible, 400, "请先登记至少一项当前数据范围内的数据服务，再结束登记");
      this.assertSourceSaveCurrent(current, originalSnapshot, tid, version, proxy);
      Object.assign(existing, { assetStatus: review.assetStatus, displayStatus: review.assetStatus,
        flowStatus: 2, updatedTime: now(), versionNo: version + 1 });
      this.persist(database);
      const { assetStatus, tableReviewReady, tableCount, registeredTableCount, pendingTableCount } = review;
      return { tid, assetStatus, displayStatus: assetStatus, completionEligible: true, tableReviewReady,
        tableCount, registeredTableCount, pendingTableCount, indexSync: "status-patch" };
    }
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
      "accessMode",
    ];
    const changes = Object.fromEntries(
      fields
        .filter((key) => props[key] !== undefined)
        .map((key) => [key, props[key]]),
    );
    const captureMode = changes.accessMode ?? existing?.accessMode;
    const sourceType = changes.dbType ?? existing?.dbType;
    if (
      captureMode === "capture" &&
      sourceType === "api" &&
      props.apiPullItems !== undefined
    ) {
      assert(Array.isArray(props.apiPullItems), 400, "API 抓取登记定义无效");
      changes.apiPullItems = props.apiPullItems.map((item) => {
        const { tableName, tableComment, serviceName, apiProtocol, baseUrl,
          endpointPath, endpointMethod, responseExtractPath, captureSchedule } = item;
        return { tableName, tableComment, serviceName, apiProtocol, baseUrl,
          endpointPath, endpointMethod, responseExtractPath, captureSchedule: clone(captureSchedule) };
      });
    }
    assert(existing || changes.dbName, 400, "请输入数据源名称");
    const row = existing || {
      tid: tid || `${database.code}-source-${crypto.randomUUID()}`,
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
    const connectionChanged = existing && (
      (changes.dbType && changes.dbType !== existing.dbType) ||
      (String(changes.showConnect) === "0" && String(existing.showConnect) === "1")
    );
    Object.assign(row, changes, {
      id: row.tid,
      updatedTime: now(),
      versionNo: row.versionNo + 1,
    });
    if (review || requestedStatus === "0" || (connectionChanged && [1, 2].includes(row.assetStatus))) {
      row.assetStatus = review?.assetStatus ?? 0;
      row.displayStatus = row.assetStatus;
      row.flowStatus = review ? 2 : 0;
    }
    if (!existing) database.sources.push(row);
    if (!dryRun) {
      this.assertSourceSaveCurrent(current, originalSnapshot, existing?.tid, version, proxy && review?.completionEligible === true);
      this.persist(database);
    }
    return {
      tid: row.tid,
      assetStatus: row.assetStatus,
      indexRefreshPending: !dryRun && [true, 1, "1"].includes(body.deferIndexRefresh),
    };
  }

  reviewSourceRegistration(database, source, proxy) {
    const tid = source.tid;
    if (proxy) {
      const serviceCount = (database.services ?? []).filter((row) =>
        row.tenantId === this.session.tenantId && row.datasourceId === tid && !row.isDel && (
          this.session.scope === "ALL" ||
          (this.session.scope === "ORG" && this.session.orgId && row.orgId === this.session.orgId) ||
          (this.session.scope === "OWNER" && this.session.userId && row.createdBy === this.session.userId)
        )).length;
      const eligible = serviceCount > 0;
      return { tid, assetStatus: eligible ? 2 : 1, completionEligible: eligible,
        tableReviewReady: eligible, serviceCount, tableCount: 0, registeredTableCount: 0, pendingTableCount: 0 };
    }
    const tables = database.tables.filter((row) =>
      row.tenantId === this.session.tenantId && row.datasourceId === tid && !row.isDel);
    const registeredTableCount = tables.filter((row) => row.assetStatus === 2).length;
    const tableReviewReady = tables.length > 0 && tables.length === registeredTableCount;
    const assetStatus = tableReviewReady ? 2 : 1;
    return { tid, assetStatus, displayStatus: assetStatus, completionEligible: true, tableReviewReady,
      tableCount: tables.length, registeredTableCount, pendingTableCount: tables.length - registeredTableCount, dryRun: true };
  }

  assertSourceSaveCurrent(current, originalSnapshot, tid, version, requireProxyService) {
    // 另一窗口已保存场景时拒绝覆盖，调用方重新读取后再提交。
    const stored = this.storage?.getItem(`data-elements:mock:chengtian:v3:${current.code}`);
    if (stored !== originalSnapshot) this.databases.delete(current.code);
    const latest = this.database;
    const row = tid && latest.sources.find((item) =>
      item.tid === tid && item.tenantId === this.session.tenantId && !item.isDel);
    if (requireProxyService) {
      assert(row && row.versionNo === version && this.reviewSourceRegistration(latest, row, true).completionEligible,
        409, "服务清单已变化，请重新确认后结束登记");
    }
    assert(stored === originalSnapshot && latest === current && (!tid || (row && row.versionNo === version)),
      409, "数据源记录已变化，请重新读取后保存");
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

  /**
   * @param {{method?:string,path:string,query?:Record<string,unknown>,body?:import("./transport.mjs").MockBody,token?:string}} request
   * @returns {Promise<import("./transport.mjs").MockResult>}
   */
  async handle({ method = "GET", path, query = {}, body = {}, token = "" }) {
    // 主页面与风驰的同源桥每次请求回读同一份场景状态，避免跨窗口缓存分叉。
    this.databases.clear();
    const key = `${method.toUpperCase()} ${path}`;
    // 只记录方法与路由，绝不记录密码、令牌或请求正文。
    this.requests.push(key);
    if (this.requests.length > 500) this.requests.shift();
    try {
      const data = await this.dispatch(key, query, body || {}, token);
      if (path.startsWith("/sym/logs/") && data?.raw === true) return data;
      return { status: 200, body: { code: 0, message: "操作成功", ...(path.startsWith("/sym/logs/") ? { success: true } : {}), data } };
    } catch (error) {
      if (!(error instanceof MockError)) throw error;
      if (path.startsWith("/sym/logs/")) return logErrorResponse(error);
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
    if (/^[A-Z]+ \/sym\/logs(?:\/|$)/.test(key))
      return this.logs.handle(key, query, body, this.database, clone(this.session), this.components);
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
    if (key === "GET /dst/database/metadata/dbInfo") return this.metadataSummary(query, false);
    if (key === "GET /dst/database/metadata/refreshDbInfo") return this.metadataSummary(query, true);
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
    if (key === "POST /dst/application/register-options") {
      // 登记选择器读取当前租户完整候选，页面再对名称和 ID 搜索。
      return this.database.applications
        .filter((row) =>
          row.tenantId === this.session.tenantId &&
          (row.isDel == null || row.isDel === 0) &&
          (this.session.scope === "ALL" ||
            (Boolean(this.session.orgId) && row.orgId === this.session.orgId)),
        )
        .map((row) => ({
          label: row.appName ?? null,
          value: row.tid,
          orgid: row.orgId ?? null,
          createdby: row.createdBy ?? null,
        }))
        .sort((left, right) =>
          (Number(left.label != null) - Number(right.label != null)) ||
          (left.label ?? "").localeCompare(right.label ?? "", "zh-CN") ||
          left.value.localeCompare(right.value),
        );
    }
    if (key === "POST /dst/application/detail") {
      const rows = this.database.applications.filter(
        (row) =>
          this.session.scope === "ALL" || row.orgId === this.session.orgId,
      );
      return clone(
        rows.find(
          (row) => row.tid === body.tid || row.appName === body.appName,
        ) || null,
      );
    }
    throw new MockError(501, `首阶段尚未覆盖接口：${key}，已阻止访问真实服务`);
  }
}

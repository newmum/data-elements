import type { SyncTask } from '../core/model';
import { platformApi, platformSessionToken } from './platformApi';

export type PlatformUser = {
  userId?: string;
  userName?: string;
  realName?: string;
  orgId?: string;
  orgName?: string;
  roles?: string[];
  perms?: string[];
};

export type ReportTable = {
  id: string;
  /** The registered datasource that owns this table. */
  datasourceId: string;
  datasourceName: string;
  name: string;
  displayName: string;
  businessType: string;
  fieldCount: number;
  recordCount: number;
  timestampField: string;
  updatedAt: string;
  tableType: string;
  /** Field-level governance saved during the platform's five-step registration. */
  governanceFields: SourceTable[];
  businessTimeField: string;
};

/** A field from the completed registration record, not an inferred upload column. */
export type ReportTableField = {
  name: string;
  displayName: string;
  dataType: string;
  primaryKey: boolean;
  /** Nullable rule saved with the completed registration field definition. */
  nullable?: boolean;
  dictionaryName: string;
  standardLabel: string;
  timeRoles: Array<'业务时间' | '时间戳'>;
};

export type ReportContext = {
  datasourceId: string;
  datasourceName: string;
  user: PlatformUser;
  tenant: { tenantId?: string | number; tenantName?: string; tenantCode?: string };
  tables: ReportTable[];
  batchCount: number;
};

/** First-screen context plus the non-blocking work used to refresh all sources. */
export type ReportContextBootstrap = {
  context: ReportContext;
  refreshTables: () => Promise<ReportContext>;
  restoredFromCache: boolean;
};

type SourceTable = Record<string, unknown>;
type RegisteredTablePage = { rows?: SourceTable[]; list?: SourceTable[]; columns?: SourceTable[]; data?: SourceTable[]; total?: number };
type ReportContextCache = {
  savedAt: number;
  datasourceId: string;
  tenantId: string;
  userId: string;
  datasourceName: string;
  tables: ReportTable[];
};
const REPORT_CONTEXT_CACHE_PREFIX = 'haitong:report-context:v1:';
const REPORT_CONTEXT_CACHE_TTL = 2 * 60 * 1000;
const text = (...values: unknown[]) => values.find(value => value !== undefined && value !== null && String(value).trim())?.toString().trim() || '';
const readRows = (result: unknown): SourceTable[] => Array.isArray(result) ? result as SourceTable[] : (result as RegisteredTablePage)?.rows || (result as RegisteredTablePage)?.list || (result as RegisteredTablePage)?.columns || (result as RegisteredTablePage)?.data || [];
const truthy = (value: unknown) => [true, 1, '1', 'true', 'TRUE', 'yes', 'YES'].includes(value as never);
const fieldName = (row: SourceTable) => text(row.columnName, row.column_name, row.fieldName, row.field_name, row.colEn);
const normalizeName = (value: string) => value.trim().toLowerCase();

type DepartmentDatasource = { id: string; name: string };

/** Mirrors the aliases accepted by the data-source registration page. */
function normalizeAccessMode(value: unknown): string {
  const mode = String(value ?? '').trim().toLowerCase();
  return ({ extract: 'explore', push: 'receive', pull: 'capture', report: 'upload' } as Record<string, string>)[mode] || mode;
}

function parseObject(value: unknown): SourceTable {
  if (value && typeof value === 'object' && !Array.isArray(value)) return value as SourceTable;
  if (typeof value !== 'string' || !value.trim()) return {};
  try {
    const parsed: unknown = JSON.parse(value);
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed as SourceTable : {};
  } catch {
    return {};
  }
}

function datasourceConfig(row: SourceTable): SourceTable {
  return parseObject(row.poolCfg ?? row.pool_cfg ?? row.connectorConfig ?? row.connector_config);
}

function datasourceAccessMode(row: SourceTable): string {
  const config = datasourceConfig(row);
  return normalizeAccessMode(text(
    row.accessMode, row.access_mode, row.dataAccessMode, row.data_access_mode,
    config.accessMode, config.access_mode, config.dataAccessMode, config.data_access_mode,
  ));
}

function datasourceId(row: SourceTable): string {
  return text(row.tid, row.id, row.datasourceId, row.dataSourceId, row.dbId);
}

function datasourceName(row: SourceTable): string {
  const config = datasourceConfig(row);
  return text(
    row.dbName, row.db_name, row.datasourceName, row.dataSourceName,
    row.appName, row.app_name, config.dbName, config.db_name,
  ) || '未命名数据源';
}

function governanceFields(row: SourceTable): SourceTable[] {
  const config = parseObject(row.fieldGovernanceConfig ?? row.field_governance_config ?? row.governanceConfig ?? row.governance_config);
  const fields = config.fields ?? config.fieldList ?? row.governanceFields ?? row.governance_fields;
  return Array.isArray(fields) ? fields.filter(item => item && typeof item === 'object') as SourceTable[] : [];
}

function dictionaryName(field: SourceTable): string {
  const relation = parseObject(field.dictionaryRelation ?? field.dictionary_relation);
  if (!truthy(relation.enabled ?? field.dictionaryEnabled ?? field.dictionary_enabled)) return '';
  const table = text(relation.dictionaryTable, relation.dictionary_table, relation.dictionaryName, relation.dictionary_name);
  const key = text(relation.dictionaryKeyField, relation.dictionary_key_field);
  const label = text(relation.dictionaryLabelField, relation.dictionary_label_field);
  if (table) return [table, key && label ? `${key}→${label}` : ''].filter(Boolean).join(' · ');
  return text(relation.name, relation.label, field.dictionaryName, field.dictionary_name) || '已关联';
}

const standardLabels: Record<string, string> = {
  primary_id: '主键标识', business_code: '业务编码', business_name: '业务名称', status: '状态字段',
  created_time: '创建时间', updated_time: '更新时间', created_by: '创建人', organization: '组织机构',
  area_code: '行政区划', SFZH: '身份证', LXDH: '手机号码', DATETIME: '日期',
};

function standardLabel(field: SourceTable): string {
  const value = text(field.standardField, field.standard_field, field.standardCode, field.standard_code);
  return value ? (standardLabels[value] || value) : '';
}

function timeRoles(field: SourceTable, table: ReportTable, name: string): Array<'业务时间' | '时间戳'> {
  const raw = field.timeRoles ?? field.time_roles ?? field.timeRole ?? field.time_role;
  const roles = Array.isArray(raw) ? raw : raw ? [raw] : [];
  const values = new Set(roles.map(role => String(role).trim().toLowerCase()));
  if (truthy(field.isTimestampField ?? field.is_timestamp_field)) values.add('timestamp');
  if (normalizeName(table.timestampField) === normalizeName(name)) values.add('timestamp');
  if (normalizeName(table.businessTimeField) === normalizeName(name)) values.add('business');
  return [values.has('business') && '业务时间', values.has('timestamp') && '时间戳'].filter(Boolean) as Array<'业务时间' | '时间戳'>;
}
async function loadStage<T>(label: string, request: Promise<T>): Promise<T> {
  try {
    return await request;
  } catch (error) {
    const message = error instanceof Error ? error.message : '未知错误';
    throw new Error(`${label}失败：${message}`);
  }
}

/** The server validates department, tenant, and upload-mode eligibility for the whole batch. */
async function loadDatasourceTables(sources: DepartmentDatasource[]): Promise<Array<{ source: DepartmentDatasource; rows: SourceTable[] }>> {
  const results = sources.map(source => ({ source, rows: [] as SourceTable[] }));
  const byId = new Map(results.map(result => [result.source.id, result]));
  for (let start = 0; start < sources.length; start += 200) {
    const batch = sources.slice(start, start + 200);
    const requested = new Set(batch.map(source => source.id));
    const loadPage = (pageNum: number) => loadStage('读取已登记数据表', platformApi<{ list: SourceTable[]; total: number }>(
      '/dst/database/departmentDataSources',
      { method: 'POST', body: { mode: 'reportTables', datasourceIds: batch.map(source => source.id), pageNum, pageSize: 100 } },
    ));
    const append = (page: { list: SourceTable[]; total: number }) => {
      if (!Array.isArray(page?.list)) throw new Error('已登记数据表批量接口返回格式不正确');
      for (const row of page.list) {
        const id = text(row.datasourceId, row.datasource_id, row.dbId);
        if (!requested.has(id)) throw new Error('已登记数据表的来源不属于本次数据源范围');
        byId.get(id)!.rows.push(row);
      }
    };
    const first = await loadPage(1);
    const total = Number(first.total);
    if (!Number.isSafeInteger(total) || total < 0) throw new Error('已登记数据表批量接口总数不正确');
    append(first);
    const pageCount = Math.ceil(total / 100);
    for (let pageNum = 2; pageNum <= pageCount; pageNum += 4) {
      const pages = await Promise.all(Array.from({ length: Math.min(4, pageCount - pageNum + 1) }, (_, index) => loadPage(pageNum + index)));
      pages.forEach(append);
    }
  }
  return results;
}

/**
 * This endpoint is scoped by the authenticated department and tenant. Its
 * summary mode retains source IDs/names and accessMode without loading every
 * source's table details; the workbench selects only “数据上报” sources.
 */
async function loadDepartmentReportDatasources(): Promise<DepartmentDatasource[]> {
  const result = await loadStage(
    '读取当前部门数据源',
    platformApi<SourceTable[] | RegisteredTablePage>('/dst/database/departmentDataSources', { method: 'POST', body: { mode: 'summary' } }),
  );
  const summaries = readRows(result);
  // The department endpoint already projects `accessMode`.  An old source
  // without that saved mode is not eligible for the reporting workbench.  Do
  // not issue one detail request per legacy source merely to rediscover an
  // absent mode: a large department could otherwise spend tens of seconds
  // hydrating irrelevant records before it reaches the few upload sources.
  const seen = new Set<string>();
  return summaries
    .filter(row => datasourceAccessMode(row) === 'upload')
    .map(row => ({ id: datasourceId(row), name: datasourceName(row) }))
    .filter(source => source.id && !seen.has(source.id) && !!seen.add(source.id));
}

/**
 * The selectable-table list intentionally excludes fieldGovernanceConfig so a
 * large datasource does not download every table's full registration payload.
 * Fetch that saved fifth-step configuration only for the table whose structure
 * dialog the user opens, then merge it with the authoritative physical fields.
 */
async function loadTableGovernanceFields(datasourceId: string, tableName: string): Promise<SourceTable[]> {
  const query = new URLSearchParams({
    dbId: datasourceId,
    paged: 'true',
    pageNo: '1',
    pageSize: '20',
    keyword: tableName,
  });
  const result = await loadStage(
    '读取登记字段治理配置',
    platformApi<RegisteredTablePage>(`/dst/database/metadata/tables?${query.toString()}`),
  );
  const record = readRows(result).find(row => normalizeName(text(row.tableName, row.table_name, row.tableNameEn)) === normalizeName(tableName));
  return record ? governanceFields(record) : [];
}

export function reportDatasourceIdFromLocation(): string {
  const params = new URLSearchParams(window.location.search);
  const hash = window.location.hash || '';
  const routeParams = new URLSearchParams(hash.includes('?') ? hash.slice(hash.indexOf('?') + 1) : '');
  return text(routeParams.get('datasourceId'), routeParams.get('dbId'), params.get('datasourceId'), params.get('dbId'));
}

function reportContextCacheKey(datasourceId: string): string {
  return `${REPORT_CONTEXT_CACHE_PREFIX}${datasourceId || 'department'}`;
}

function readCachedReportContext(datasourceId: string, user: PlatformUser, tenant: ReportContext['tenant']): ReportContext | undefined {
  try {
    const raw = sessionStorage.getItem(reportContextCacheKey(datasourceId));
    if (!raw) return undefined;
    const cached = JSON.parse(raw) as ReportContextCache;
    if (Date.now() - cached.savedAt > REPORT_CONTEXT_CACHE_TTL
      || cached.datasourceId !== datasourceId
      || cached.tenantId !== text(tenant.tenantId)
      || cached.userId !== text(user.userId)) return undefined;
    if (!Array.isArray(cached.tables)) return undefined;
    return {
      datasourceId,
      datasourceName: cached.datasourceName,
      user,
      tenant,
      tables: cached.tables,
      batchCount: 0,
    };
  } catch {
    return undefined;
  }
}

function cacheReportContext(context: ReportContext): void {
  try {
    const entry: ReportContextCache = {
      savedAt: Date.now(),
      datasourceId: context.datasourceId,
      tenantId: text(context.tenant.tenantId),
      userId: text(context.user.userId),
      datasourceName: context.datasourceName,
      tables: context.tables,
    };
    sessionStorage.setItem(reportContextCacheKey(context.datasourceId), JSON.stringify(entry));
  } catch {
    // Storage can be disabled or full.  The live API result remains usable.
  }
}

function toReportTables(results: Array<{ source: DepartmentDatasource; rows: SourceTable[] }>): ReportTable[] {
  const seen = new Set<string>();
  return results
    .flatMap(({ source, rows }) => rows.map(row => ({ row, source })))
    .filter(({ row }) => ['业务表', '日志表'].includes(text(row.businessType, row.business_type)))
    .map(({ row, source }) => {
      const governance = parseObject(row.fieldGovernanceConfig ?? row.field_governance_config ?? row.governanceConfig ?? row.governance_config);
      return {
        id: text(row.tid, row.tableId, row.id),
        datasourceId: source.id,
        datasourceName: source.name,
        name: text(row.tableName, row.table_name, row.tableNameEn),
        displayName: text(row.tableNameCn, row.table_name_cn, row.tableComment, row.table_name, row.tableName),
        businessType: text(row.businessType, row.business_type),
        fieldCount: Number(row.fieldCount || row.field_count || row.columnCount || row.column_count || 0),
        recordCount: Number(row.recordCount || row.record_count || 0),
        timestampField: text(row.timestampField, row.timestamp_field, governance.timestampField, governance.timestamp_field),
        updatedAt: text(row.updatedTime, row.updated_time, row.regTime, row.reg_time),
        tableType: text(row.tableType, row.table_type),
        governanceFields: governanceFields(row),
        businessTimeField: text(row.businessTimeField, row.business_time_field, governance.businessTimeField, governance.business_time_field),
      };
    })
    .filter(table => table.id && table.name && !seen.has(`${table.datasourceId}:${table.id}`) && !!seen.add(`${table.datasourceId}:${table.id}`));
}

function contextFromTables(
  datasourceId: string,
  user: PlatformUser,
  tenant: ReportContext['tenant'],
  datasources: DepartmentDatasource[],
  tables: ReportTable[],
): ReportContext {
  return {
    datasourceId,
    datasourceName: datasources.length === 1 ? datasources[0].name : '当前部门数据上报数据源',
    user,
    tenant,
    tables,
    batchCount: 0,
  };
}

async function refreshDepartmentReportContext(
  datasourceId: string,
  user: PlatformUser,
  tenant: ReportContext['tenant'],
): Promise<ReportContext> {
  const datasources = await loadDepartmentReportDatasources();
  const tables = toReportTables(await loadDatasourceTables(datasources));
  const context = contextFromTables(datasourceId, user, tenant, datasources, tables);
  cacheReportContext(context);
  return context;
}

/**
 * Opens the workbench as soon as its preferred reporting source is usable;
 * tables from other department sources are discovered in the background. A
 * short, user-and-tenant-bound session cache makes a return visit immediate
 * while the server result is still refreshed before a submission is possible.
 */
export async function bootstrapReportContext(preferredDatasourceId = ''): Promise<ReportContextBootstrap> {
  // Start the session bridge once before issuing API calls.  The user, tenant,
  // and datasource list have no dependency on one another, so do not serialize
  // them behind the relatively expensive department-scope query.
  const session = platformSessionToken();
  const [userResponse, tenantResponse] = await Promise.all([
    loadStage('读取当前用户', platformApi<SourceTable>('/sym/user/me', { method: 'POST', body: {} })),
    loadStage('读取当前租户', platformApi<SourceTable>('/sym/tenant/current')),
    session,
  ]).then(([loadedUser, loadedTenant]) => [loadedUser, loadedTenant] as const);
  const user: PlatformUser = { ...userResponse, userId: text(userResponse.userId, userResponse.tid, userResponse.id) };
  const tenant: ReportContext['tenant'] = {
    tenantId: text(tenantResponse.tenantId, tenantResponse.tid, tenantResponse.id),
    tenantName: text(tenantResponse.tenantName, tenantResponse.name),
    tenantCode: text(tenantResponse.tenantCode, tenantResponse.code),
  };
  if (!user.userId || !tenant.tenantId) throw new Error('当前登录身份或租户信息不完整，请重新登录后打开数据上报');
  const cached = readCachedReportContext(preferredDatasourceId, user, tenant);
  if (cached) {
    return {
      context: cached,
      restoredFromCache: true,
      refreshTables: () => refreshDepartmentReportContext(preferredDatasourceId, user, tenant),
    };
  }

  const datasources = await loadDepartmentReportDatasources();
  const preferred = datasources.find(source => source.id === preferredDatasourceId) || datasources[0];
  const firstResult = preferred ? (await loadDatasourceTables([preferred]))[0] : undefined;
  const firstTables = firstResult ? toReportTables([firstResult]) : [];
  const context = contextFromTables(preferredDatasourceId, user, tenant, datasources, firstTables);
  return {
    context,
    restoredFromCache: false,
    refreshTables: async () => {
      const remaining = datasources.filter(source => source.id !== preferred?.id);
      const allTables = toReportTables([
        ...(firstResult ? [firstResult] : []),
        ...await loadDatasourceTables(remaining),
      ]);
      const refreshed = contextFromTables(preferredDatasourceId, user, tenant, datasources, allTables);
      cacheReportContext(refreshed);
      return refreshed;
    },
  };
}

/**
 * Lists all tables registered beneath the current department's “数据上报”
 * datasources. The URL id remains only for backward-compatible entry links;
 * it must not narrow or grant access to a datasource.
 */
export async function loadReportContext(preferredDatasourceId = ''): Promise<ReportContext> {
  const bootstrap = await bootstrapReportContext(preferredDatasourceId);
  return bootstrap.refreshTables();
}

/**
 * Reads field definitions only for the table the user selected.  Loading them
 * eagerly for every table would make a 4,000-table data source unnecessarily
 * slow and would also risk building a template from an unrelated table.
 */
export async function loadReportTableFields(table: ReportTable): Promise<ReportTableField[]> {
  const query = new URLSearchParams({ tid: table.id, dbId: table.datasourceId, tableName: table.name });
  const [result, savedGovernanceFields] = await Promise.all([
    loadStage('读取登记字段', platformApi<SourceTable[] | RegisteredTablePage>(`/dst/database/metadata/columns?${query.toString()}`)),
    table.governanceFields.length
      ? Promise.resolve(table.governanceFields)
      : loadTableGovernanceFields(table.datasourceId, table.name),
  ]);
  const savedByName = new Map(savedGovernanceFields.map(field => [normalizeName(fieldName(field)), field] as const).filter(([name]) => name));
  return readRows(result)
    .map(row => {
      const name = fieldName(row);
      // Physical metadata keeps the type/current column order; the five-step
      // registration configuration supplies the governance annotations.
      const saved = savedByName.get(normalizeName(name));
      const field = saved ? { ...row, ...saved, columnName: row.columnName ?? saved.columnName } : row;
      const baseType = text(row.columnType, row.typeName, row.dataType, row.data_type);
      const length = text(row.columnLength, row.columnSize, row.characterMaximumLength, row.decimalDigits);
      return {
        name,
        displayName: text(row.columnComment, row.column_comment, row.fieldComment, row.field_comment, row.columnName, row.column_name),
        dataType: baseType && length && !/[（(]/.test(baseType) ? `${baseType}(${length})` : baseType,
        primaryKey: truthy(field.primaryKey ?? field.primary_key ?? field.isPrimaryKey ?? field.is_primary_key),
        nullable: (() => {
          const value = field.nullable ?? field.isNullable ?? field.is_nullable ?? field.allowNull ?? field.allow_null;
          return value === undefined || value === null || value === '' ? undefined : truthy(value);
        })(),
        dictionaryName: dictionaryName(field),
        standardLabel: standardLabel(field),
        timeRoles: timeRoles(field, table, name),
      };
    })
    .filter(field => field.name);
}

export async function submitRegisteredReport(
  context: ReportContext,
  task: SyncTask,
  sourceFile?: File,
): Promise<{ targetDatasourceName?: string; remoteFileName?: string }> {
  if (!task.targetTable) {
    throw new Error('请先选择已完成五步登记的数据表，再上传并提交数据');
  }
  const registered = context.tables.find(table => table.id === task.targetTable?.id
    && table.datasourceId === task.targetTable?.datasourceId
    && table.name.toLowerCase() === task.targetTable?.name.toLowerCase());
  if (!registered) {
    throw new Error(`“${task.targetTable.displayName}”不在当前数据源可上报的登记表范围内，请返回登记流程确认权限与表状态`);
  }
  if (task.schema.englishName.trim().toLowerCase() !== registered.name.toLowerCase()) {
    throw new Error('上传资源与所选登记表不一致，请重新选择数据表并读取对应的数据文件');
  }
  if (!sourceFile) {
    throw new Error('原始上报文件已不在当前会话中，请重新读取文件后再提交');
  }
  const form = new FormData();
  form.append('datasourceId', registered.datasourceId);
  form.append('tableName', registered.name);
  form.append('file', sourceFile, sourceFile.name);
  const result = await platformApi<{ targetDatasourceName?: string; remoteFileName?: string }>('/dst/database/report-file', {
    method: 'POST',
    body: form,
  });
  return result;
}

/**
 * Reads a legacy immutable task snapshot by physical English column name.
 * It is retained for historical-task compatibility and tests only; the live
 * report-file flow transfers the original uploaded workbook instead of this
 * row payload.
 */
export function reportRowsForSubmission(task: SyncTask): Record<string, unknown>[] {
  return task.items.map(item => Object.fromEntries(
    task.schema.fields
      .filter(field => field.en)
      .map(field => [
        field.en,
        Object.prototype.hasOwnProperty.call(item.values, field.en)
          ? item.values[field.en]
          : item.values[field.id] ?? null,
      ]),
  ));
}

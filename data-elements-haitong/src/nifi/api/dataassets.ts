/**
 * 数据资产 API 服务
 *
 * 提供数据库字典查询接口，用于来源库/目标库快捷选择与配置回填。
 */

import axios from 'axios';
import { getBridgeToken, getSessionRevision, onNifiSessionChange } from './bridgeSession';
import { getOrgPath, getIsAdmin } from './iframeBridge';
import { isChengtianIntegration } from '@/config/appConfig';
import { getApiErrorMessage, unwrapResponse } from './response';

const dataassetsBase = import.meta.env.VITE_APP_DATAASSETS_API || '/dev-api';
const requestTimeout = Number(import.meta.env.VITE_APP_DATAASSETS_TIMEOUT_MS || import.meta.env.VITE_APP_API_TIMEOUT_MS || 45000);

/**
 * 解包通用响应：{ code, data, ... } -> data
 */
function unwrap<T = unknown>(body: unknown): T {
  if (typeof body === 'string') {
    try {
      return unwrap<T>(JSON.parse(body));
    } catch {
      return body as T;
    }
  }
  if (body && typeof body === 'object' && !Array.isArray(body)) {
    const obj = body as Record<string, unknown>;
    if (obj.success === false || (typeof obj.code === 'number' && obj.code !== 0 && obj.code !== 200)) {
      throw new Error(String(obj.msg ?? obj.message ?? `接口返回异常(${obj.code ?? 'unknown'})`));
    }
    if (('code' in obj || 'status' in obj) && 'data' in obj) {
      return obj.data as T;
    }
  }
  return body as T;
}

function authHeaders(token = getBridgeToken()) {
  return token ? { Authorization: `Bearer ${token}`, token } : undefined;
}

function assertCurrentSession(revision: number) {
  if (revision !== getSessionRevision()) throw new Error('登录会话已切换，旧数据已丢弃');
}

// ---- 类型定义 ----

/**
 * 数据源登记第一步保存的一条 API 拉取定义。
 *
 * 一个 API 数据源可以登记多个逻辑表，不能再把它压缩为旧版的单一
 * apiUrl/requestBody 字段；画布需要根据当前来源表选择对应的定义。
 */
export interface ApiPullItem {
  tableName?: string;
  tableComment?: string;
  requestUrl?: string;
  url?: string;
  apiUrl?: string;
  baseUrl?: string;
  endpointPath?: string;
  endpointMethod?: string;
  method?: string;
  apiMethod?: string;
  requestHeadersJson?: string;
  commonHeadersJson?: string;
  requestHeaders?: Record<string, string> | Array<{ key?: string; name?: string; value?: string }>;
  requestTemplateJson?: string;
  requestBody?: string;
  apiBody?: string;
  contentType?: string;
  apiCollectionPaths?: string[] | string;
  collectionPaths?: string[] | string;
  apiTimeoutSeconds?: number | string;
  connectTimeout?: string;
  readTimeout?: string;
  responseConfigJson?: string;
  paginationConfigJson?: string;
  incrementalConfigJson?: string;
}

/** 数据库字典项（sym/dict 接口返回的单条记录） */
export interface DbDictItem {
  label: string;
  value: string;
  id: string;
  tid?: string;
  dbName?: string;
  dbType?: string;
  host?: string;
  port?: string;
  nodeId?: string;
  nodeType?: string;
  nodeTypeName?: string;
  tableNum?: number;
  database?: string;
  username?: string;
  password?: string;
  jdbcURL?: string;
  jdbcUrl?: string;
  /** Oracle endpoint mode as registered by the datasource (SID or Service Name). */
  connectionType?: string;
  jdbcType?: string;
  sid?: string;
  serviceName?: string;
  schema?: string;
  defaultSchema?: string;
  /** OceanBase connection metadata. */
  compatibleMode?: string;
  tenant?: string;
  clusterId?: string;
  /** Vendor JDBC driver properties from the datasource custom-property panel. */
  jdbcProperties?: Record<string, string>;
  /** Server-owned Huawei MRS Hive metadata connection marker. */
  metadataAccessMode?: string;
  hiveConnectionMode?: string;
  /** Server-owned MRS profile identifier; it never contains client config or secrets. */
  hiveProfile?: string;
  authMode?: string;
  zookeeperQuorum?: string;
  serviceDiscoveryMode?: string;
  zookeeperNamespace?: string;
  principal?: string;
  userPrincipal?: string;
  keytabPath?: string;
  krb5ConfPath?: string;
  clientConfigDir?: string;
  saslQop?: string;
  ssl?: boolean;
  driverLocations?: string;
  extraParams?: string;
  ftpProtocol?: string;
  protocol?: string;
  ftpHost?: string;
  ftpPort?: string;
  ftpPath?: string;
  ftpUsername?: string;
  ftpPassword?: string;
  ftpPassiveMode?: boolean;
  ftpFilePattern?: string;
  apiUrl?: string;
  apiMethod?: string;
  contentType?: string;
  requestBody?: string;
  /** 画布/探查服务使用的 API 请求头 JSON。 */
  commonHeadersJson?: string;
  apiHeaders?: string;
  apiBody?: string;
  apiCollectionPaths?: string[] | string;
  apiTimeoutSeconds?: number | string;
  /** 数据源登记中的全部接口定义，按来源表匹配。 */
  apiPullItems?: ApiPullItem[];
  authHeader?: string;
  connectTimeout?: string;
  readTimeout?: string;
  showConnect?: number | boolean;
  connectionStatus?: string;
  assetType?: string;
  assettype?: string;
  appName?: string;
  applicationName?: string;
  /** 组织机构路径，用于客户端前缀匹配过滤 */
  orgPath?: string;
}

// ---- 模块级缓存（防止 StrictMode 双重挂载 & 多次打开抽屉时重复请求） ----

let databasesCache: DbDictItem[] | null = null;
let databasesPromise: Promise<DbDictItem[]> | null = null;

export function clearDataassetsSessionCache() {
  databasesCache = null;
  databasesPromise = null;
}

onNifiSessionChange(clearDataassetsSessionCache);

// ---- 接口函数 ----

/**
 * 获取数据库字典列表（带模块级缓存）
 * GET /sym/dict?code=db
 *
 * 同一会话内只请求一次，后续调用直接返回缓存结果。
 * 并发调用共享同一个 Promise，避免 StrictMode 双重挂载导致的重复请求。
 *
 * The backend applies the tenant and data-scope boundary.  The canvas does
 * not derive a privilege or organization filter from URL or iframe messages.
 */
export async function fetchDatabases(): Promise<DbDictItem[]> {
  if (databasesPromise) return databasesPromise.then(filterByOrgPath);
  if (databasesCache) return filterByOrgPath(databasesCache);

  const revision = getSessionRevision();
  const token = getBridgeToken();
  let request!: Promise<DbDictItem[]>;
  request = (async () => {
    try {
      const pageList = await fetchDatabasePageList(token, revision);
      assertCurrentSession(revision);
      if (pageList.length) {
        databasesCache = pageList;
        return databasesCache;
      }

      const { data } = await axios.get<unknown>(`${dataassetsBase}/sym/dict`, {
        params: { code: 'db' },
        headers: authHeaders(token),
        timeout: requestTimeout,
      });
      assertCurrentSession(revision);
      const unwrapped = unwrap<DbDictItem[] | { data?: DbDictItem[] }>(data);
      let fallbackList: DbDictItem[] = [];
      if (unwrapped && typeof unwrapped === 'object' && !Array.isArray(unwrapped) && 'data' in unwrapped) {
        fallbackList = Array.isArray(unwrapped.data) ? unwrapped.data : [];
      } else if (Array.isArray(unwrapped)) {
        fallbackList = unwrapped;
      }
      databasesCache = normalizeDatabaseItems(fallbackList);
      return databasesCache;
    } finally {
      if (databasesPromise === request) databasesPromise = null;
    }
  })();

  databasesPromise = request;

  return request.then(filterByOrgPath);
}

/** 客户端按 orgPath 前缀匹配过滤数据库列表 */
export async function fetchDatabaseDetail(id: string): Promise<DbDictItem | null> {
  if (!id) return null;
  const revision = getSessionRevision();
  const token = getBridgeToken();
  const { data } = await axios.post<unknown>(
    `${dataassetsBase}/dst/database/detail`,
    { tid: id },
    {
      headers: authHeaders(token),
      timeout: requestTimeout,
    },
  );
  assertCurrentSession(revision);
  return normalizeDatabaseItem(unwrap<Record<string, unknown>>(data));
}

async function fetchDatabasePageList(token: string | null, revision: number): Promise<DbDictItem[]> {
  const pageSize = 500;
  const first = await fetchDatabasePage(1, pageSize, token, revision);
  assertCurrentSession(revision);
  const total = Number(first.total ?? 0);
  if (!Number.isFinite(total) || total <= first.list.length) {
    return normalizeDatabaseItems(first.list);
  }

  const pageCount = Math.ceil(total / pageSize);
  const rest = await Promise.all(
    Array.from({ length: Math.max(0, pageCount - 1) }, (_, index) => fetchDatabasePage(index + 2, pageSize, token, revision)),
  );
  return normalizeDatabaseItems([
    ...first.list,
    ...rest.flatMap((page) => page.list),
  ]);
}

async function fetchDatabasePage(pageNo: number, pageSize: number, token: string | null, revision: number): Promise<{ list: unknown[]; total?: number }> {
  const { data } = await axios.post<unknown>(
    `${dataassetsBase}/dst/database/page`,
    { pageNo, pageSize },
    {
      headers: authHeaders(token),
      timeout: requestTimeout,
    },
  );
  assertCurrentSession(revision);
  const payload = unwrap<any>(data);
  const page = payload?.list ? payload : payload?.data?.list ? payload.data : payload;
  return {
    list: Array.isArray(page?.list) ? page.list : Array.isArray(page) ? page : [],
    total: Number(page?.total ?? 0),
  };
}

function normalizeDatabaseItems(input: unknown): DbDictItem[] {
  const rows = Array.isArray(input) ? input : [];
  const seen = new Set<string>();
  const result: DbDictItem[] = [];
  for (const row of rows) {
    const item = normalizeDatabaseItem(row);
    if (!item) continue;
    const key = item.value || item.id || item.tid || item.label;
    if (!key || seen.has(key)) continue;
    seen.add(key);
    result.push(item);
  }
  return result;
}

function normalizeDatabaseItem(input: unknown): DbDictItem | null {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return null;
  const row = input as Record<string, any>;
  const assetType = pickString(row.assetType, row.assettype, row.asset_type);
  if (assetType && assetType.toLowerCase() !== 'db' && assetType.toLowerCase() !== 'database') return null;

  const id = pickString(row.tid, row.id, row.value);
  const dbName = pickString(row.dbName, row.db_name, row.datasourceName, row.datasource_name, row.name, row.label);
  const poolConfig = readPoolConfig(row.poolCfg, row.pool_cfg);
  const apiPullItems = readApiPullItems(row.apiPullItems, row.api_pull_items, poolConfig.apiPullItems, poolConfig.api_pull_items);
  // Detail records from the registration workflow keep the endpoint settings
  // in pool_cfg.  Use the first definition only as a connection-preview
  // fallback; callers retain apiPullItems and select by the actual source table.
  const defaultApi = apiPullItems[0] ?? {};
  const apiUrl = apiRequestUrl(defaultApi) ?? pickString(
    row.apiUrl, row.api_url, row.url,
    poolConfig.apiUrl, poolConfig.api_url, poolConfig.url,
  );
  const apiMethod = pickString(
    defaultApi.endpointMethod, defaultApi.method, defaultApi.apiMethod,
    row.apiMethod, row.api_method, row.method,
    poolConfig.apiMethod, poolConfig.api_method, poolConfig.method,
  );
  const commonHeadersJson = apiHeaderJson(defaultApi) ?? pickString(
    row.commonHeadersJson, row.common_headers_json, row.requestHeadersJson, row.request_headers_json,
    poolConfig.commonHeadersJson, poolConfig.common_headers_json, poolConfig.requestHeadersJson, poolConfig.request_headers_json,
  );
  const requestBody = pickString(
    defaultApi.requestTemplateJson, defaultApi.requestBody, defaultApi.apiBody,
    row.requestBody, row.request_body, row.apiBody, row.api_body,
    poolConfig.requestBody, poolConfig.request_body, poolConfig.apiBody, poolConfig.api_body,
  );
  // Detail responses in older deployments leave protocol fields inside poolCfg;
  // retain those values rather than requiring every API revision to flatten
  // them before a canvas node can be hydrated.
  const database = pickString(
    row.database, row.databaseName, row.database_name, row.schemaName, row.schema_name,
    poolConfig.database, poolConfig.databaseName, poolConfig.database_name,
    poolConfig.serviceName, poolConfig.service_name, poolConfig.sid,
  );
  const host = pickString(
    row.host, row.hostname, row.ftpHost, row.ftp_host, row.ip,
    poolConfig.host, poolConfig.hostname, poolConfig.ftpHost, poolConfig.ftp_host, poolConfig.ip,
  );
  const label = pickString(row.label, dbName, database, host, id);
  if (!id || !label) return null;

  return {
    ...(row as DbDictItem),
    id,
    tid: pickString(row.tid, id),
    value: id,
    label,
    dbName: dbName || label,
    database,
    dbType: pickString(
      row.dbType, row.db_type, row.databaseType, row.database_type,
      poolConfig.dbType, poolConfig.db_type, poolConfig.databaseType, poolConfig.database_type,
    ),
    host,
    port: pickString(row.port, row.ftpPort, row.ftp_port, poolConfig.port, poolConfig.ftpPort, poolConfig.ftp_port),
    nodeId: pickString(row.nodeId, row.node_id),
    nodeType: pickString(row.nodeType, row.node_type),
    nodeTypeName: pickString(row.nodeTypeName, row.node_type_name, row.nodeName, row.node_name),
    username: pickString(row.username, row.userName, row.dbMetaUser, row.db_meta_user, poolConfig.username, poolConfig.userName),
    password: pickString(row.password, row.dbMetaPassword, row.db_meta_password, poolConfig.password),
    jdbcURL: pickString(row.jdbcURL, row.jdbcUrl, row.jdbc_url),
    jdbcUrl: pickString(row.jdbcUrl, row.jdbcURL, row.jdbc_url),
    connectionType: pickString(
      row.connectionType, row.connection_type, row.jdbcType, row.jdbc_type,
      poolConfig.connectionType, poolConfig.connection_type, poolConfig.jdbcType, poolConfig.jdbc_type,
    ),
    jdbcType: pickString(row.jdbcType, row.jdbc_type, poolConfig.jdbcType, poolConfig.jdbc_type),
    sid: pickString(row.sid, row.SID, poolConfig.sid, poolConfig.SID),
    serviceName: pickString(
      row.serviceName, row.service_name, row.service, poolConfig.serviceName, poolConfig.service_name, poolConfig.service,
    ),
    compatibleMode: pickString(
      row.compatibleMode, row.compatible_mode, poolConfig.compatibleMode, poolConfig.compatible_mode,
    ),
    tenant: pickString(row.tenant, row.obTenant, row.ob_tenant, poolConfig.tenant, poolConfig.obTenant, poolConfig.ob_tenant),
    clusterId: pickString(row.clusterId, row.cluster_id, poolConfig.clusterId, poolConfig.cluster_id),
    jdbcProperties: pickJdbcProperties(row),
    metadataAccessMode: pickString(row.metadataAccessMode, row.metadata_access_mode, poolConfig.metadataAccessMode, poolConfig.metadata_access_mode),
    hiveConnectionMode: pickString(row.hiveConnectionMode, row.hive_connection_mode, poolConfig.hiveConnectionMode, poolConfig.hive_connection_mode),
    hiveProfile: pickString(row.hiveProfile, row.hive_profile, poolConfig.hiveProfile, poolConfig.hive_profile),
    authMode: pickString(row.authMode, row.auth_mode, row.auth, poolConfig.authMode, poolConfig.auth_mode, poolConfig.auth),
    zookeeperQuorum: pickString(row.zookeeperQuorum, row.zookeeper_quorum, row.zkQuorum, row.zk_quorum, poolConfig.zookeeperQuorum, poolConfig.zookeeper_quorum, poolConfig.zkQuorum, poolConfig.zk_quorum),
    serviceDiscoveryMode: pickString(row.serviceDiscoveryMode, row.service_discovery_mode, poolConfig.serviceDiscoveryMode, poolConfig.service_discovery_mode),
    zookeeperNamespace: pickString(row.zookeeperNamespace, row.zooKeeperNamespace, row.zookeeper_namespace, poolConfig.zookeeperNamespace, poolConfig.zooKeeperNamespace, poolConfig.zookeeper_namespace),
    schema: pickString(row.schema, row.defaultSchema, row.currentSchema, poolConfig.schema, poolConfig.defaultSchema, poolConfig.currentSchema),
    principal: pickString(row.principal, row.hivePrincipal, poolConfig.principal, poolConfig.hivePrincipal),
    userPrincipal: pickString(row.userPrincipal, row.clientPrincipal, poolConfig.userPrincipal, poolConfig.clientPrincipal),
    keytabPath: pickString(row.keytabPath, row.keytab, poolConfig.keytabPath, poolConfig.keytab),
    krb5ConfPath: pickString(row.krb5ConfPath, row.krb5Conf, poolConfig.krb5ConfPath, poolConfig.krb5Conf),
    clientConfigDir: pickString(row.clientConfigDir, row.mrsClientConfigDir, poolConfig.clientConfigDir, poolConfig.mrsClientConfigDir),
    saslQop: pickString(row.saslQop, row['sasl.qop'], poolConfig.saslQop, poolConfig['sasl.qop']),
    ssl: pickBoolean(row.ssl, poolConfig.ssl),
    driverLocations: pickString(row.driverLocations, row.nifiDriverLocations, poolConfig.driverLocations, poolConfig.nifiDriverLocations),
    extraParams: pickString(row.extraParams, poolConfig.extraParams),
    ftpProtocol: pickString(row.ftpProtocol, row.ftp_protocol, row.protocol, poolConfig.ftpProtocol, poolConfig.ftp_protocol, poolConfig.protocol),
    protocol: pickString(row.protocol, row.ftpProtocol, row.ftp_protocol, poolConfig.protocol, poolConfig.ftpProtocol, poolConfig.ftp_protocol),
    ftpHost: pickString(row.ftpHost, row.ftp_host, row.hostname, row.host, poolConfig.ftpHost, poolConfig.ftp_host, poolConfig.hostname, poolConfig.host),
    ftpPort: pickString(row.ftpPort, row.ftp_port, row.port, poolConfig.ftpPort, poolConfig.ftp_port, poolConfig.port),
    ftpPath: pickString(row.ftpPath, row.ftp_path, row.remotePath, row.remote_path, poolConfig.ftpPath, poolConfig.ftp_path, poolConfig.remotePath, poolConfig.remote_path),
    ftpUsername: pickString(row.ftpUsername, row.ftp_username, poolConfig.ftpUsername, poolConfig.ftp_username, row.username, poolConfig.username),
    ftpPassword: pickString(row.ftpPassword, row.ftp_password, poolConfig.ftpPassword, poolConfig.ftp_password, row.password, poolConfig.password),
    ftpPassiveMode: pickBoolean(row.ftpPassiveMode, row.ftp_passive_mode, poolConfig.ftpPassiveMode, poolConfig.ftp_passive_mode),
    ftpFilePattern: pickString(row.ftpFilePattern, row.ftp_file_pattern, row.fileFilterRegex, poolConfig.ftpFilePattern, poolConfig.ftp_file_pattern, poolConfig.fileFilterRegex),
    apiUrl,
    apiMethod,
    contentType: pickString(defaultApi.contentType, row.contentType, row.content_type, poolConfig.contentType, poolConfig.content_type),
    requestBody,
    apiBody: requestBody,
    commonHeadersJson,
    apiHeaders: commonHeadersJson,
    apiCollectionPaths: pickStringOrStringArray(defaultApi.apiCollectionPaths, defaultApi.collectionPaths, poolConfig.apiCollectionPaths, poolConfig.collectionPaths),
    apiTimeoutSeconds: pickNumberOrString(defaultApi.apiTimeoutSeconds, poolConfig.apiTimeoutSeconds, poolConfig.api_timeout_seconds),
    apiPullItems,
    authHeader: pickString(row.authHeader, row.auth_header, row.authorization),
    connectTimeout: pickString(defaultApi.connectTimeout, row.connectTimeout, row.connect_timeout, row.connectionTimeout, poolConfig.connectTimeout, poolConfig.connect_timeout),
    readTimeout: pickString(defaultApi.readTimeout, row.readTimeout, row.read_timeout, poolConfig.readTimeout, poolConfig.read_timeout),
    orgPath: pickString(row.orgPath, row.org_path),
  };
}

function pickString(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (value === null || value === undefined) continue;
    const text = String(value).trim();
    if (text) return text;
  }
  return undefined;
}

function pickStringOrStringArray(...values: unknown[]): string | string[] | undefined {
  for (const value of values) {
    if (Array.isArray(value)) {
      const result = value.map((item) => String(item ?? '').trim()).filter(Boolean);
      if (result.length) return result;
    }
    const text = pickString(value);
    if (text) return text;
  }
  return undefined;
}

function pickNumberOrString(...values: unknown[]): number | string | undefined {
  for (const value of values) {
    if (typeof value === 'number' && Number.isFinite(value)) return value;
    const text = pickString(value);
    if (text) return text;
  }
  return undefined;
}

export interface MaterializeTableResult {
  status: 'created' | 'exists';
  created: boolean;
  tableName: string;
  message?: string;
}

/** A registered materialization template returned by `/ods/getTableTempalte`. */
export interface MaterializationTemplateColumn {
  columnName: string;
  columnComment?: string;
  dataType?: string;
  columnType?: string;
  typeName?: string;
  length?: number;
  precisionLength?: number;
  columnSize?: number;
  scale?: number;
  decimalDigits?: number;
  nullable?: boolean | number | null;
  primaryKey?: boolean | number | null;
  ordinalPosition?: number;
  defaultValue?: string | null;
  dictionaryTranslationField?: boolean;
  dictionarySourceField?: string;
}

/** Normalized DDL input accepted from either a physical probe or a registered template. */
export interface TargetTableDdlColumn {
  columnName: string;
  columnComment?: string;
  dataType?: string;
  columnType?: string;
  typeName?: string;
  length?: number;
  precisionLength?: number;
  columnSize?: number;
  scale?: number;
  decimalDigits?: number;
  nullable?: boolean | number | null;
  primaryKey?: boolean | number | null;
  ordinalPosition?: number;
  defaultValue?: string | null;
  dictionaryTranslationField?: boolean;
  dictionarySourceField?: string;
}

export interface MaterializationTemplate {
  propList?: {
    tableName?: string;
    sourceTableName?: string;
    [key: string]: unknown;
  };
  tableItems: MaterializationTemplateColumn[];
}

/**
 * Uses the same registered-table template as the task-management materialization dialog.
 * The server applies dictionary, mandatory-standard, and ODS system-field rules here.
 */
export async function fetchMaterializationTemplate(sourceTableId: string): Promise<MaterializationTemplate> {
  const revision = getSessionRevision();
  const headers = authHeaders();
  const { data } = await axios.post(`${dataassetsBase}/ods/getTableTempalte`, { tid: sourceTableId }, {
    headers, timeout: requestTimeout,
  });
  assertCurrentSession(revision);
  const payload = unwrapResponse<unknown>(data);
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new Error(getApiErrorMessage(data, '读取登记建表模板失败'));
  }
  const template = payload as Partial<MaterializationTemplate>;
  if (!Array.isArray(template.tableItems) || !template.tableItems.length) {
    throw new Error(getApiErrorMessage(data, '登记建表模板未返回字段'));
  }
  return { propList: template.propList, tableItems: template.tableItems };
}

export async function generateTargetTableDdl(
  dbId: string,
  tableName: string,
  columns: TargetTableDdlColumn[],
  sourceDatabaseType: string,
): Promise<string> {
  const revision = getSessionRevision();
  const headers = authHeaders();
  const { data } = await axios.post(`${dataassetsBase}/dst/database/metadata/getCreateTableDDL`, {
    canvas: true,
    sourceDatabaseType,
    propList: { dbId, tableName },
    tableItems: columns.map((column) => ({
      columnName: column.columnName, columnComment: column.columnComment,
      dataType: column.dataType, columnType: column.columnType || column.typeName || column.dataType,
      length: column.length ?? column.columnSize,
      precisionLength: column.precisionLength ?? column.columnSize,
      scale: column.scale ?? column.decimalDigits,
      nullable: column.nullable !== false && column.nullable !== 0,
      primaryKey: column.primaryKey === true || column.primaryKey === 1,
      ordinalPosition: column.ordinalPosition,
    })),
  }, { headers, timeout: requestTimeout });
  assertCurrentSession(revision);
  const ddl = unwrapResponse<unknown>(data);
  if (typeof ddl !== 'string' || !ddl.trim()) throw new Error(getApiErrorMessage(data, '生成建表语句失败'));
  return ddl;
}

/** Same physical CREATE capability used by /ods/createTapleApply. */
export async function materializeTargetTable(dbId: string, ddl: string): Promise<MaterializeTableResult> {
  const revision = getSessionRevision();
  const headers = authHeaders();
  const { data } = await axios.post(`${dataassetsBase}/dst/database/metadata/createTable`,
    { dbId, ddl, canvas: true }, { headers, timeout: requestTimeout });
  assertCurrentSession(revision);
  const result = unwrapResponse<Partial<MaterializeTableResult> | null>(data);
  if (!result || !['created', 'exists'].includes(String(result.status)) || !result.tableName) {
    throw new Error(getApiErrorMessage(data, '物化建表未返回有效结果'));
  }
  return result as MaterializeTableResult;
}

function pickBoolean(...values: unknown[]): boolean | undefined {
  for (const value of values) {
    if (value === null || value === undefined || value === '') continue;
    if (typeof value === 'boolean') return value;
    const text = String(value).trim().toLowerCase();
    if (text === 'true' || text === '1') return true;
    if (text === 'false' || text === '0') return false;
  }
  return undefined;
}

function readPoolConfig(...values: unknown[]): Record<string, unknown> {
  for (const value of values) {
    if (!value) continue;
    if (typeof value === 'object' && !Array.isArray(value)) return value as Record<string, unknown>;
    if (typeof value === 'string') {
      try {
        const parsed = JSON.parse(value);
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) return parsed as Record<string, unknown>;
      } catch {
        // A malformed historical pool_cfg must not make the whole dictionary entry unusable.
      }
    }
  }
  return {};
}

function readApiPullItems(...values: unknown[]): ApiPullItem[] {
  for (const value of values) {
    let parsed = value;
    if (typeof parsed === 'string') {
      try {
        parsed = JSON.parse(parsed);
      } catch {
        continue;
      }
    }
    if (!Array.isArray(parsed)) continue;
    return parsed.filter((item): item is ApiPullItem => Boolean(item) && typeof item === 'object' && !Array.isArray(item));
  }
  return [];
}

function apiRequestUrl(item: ApiPullItem): string | undefined {
  const direct = pickString(item.requestUrl, item.url, item.apiUrl);
  if (direct) return direct;
  const baseUrl = pickString(item.baseUrl);
  const endpointPath = pickString(item.endpointPath);
  if (!baseUrl) return endpointPath;
  if (!endpointPath) return baseUrl;
  return `${baseUrl.replace(/\/+$/, '')}${endpointPath.startsWith('/') ? endpointPath : `/${endpointPath}`}`;
}

function apiHeaderJson(item: ApiPullItem): string | undefined {
  const raw = item.requestHeadersJson ?? item.commonHeadersJson ?? item.requestHeaders;
  if (typeof raw === 'string') return raw.trim() || undefined;
  if (!raw) return undefined;
  try {
    if (Array.isArray(raw)) {
      const headers: Record<string, string> = {};
      raw.forEach((entry) => {
        const name = pickString(entry.key, entry.name);
        if (name) headers[name] = String(entry.value ?? '');
      });
      return Object.keys(headers).length ? JSON.stringify(headers) : undefined;
    }
    return JSON.stringify(raw);
  } catch {
    return undefined;
  }
}

function pickJdbcProperties(row: Record<string, any>): Record<string, string> | undefined {
  const values: Record<string, string> = {};
  const addProperties = (candidate: unknown) => {
    if (!candidate || typeof candidate !== 'object' || Array.isArray(candidate)) return;
    for (const [rawKey, rawValue] of Object.entries(candidate as Record<string, unknown>)) {
      const key = rawKey.trim();
      const value = rawValue === null || rawValue === undefined ? '' : String(rawValue).trim();
      if (/^[A-Za-z][A-Za-z0-9_-]*(?:\.[A-Za-z][A-Za-z0-9_-]*)+$/.test(key) && value) {
        values[key] = value;
      }
    }
  };
  addProperties(row.jdbcProperties);
  addProperties(row.poolCfg);
  if (typeof row.pool_cfg === 'string') {
    try {
      addProperties(JSON.parse(row.pool_cfg));
    } catch {
      // Keep the regular datasource fields usable when a historical pool_cfg is malformed.
    }
  } else {
    addProperties(row.pool_cfg);
  }
  addProperties(row);
  return Object.keys(values).length ? values : undefined;
}

function filterByOrgPath(list: DbDictItem[]): DbDictItem[] {
  if (isChengtianIntegration || !list.length || getIsAdmin()) return list;
  const orgPath = getOrgPath();
  return orgPath ? list.filter((item) => String(item.orgPath || '').startsWith(orgPath)) : list;
}


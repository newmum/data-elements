import { platformApi } from '../shared/platformApi';

export type SourceOption = { value: string; label: string; orgId?: string; children?: SourceOption[] };
export type SourceChoices = { types: SourceOption[]; networks: SourceOption[]; organizations: SourceOption[]; applications: SourceOption[]; nodes: SourceOption[] };
export type SourceDraft = Record<string, unknown> & { dbName: string; dbType: string; appId?: string; orgId?: string; storageDomain?: string };
export type ConnectionResult = { connected?: boolean; success?: boolean; error?: string; message?: string; elapsedMs?: number };
export type DeleteResult = { deleted?: boolean; exists?: boolean; alreadyDeleted?: boolean; cleanup?: Record<string, number>; projectionFailures?: unknown[] };
type Field = { name: string; label: string; required?: boolean; secret?: boolean; wide?: boolean };
const field = (name: string, label: string, required = false, secret = false): Field => ({ name, label, required, secret });

/** The connector property names are exactly those accepted by the platform save/test API. */
export function connectionFields(type: string, hiveMode?: unknown): Field[] {
  switch (type.toLowerCase()) {
    case 'maxcompute': return [field('maxcomputeEndpoint', 'Endpoint', true), field('maxcomputeProject', '项目名称', true), field('maxcomputeAccessKeyId', 'AccessKey ID', true), field('maxcomputeAccessKeySecret', 'AccessKey Secret', true, true), field('maxcomputeTunnelEndpoint', 'Tunnel Endpoint')];
    case 'minio': return [field('minioEndpoint', '服务地址', true), field('minioBucket', 'Bucket', true), field('minioAccessKey', 'Access Key', true), field('minioSecretKey', 'Secret Key', true, true), field('minioRegion', '区域'), field('minioPrefix', '路径前缀'), field('minioFilePattern', '文件匹配规则')];
    case 'ftp': case 'sftp': return [field('ftpHost', '服务器地址', true), field('ftpPort', '端口', true), field('ftpPath', '目录路径', true), field('ftpUsername', '用户名', true), field('ftpPassword', '密码', true, true), field('ftpFilePattern', '文件匹配规则')];
    case 'api': return [field('apiUrl', '接口地址', true), field('apiMethod', '请求方法', true), field('apiToken', '认证令牌', false, true), field('apiHeaders', '请求头（JSON）'), field('apiBody', '请求体（JSON）')];
    case 'kafka': return [field('kafkaBootstrapServers', '服务地址', true), field('kafkaTopic', '主题', true), field('kafkaGroupId', '消费组'), field('kafkaSecurityProtocol', '安全协议'), field('kafkaSaslMechanism', 'SASL 机制'), field('kafkaUsername', '用户名'), field('kafkaPassword', '密码', false, true)];
    default: {
      const managedHive = type.toLowerCase() === 'hive' && hiveMode === 'huawei-mrs-hive-jdbc';
      const fields = [field('host', '主机地址', !managedHive), field('port', '端口', !managedHive), field('database', '数据库 / 实例名', !managedHive), field('username', '用户名', !managedHive), field('password', '密码', false, true), { ...field('jdbcURL', 'JDBC 连接地址', !managedHive), wide: true }];
      if (type.toLowerCase() === 'oracle') fields.push(field('jdbcType', 'Oracle 连接方式'), field('serviceName', '服务名 / SID'));
      if (['postgres', 'postgresql', 'gaussdb', 'kingbase8', 'vertica'].includes(type.toLowerCase())) fields.push(field('schema', 'Schema'));
      if (type.toLowerCase() === 'hive' && hiveMode !== 'huawei-mrs-hive-jdbc') fields.push(field('dbMetaType', '元数据库类型', true), field('dbMetaIp', '元数据库地址', true), field('dbMetaPort', '元数据库端口', true), field('dbMetaDbName', '元数据库实例名', true), field('dbMetaUser', '元数据库用户名', true), field('dbMetaPassword', '元数据库密码', false, true));
      fields.push(field('dbVersion', '数据库版本'));
      return fields;
    }
  }
}

export function makeJdbcUrl(values: SourceDraft): string {
  const host = String(values.host || '').trim(); const port = String(values.port || '').trim();
  const database = String(values.database || '').trim();
  if (!host || !port || !database) return '';
  const address = `${host.includes(':') && !host.startsWith('[') ? `[${host}]` : host}:${port}`;
  switch (values.dbType.toLowerCase()) {
    case 'mysql': case 'oceanbase-mysql': return `jdbc:mysql://${address}/${database}`;
    case 'postgres': case 'postgresql': case 'gaussdb': return `jdbc:postgresql://${address}/${database}`;
    case 'kingbase8': return `jdbc:kingbase8://${address}/${database}`;
    case 'sqlserver': return `jdbc:sqlserver://${address};databaseName=${database}`;
    case 'oracle': return values.jdbcType === 'sid' ? `jdbc:oracle:thin:@${address}:${values.serviceName || database}` : `jdbc:oracle:thin:@//${address}/${values.serviceName || database}`;
    case 'hive': return `jdbc:hive2://${address}/${database}`;
    case 'vertica': return `jdbc:vertica://${address}/${database}`;
    case 'dameng': return `jdbc:dm://${address}`;
    case 'gbase8a': return `jdbc:gbase://${address}/${database}`;
    default: return '';
  }
}

function rows(value: unknown): Record<string, unknown>[] {
  if (Array.isArray(value)) return value as Record<string, unknown>[];
  if (!value || typeof value !== 'object') return [];
  const item = value as Record<string, unknown>;
  return rows(item.list ?? item.rows ?? item.data);
}
export function sourceOptions(value: unknown): SourceOption[] {
  return rows(value).map(row => ({ value: String(row.value ?? row.tid ?? row.id ?? row.dictCode ?? ''), label: String(row.label ?? row.appName ?? row.name ?? row.dictName ?? ''), orgId: row.orgId ? String(row.orgId) : undefined, children: sourceOptions(row.children) })).filter(row => row.value && row.label);
}
export function leafOptions(options: SourceOption[]): SourceOption[] {
  return [...new Map(options.flatMap(option => option.children?.length ? leafOptions(option.children) : [option]).map(option => [option.value, option])).values()];
}
export function organizationOptions(value: unknown): SourceOption[] {
  const all = rows(value).filter(row => String(row.id ?? row.tid) !== 'ROOT');
  const nodes = new Map(all.map(row => [String(row.id ?? row.tid), { value: String(row.id ?? row.tid), label: String(row.name ?? row.label ?? ''), children: [] as SourceOption[] }]));
  const roots: SourceOption[] = [];
  for (const row of all) {
    const node = nodes.get(String(row.id ?? row.tid))!;
    const parent = nodes.get(String(row.parentId ?? ''));
    if (parent && parent !== node) parent.children.push(node); else roots.push(node);
  }
  return roots;
}
const baseFields = ['dbName', 'dbType', 'appId', 'orgId', 'storageDomain', 'nodeId', 'assetDesc', 'contactName', 'contactPhone', 'isEnable'];
export function sourceProperties(draft: SourceDraft): Record<string, unknown> {
  const permitted = [...baseFields, ...connectionFields(draft.dbType, draft.hiveConnectionMode).map(item => item.name)];
  const props: Record<string, unknown> = {};
  for (const name of permitted) {
    const value = draft[name];
    // Omitted secret fields preserve the existing pool_cfg; they are never exported or persisted in browser storage.
    const secret = connectionFields(draft.dbType, draft.hiveConnectionMode).find(item => item.name === name)?.secret;
    if (value !== undefined && !(secret && !value)) props[name] = typeof value === 'string' && !secret ? value.trim() : value;
  }
  // 'explore' is the platform's 数据抽取方式; 'capture' is its different scheduled capture mode.
  return { ...props, SSWL: draft.storageDomain, accessMode: 'explore', dataAccessMode: 'explore', showConnect: 1, preserveSourceOrg: true };
}
export function draftFromDetail(detail: Record<string, unknown>): SourceDraft {
  const draft: SourceDraft = { dbName: String(detail.dbName || ''), dbType: String(detail.dbType || detail.db_type || '') };
  for (const name of [...baseFields, ...connectionFields(draft.dbType, detail.hiveConnectionMode).map(item => item.name)]) {
    const value = detail[name];
    if (value != null && !connectionFields(draft.dbType, detail.hiveConnectionMode).find(item => item.name === name)?.secret) draft[name] = value;
  }
  draft.storageDomain = String(detail.storageDomain || detail.SSWL || '');
  draft.hiveConnectionMode = detail.hiveConnectionMode;
  // Only a boolean marker enters the editor; existing credentials are not rendered back to the client form.
  draft.storedSecretFields = connectionFields(draft.dbType, detail.hiveConnectionMode).filter(item => item.secret && Boolean(detail[item.name])).map(item => item.name);
  return draft;
}

export const datasourceRegistrationApi = {
  async choices(): Promise<SourceChoices> {
    const [dict, applications, organizations] = await Promise.all([
      platformApi<Record<string, { options?: unknown }>>('/sym/dict'),
      // Same tenant-scoped registered application/ES list as the platform business-system page.
      platformApi<unknown>('/dst/application/list', {}),
      platformApi<unknown>('/sym/org/pageOrg'),
    ]);
    return { types: leafOptions(sourceOptions(dict.dbType?.options)), networks: leafOptions(sourceOptions(dict.storageDomain?.options)), organizations: organizationOptions(organizations), applications: sourceOptions(applications), nodes: leafOptions(sourceOptions(dict.nodeType?.options)) };
  },
  detail: (tid: string) => platformApi<Record<string, unknown>>('/dst/database/detail', { tid }),
  // Commit the authoritative record first. ES projection is refreshed separately
  // after the editor closes, using the platform's existing deferred-save contract.
  save: (draft: SourceDraft, tid?: string) => platformApi<{ tid: string; indexRefreshPending?: boolean }>('/dst/database/saveOrUpdate', { tid: tid || '', assetType: 'db', deferIndexRefresh: true, propList: { ...sourceProperties(draft), ...(!tid ? { assetStatus: 2 } : {}) } }),
  async refreshIndex(tid: string): Promise<void> {
    const result = await platformApi<{ refreshed?: boolean; removed?: boolean }>('/dst/maintenance/refresh', { tid, assetType: 'db' }, { timeoutMs: 120000 });
    if (result.refreshed !== true) throw new Error(result.removed ? '数据源主记录已不存在，请刷新列表核对' : '索引接口未确认同步成功，请重试同步');
  },
  test: (draft: SourceDraft, tid?: string) => platformApi<ConnectionResult>('/dst/database/metadata/test-connection', { ...sourceProperties(draft), ...(tid ? { tid } : {}) }, { timeoutMs: 120000 }),
  testSaved: (tid: string) => platformApi<ConnectionResult>('/dst/database/metadata/test-connection', { tid }, { timeoutMs: 120000 }),
  deletePreview: (tid: string) => platformApi<DeleteResult>('/dst/database/delete', { tid, dryRun: true }),
  remove: (tid: string) => platformApi<DeleteResult>('/dst/database/delete', { tid }),
};

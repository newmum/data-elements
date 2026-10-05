import { PlatformApiError, platformApi } from './platformApi';

export type AccessTask = {
  tid: string;
  taskName?: string;
  taskDesc?: string;
  taskStatus?: number | string;
  pipelineId?: string;
  recordSource?: string;
  sourceDbName?: string;
  sourceTableName?: string;
  sourceTableSummary?: string;
  targetDbName?: string;
  targetTableName?: string;
  targetTableSummary?: string;
  scheduleFrequency?: string;
  monitorStatus?: string;
  monitorMsg?: string;
  lastRunning?: string;
  endRunning?: string;
  syncTables?: Array<Record<string, unknown>>;
  [key: string]: unknown;
};

export type AccessTable = {
  tid?: string;
  tableId?: string;
  sourceTableId?: string;
  tableName?: string;
  tableNameCn?: string;
  tableComment?: string;
  datasourceName?: string;
  datasourceId?: string;
  dataCatalogId?: string;
  catalogId?: string;
  sourceCatalogId?: string;
  [key: string]: unknown;
};

export type ReconciliationPolicy = {
  tid: string;
  policyName?: string;
  policy_name?: string;
  accessTaskId?: string;
  access_task_id?: string;
  compareMode?: string;
  compare_mode?: string;
  triggerMode?: string;
  trigger_mode?: string;
  status?: number | string;
  lastRunStatus?: string;
  last_run_status?: string;
  consistencyRate?: number | string;
  consistency_rate?: number | string;
  [key: string]: unknown;
};

export type ReconciliationRun = {
  tid: string;
  policyName?: string;
  policy_name?: string;
  triggerType?: string;
  trigger_type?: string;
  status?: string;
  sourceCount?: number | string;
  source_count?: number | string;
  targetCount?: number | string;
  target_count?: number | string;
  matchedCount?: number | string;
  matched_count?: number | string;
  diffCount?: number | string;
  diff_count?: number | string;
  consistencyRate?: number | string;
  consistency_rate?: number | string;
  createdTime?: string | number;
  created_time?: string | number;
  [key: string]: unknown;
};

export type PageResult<T> = { list: T[]; total: number };

export type RuntimeOverview = {
  total: number;
  running: number;
  failed: number;
  flows: Array<Record<string, unknown>>;
  legacy?: boolean;
};

export type AccessMonitorSnapshot = {
  tid: string;
  taskId?: string;
  monitorTime?: string;
  monitorStatus?: string;
  monitorMsg?: string;
  delayLevel?: string;
  queuedCount?: number;
  flowFilesIn?: number;
  flowFilesOut?: number;
  bytesIn?: number;
  bytesOut?: number;
  activeThreadCount?: number;
};

export type MonitorTrendPoint = {
  bucketTime: string;
  sampleCount: number;
  flowFilesIn: number;
  flowFilesOut: number;
  bytesIn: number;
  bytesOut: number;
  queuedCountMax: number;
};

export type MonitorTrend = {
  interval: 'hour';
  metric: 'SUM_OF_TASK_HOURLY_SNAPSHOT_PEAKS';
  beginTime: string;
  endTime: string;
  points: MonitorTrendPoint[];
};

export type DistributionTask = Record<string, unknown> & { tid: string };

export function asPage<T>(value: unknown): PageResult<T> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('数据中台分页接口返回格式无效');
  const item = value as Record<string, unknown>;
  const list = Array.isArray(item.list) ? item.list as T[] : Array.isArray(item.records) ? item.records as T[] : null;
  if (!list) throw new Error('数据中台分页接口缺少记录列表');
  const total = Number(item.total ?? item.count ?? list.length);
  if (!Number.isFinite(total) || total < 0) throw new Error('数据中台分页接口总数无效');
  return { list, total };
}

/** These are the existing shared data-elements Magic API and NiFi routes. */
export const integrationPlatformService = {
  async taskPage(query: { page?: number; size?: number; status?: string; keyword?: string } = {}): Promise<PageResult<AccessTask>> {
    return asPage<AccessTask>(await platformApi('/ods/task/page', {
      taskStatus: query.status || 'all', taskName: query.keyword || '', pageNum: query.page || 1, pageSize: query.size || 20,
    }));
  },
  async sourceTablePage(query: { page?: number; size?: number; keyword?: string } = {}): Promise<PageResult<AccessTable>> {
    return asPage<AccessTable>(await platformApi('/ods/dataAggPage', {
      viewLevel: 'table', scopeType: 'all', scopeId: '', pageNum: query.page || 1, pageSize: query.size || 20,
      keyword: query.keyword || '', conditions: [{ field: 'assetType', value: 'table', type: 'match' }],
    }));
  },
  async ensureTask(source: AccessTable): Promise<{ taskId?: string; pipelineId?: string; task?: AccessTask; created?: boolean; repairRequired?: boolean; repairReasons?: string[] }> {
    const tableId = String(source.sourceTableId || source.tableId || source.tid || '').trim();
    if (!tableId) throw new Error('缺少来源数据表标识，无法创建接入任务');
    return platformApi('/ods/dataAggTaskEnsure', { tableId, catalogId: source.sourceCatalogId || source.catalogId || source.dataCatalogId || '' });
  },
  bindApiPullTask: (datasourceId: string, sourceTableId: string, pipelineId: string) =>
    platformApi<{ applicable?: boolean }>('/ods/api-pull/bind-task', { datasourceId, sourceTableId, pipelineId }),
  runtimeList: () => platformApi<Array<Record<string, unknown>>>('/nifi/api/pipelines/runtime-list'),
  async runtimeOverview(): Promise<RuntimeOverview> {
    try {
      return await platformApi<RuntimeOverview>('/nifi/api/pipelines/runtime-overview');
    } catch (cause) {
      if (!(cause instanceof PlatformApiError) || cause.status !== 404) throw cause;
      // During a rolling upgrade the previous backend still has the full runtime list.
      const flows = await integrationPlatformService.runtimeList();
      return {
        total: flows.length,
        running: flows.filter(row => String(row.currentStatus || '').toUpperCase() === 'RUNNING').length,
        failed: flows.filter(row => ['RUN_ERROR', 'DEPLOY_FAILED'].includes(String(row.currentStatus || '').toUpperCase())).length,
        flows: flows.slice(0, 5),
        legacy: true,
      };
    }
  },
  async monitorSnapPage(query: { taskId: string; page?: number; size?: number; beginTime?: string; endTime?: string }): Promise<PageResult<AccessMonitorSnapshot>> {
    return asPage<AccessMonitorSnapshot>(await platformApi('/ods/task/monitorSnapPage', {
      taskId: query.taskId, pageNum: query.page || 1, pageSize: query.size || 20,
      beginTime: query.beginTime || '', endTime: query.endTime || '',
    }));
  },
  monitorTrend: (beginTime: string, endTime: string) => platformApi<MonitorTrend>('/ods/task/monitor/trend', { beginTime, endTime, interval: 'hour' }),
  async distributionTaskPage(query: { page?: number; size?: number; keyword?: string; status?: string } = {}): Promise<PageResult<DistributionTask>> {
    return asPage<DistributionTask>(await platformApi('/ods/distribution/task/page', {
      pageNum: query.page || 1, pageSize: query.size || 20, keyword: query.keyword || '', status: query.status || '',
    }));
  },
  distributionTaskDetail: (tid: string) => platformApi<DistributionTask>('/ods/distribution/task/detail', { tid }),
  pipelineStatus: (pipelineId: string) => platformApi<Record<string, unknown>>(`/nifi/api/pipelines/${encodeURIComponent(pipelineId)}/status`),
  pipelineErrors: (pipelineId: string) => platformApi<Array<Record<string, unknown>>>(`/nifi/api/pipelines/${encodeURIComponent(pipelineId)}/errors`),
  async reconciliationPolicyPage(query: { page?: number; size?: number; keyword?: string } = {}): Promise<PageResult<ReconciliationPolicy>> {
    return asPage<ReconciliationPolicy>(await platformApi('/ods/reconciliation/policy/page', { page: query.page || 1, size: query.size || 20, keyword: query.keyword || '' }));
  },
  async reconciliationRunPage(query: { page?: number; size?: number; keyword?: string; status?: string } = {}): Promise<PageResult<ReconciliationRun>> {
    return asPage<ReconciliationRun>(await platformApi('/ods/reconciliation/run/page', { page: query.page || 1, size: query.size || 20, keyword: query.keyword || '', status: query.status || '' }));
  },
  reconciliationSummary: () => platformApi<Record<string, unknown>>('/ods/reconciliation/summary', {}),
  reconciliationAccessTasks: () => platformApi<Array<Record<string, unknown>>>('/ods/reconciliation/access-tasks', {}),
  saveReconciliationPolicy: (body: Record<string, unknown>) => platformApi<Record<string, unknown>>('/ods/reconciliation/policy/save', body),
  policyPrecheck: (policyId: string) => platformApi<Record<string, unknown>>('/ods/reconciliation/policy/precheck', { policyId }),
  manualReconciliation: (policyId: string) => platformApi<Record<string, unknown>>('/ods/reconciliation/run/manual', { policyId, requestId: `${policyId}-${Date.now()}` }),
};

import { platformApi } from './platformApi';

export type BatchDatasource = Record<string, unknown> & { tid: string; dbName?: string; dbType?: string };
export type BatchTarget = Record<string, unknown> & { targetTableId?: string; targetTableName?: string; targetDbId?: string; targetDbName?: string };
export type BatchTable = Record<string, unknown> & {
  tid: string; sourceTableId?: string; datasourceId?: string; datasourceName?: string;
  tableName?: string; tableNameCn?: string; sourceCatalogId?: string;
  targetTables?: BatchTarget[]; relationAmbiguous?: boolean;
  taskCount?: number; accessTaskId?: string;
};
export type EnsureTaskResult = Record<string, unknown> & {
  created?: boolean; taskId?: string; pipelineId?: string; repairRequired?: boolean; repairReasons?: string[];
};
export type Page<T> = { list: T[]; total: number };

const page = <T>(result: unknown): Page<T> => {
  if (!result || typeof result !== 'object' || !Array.isArray((result as Record<string, unknown>).list))
    throw new Error('数据中台的库表分页接口返回格式无效');
  const response = result as { list: T[]; total?: number };
  const total = Number(response.total ?? response.list.length);
  if (!Number.isFinite(total) || total < 0) throw new Error('数据中台的库表总数无效');
  return { list: response.list, total };
};

/** Tenant and data-source eligibility are enforced by the existing Magic APIs. */
export const batchPlatformService = {
  async sourceDatabases(): Promise<BatchDatasource[]> {
    const result = page<BatchDatasource>(await platformApi('/ods/batchCreateJob', { action: 'sourceOptions' }));
    if (result.total > 200) throw new Error('当前租户来源库超过 200 个；请先在数据中台缩小可选范围');
    return result.list;
  },
  sourceTables: (datasourceId: string, pageNum: number, pageSize = 20): Promise<Page<BatchTable>> => {
    if (!Number.isInteger(pageNum) || pageNum < 1 || !Number.isInteger(pageSize) || pageSize < 1 || pageSize > 100)
      throw new Error('来源表分页参数无效');
    return platformApi('/ods/batchCreateJob', {
      action: 'preview', datasourceId, pageNum, pageSize,
    }).then(page<BatchTable>);
  },
  async targetDatabases(): Promise<BatchDatasource[]> {
    return page<BatchDatasource>(await platformApi('/ods/batchCreateJob', { action: 'targetOptions' })).list;
  },
  ensureTask: (sourceTableId: string, catalogId = '') => {
    if (!sourceTableId.trim()) throw new Error('缺少来源表 ID');
    return platformApi<EnsureTaskResult>('/ods/dataAggTaskEnsure', { tableId: sourceTableId, catalogId });
  },
};

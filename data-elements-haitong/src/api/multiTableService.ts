import { asPage, type PageResult } from './integrationPlatformService';
import { platformApi } from './platformApi';

/** A group coordinates existing single-table tasks; it is not a NiFi pipeline. */
export type MultiTableItem = {
  itemId: string;
  sourceTableId: string;
  sourceTableName?: string;
  sourceTableNameCn?: string;
  taskId: string;
  taskName?: string;
  taskAvailable?: boolean;
  taskStatus?: number | null;
  pipelineId?: string | null;
  monitorStatus?: string | null;
  monitorMessage?: string | null;
  lastRunning?: string | null;
  endRunning?: string | null;
  sortNo: number;
};

export type MultiTableGroup = {
  groupId: string;
  groupName: string;
  createdBy?: string;
  canManage?: boolean;
  createdTime?: string;
  updatedTime?: string;
  items: MultiTableItem[];
};

export type MultiTableCandidate = {
  taskId: string;
  taskName?: string;
  taskStatus?: number | null;
  pipelineId?: string | null;
  sourceTableId: string;
  sourceTableName?: string;
  sourceTableNameCn?: string;
  datasourceName?: string;
};

export const multiTableService = {
  async candidates(query: { page?: number; size?: number; keyword?: string } = {}): Promise<PageResult<MultiTableCandidate>> {
    return asPage<MultiTableCandidate>(await platformApi('/ods/task/multi/candidates', {
      pageNum: query.page || 1, pageSize: query.size || 10, keyword: query.keyword || '',
    }));
  },
  async page(query: { page?: number; size?: number; keyword?: string } = {}): Promise<PageResult<MultiTableGroup>> {
    const result = await platformApi<unknown>('/ods/task/multi/page', {
      pageNum: query.page || 1, pageSize: query.size || 20, keyword: query.keyword || '',
    });
    return asPage<MultiTableGroup>(result);
  },
  save: (groupId: string, groupName: string, taskIds: string[]) =>
    platformApi<{ groupId: string; created: boolean; tableCount: number }>('/ods/task/multi/save', { groupId, groupName, taskIds }),
  remove: (groupId: string) =>
    platformApi<{ groupId: string; deleted: boolean; memberTasksPreserved: boolean }>('/ods/task/multi/delete', { groupId }),
  relink: (groupId: string, sourceTableId: string, taskId: string) =>
    platformApi<{ linked: boolean }>('/ods/task/multi/relink', { groupId, sourceTableId, taskId }),
};

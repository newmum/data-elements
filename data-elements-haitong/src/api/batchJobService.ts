import { platformApi } from './platformApi';

export type BatchItemStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';
export type BatchJobStatus = 'RUNNING' | 'SUCCEEDED' | 'PARTIAL' | 'FAILED' | 'STOPPED';
export type BatchJob = {
  jobId: string; requestKey: string; jobName: string; sourceDbId: string; targetDbId: string;
  status: BatchJobStatus; stopRequested: number; totalCount: number;
  createdTime: string; updatedTime: string; finishedTime?: string | null;
};
export type BatchJobItem = {
  itemId: string; jobId: string; itemIndex: number;
  sourceTableId: string; sourceTableName: string; sourceCatalogId?: string | null;
  targetTableId: string; targetTableName: string;
  status: BatchItemStatus; attemptCount: number;
  taskId?: string | null; wasExisting?: number | null; errorMessage?: string | null;
  claimedTime?: string | null; completedTime?: string | null;
};
export type BatchJobDetail = { job: BatchJob; items: BatchJobItem[] };
export type BatchClaim = {
  done: boolean; busy?: boolean; stopped?: boolean; status?: BatchJobStatus;
  item?: BatchJobItem; leaseToken?: string;
};
export type BatchCreateRequest = {
  requestKey: string; jobName: string; sourceDbId: string; targetDbId: string;
  matchRule: { removePrefix: string; addPrefix: string; addSuffix: string };
  items: Array<{ sourceTableId: string; targetTableId: string }>;
};

const path = '/ods/batchCreateJob';
const call = <T>(action: string, body: Record<string, unknown> = {}) =>
  platformApi<T>(path, { action, ...body });

export const batchJobService = {
  list: async () => (await call<{ jobs: BatchJob[] }>('list')).jobs,
  detail: (jobId: string) => call<BatchJobDetail>('detail', { jobId }),
  create: (request: BatchCreateRequest) => call<BatchJobDetail>('create', request),
  claim: (jobId: string) => call<BatchClaim>('claim', { jobId }),
  complete: (jobId: string, itemId: string, leaseToken: string, result: {
    success: boolean; taskId?: string; existing?: boolean; error?: string;
  }) => call<BatchJobDetail>('complete', { jobId, itemId, leaseToken, ...result }),
  stop: (jobId: string) => call<BatchJobDetail>('stop', { jobId }),
  retry: (jobId: string) => call<BatchJobDetail>('retry', { jobId }),
};

import { platformApi } from './platformApi';
import { asPage, type PageResult } from './integrationPlatformService';

export type ReconcilePolicy = Record<string, unknown> & { tid: string };
export type ReconcileRun = Record<string, unknown> & { tid: string };
export type ReconcileDiff = Record<string, unknown> & { tid: string };
export type ReconcileAccessTask = Record<string, unknown> & { tid: string };
export type ReconcileDiffExport = {
  runId: string;
  total: number;
  limit: number;
  filename: string;
  rows: Array<{
    businessKey: string;
    diffType: string;
    fieldName: string;
    sourceValueMasked: string;
    targetValueMasked: string;
  }>;
};

export const value = (row: Record<string, unknown>, ...keys: string[]): unknown => {
  for (const key of keys) if (row[key] !== undefined && row[key] !== null) return row[key];
  return undefined;
};
export const str = (row: Record<string, unknown>, ...keys: string[]): string => String(value(row, ...keys) ?? '');
export const num = (row: Record<string, unknown>, ...keys: string[]): number => {
  const result = Number(value(row, ...keys) ?? 0);
  return Number.isFinite(result) ? result : 0;
};
export const enabled = (row: Record<string, unknown>): boolean => {
  const flag = value(row, 'enabled', 'is_enabled');
  return flag === true || flag === 1 || flag === '1';
};

export const reconciliationLive = {
  async policies(page = 1, size = 200, keyword = ''): Promise<PageResult<ReconcilePolicy>> {
    return asPage(await platformApi('/ods/reconciliation/policy/page', { page, size, keyword }));
  },
  async runs(page = 1, size = 200, status = '', keyword = ''): Promise<PageResult<ReconcileRun>> {
    return asPage(await platformApi('/ods/reconciliation/run/page', { page, size, status, keyword }));
  },
  async diffs(runId: string, page = 1, size = 200): Promise<PageResult<ReconcileDiff>> {
    return asPage(await platformApi('/ods/reconciliation/run/diff/page', { runId, page, size }));
  },
  exportDiffs: (runId: string) => platformApi<ReconcileDiffExport>('/ods/reconciliation/run/diff/export', { runId }),
  accessTasks: () => platformApi<ReconcileAccessTask[]>('/ods/reconciliation/access-tasks', {}),
  policyDetail: (policyId: string) => platformApi<ReconcilePolicy>('/ods/reconciliation/policy/detail', { policyId }),
  runDetail: (runId: string) => platformApi<ReconcileRun>('/ods/reconciliation/run/detail', { runId }),
  savePolicy: (body: Record<string, unknown>) => platformApi<ReconcilePolicy>('/ods/reconciliation/policy/save', body),
  precheck: (policyId: string) => platformApi<Record<string, unknown>>('/ods/reconciliation/policy/precheck', { policyId }),
  enable: (policyId: string, isEnabled: boolean) => platformApi<boolean>('/ods/reconciliation/policy/enable', { policyId, enabled: isEnabled }),
  manualRun: (policyId: string) => platformApi<ReconcileRun>('/ods/reconciliation/run/manual', { policyId, requestId: `${policyId}-${Date.now()}` }),
};

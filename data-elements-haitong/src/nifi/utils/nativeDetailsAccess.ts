import type { FlowStatusPayload } from '../api/pipelines';

type NativeStatus = Pick<FlowStatusPayload, 'id' | 'status' | 'deployed' | 'processGroupId'>;

/** Native details inspect the existing deployment; starting the saved version has separate checks. */
export function nativeDetailsAccess(
  pipelineId: string | null,
  status: NativeStatus | undefined,
  query: { isPending: boolean; isError: boolean; error?: unknown },
): { canOpen: boolean; tooltip: string } {
  if (!pipelineId) return { canOpen: false, tooltip: '请先保存并部署当前任务' };
  const failedStatus = (query.error as { response?: { status?: number } } | null)?.response?.status;
  if (query.isError && (failedStatus === 403 || failedStatus === 404)) {
    return { canOpen: false, tooltip: '当前任务不存在或无访问权限，请重新从任务列表打开' };
  }
  if (query.isError && failedStatus === 401) {
    return { canOpen: false, tooltip: '登录状态已失效，请重新登录' };
  }
  if (status?.id === pipelineId && status.deployed && status.processGroupId?.trim()) {
    return { canOpen: true, tooltip: '在 NiFi 原生页面中查看当前任务' };
  }
  if (query.isError) return { canOpen: false, tooltip: '无法确认 NiFi 部署状态，请稍后重试' };
  if (query.isPending || status?.id !== pipelineId) {
    return { canOpen: false, tooltip: '正在确认 NiFi 部署状态' };
  }
  if (status.status === 'DEPLOYING') return { canOpen: false, tooltip: '当前任务正在部署到 NiFi，请稍候' };
  if (['RUNNING', 'RUN_ERROR', 'STOPPING'].includes(status.status)) {
    return { canOpen: false, tooltip: '运行记录缺少 NiFi 流程关联，请刷新或检查任务关联' };
  }
  if (status.status === 'DEPLOY_FAILED') {
    return { canOpen: false, tooltip: '部署未成功，请打开“错误与告警”查看原因' };
  }
  return { canOpen: false, tooltip: '当前任务尚未部署到 NiFi，请先点击“保存”' };
}

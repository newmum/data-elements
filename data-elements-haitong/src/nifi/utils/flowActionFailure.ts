import type { FlowError } from '../api/pipelines';

export type FlowAction = 'save' | 'deploy' | 'start' | 'stop';
const labels: Record<FlowAction, string> = { save: '保存', deploy: '部署', start: '启动', stop: '停止' };
let sequence = 0;
const object = (value: unknown): Record<string, any> | undefined =>
  value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, any> : undefined;

/** Keep diagnostics useful without displaying connection credentials or an entire proxy HTML page. */
export function diagnosticText(value: unknown): string {
  if (value == null) return '';
  let text = typeof value === 'string' ? value : JSON.stringify(value);
  if (/<(?:!doctype|html|head|body)\b/i.test(text)) {
    text = text.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
  }
  return text
    .replace(/((?:["']?)(?:password|passwd|pwd|token|access_token|authorization|secret)(?:["']?)\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\s,;&}]+)/gi, '$1[已隐藏]')
    .replace(/(Bearer\s+)[\w.+\/-]+/gi, '$1[已隐藏]')
    .replace(/(\/\/)[^\s/:@]+:[^\s/@]+@/g, '$1[已隐藏]@')
    .slice(0, 12000);
}

/** A failed response is itself evidence, even if polling has not persisted/returned an error yet. */
export function flowActionFailure(error: unknown, action: FlowAction): FlowError {
  const cause = object(error);
  const response = object(cause?.response);
  const original = response?.data ?? cause?.payload ?? error;
  let body = object(original);
  for (let i = 0; i < 5 && body && !body.flowError; i++) {
    const nested = object(body.data ?? body.result);
    if (!nested) break;
    body = nested;
  }
  const stored = object(body?.flowError);
  const nestedError = object(body?.error);
  const description = diagnosticText(stored?.message ?? body?.message ?? body?.msg ?? nestedError?.message ?? body?.error
    ?? (typeof original === 'string' ? original : cause?.message));
  const timeout = ['ECONNABORTED', 'ETIMEDOUT'].includes(cause?.code);
  const network = !response && cause?.code === 'ERR_NETWORK';
  const message = timeout ? `${labels[action]}请求超时，执行结果尚未确认`
    : network ? `${labels[action]}请求未收到响应，请检查网络或后端服务`
    : description || `${labels[action]}失败，接口未返回错误说明`;
  const details = [stored?.detail, body?.detail, body?.body,
    !stored && description !== message ? description : '',
    response?.status ? `HTTP ${response.status}` : '',
    body?.code ?? body?.errorCode ? `错误码：${body?.code ?? body?.errorCode}` : '',
    body?.traceId ?? body?.requestId ? `跟踪号：${body?.traceId ?? body?.requestId}` : '',
    cause?.config?.url ? `请求：${String(cause.config.method ?? 'POST').toUpperCase()} ${String(cause.config.url).split('?')[0]}` : '',
  ].map(diagnosticText).filter(Boolean);
  return {
    id: typeof stored?.id === 'string' ? stored.id : `request-${Date.now()}-${++sequence}`,
    level: 'ERROR', phase: stored?.phase === 'VALIDATION' ? 'VALIDATION' : action === 'save' ? 'VALIDATION' : 'DEPLOYMENT',
    nodeId: stored?.nodeId, nodeLabel: stored?.nodeLabel, fieldKey: stored?.fieldKey,
    message, detail: [...new Set(details)].join('\n'),
    suggestion: diagnosticText(stored?.suggestion) || (timeout || network
      ? '先刷新流程状态确认是否已完成，再决定是否重试；不要连续重复提交。'
      : action === 'deploy' ? '画布设计已保存，请根据错误详情修复后重新部署。' : undefined),
    occurredAt: typeof stored?.occurredAt === 'string' ? stored.occurredAt : new Date().toISOString(),
  };
}

export function mergeFlowErrors(remote: FlowError[], local: FlowError[]): FlowError[] {
  const result = [...remote];
  for (const item of local) {
    if (!result.some(row => row.id === item.id || (row.phase === item.phase && row.message === item.message
      && row.detail === item.detail && Math.abs(Date.parse(row.occurredAt) - Date.parse(item.occurredAt)) < 60000))) result.push(item);
  }
  return result.sort((a, b) => Date.parse(b.occurredAt) - Date.parse(a.occurredAt));
}

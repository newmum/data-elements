import { getBridgeToken, getSessionRevision, waitForToken } from './bridgeSession';
export { readDiagnosticStream } from './diagnosticStream';
export type { DiagnosticEvidence, DiagnosticEvent } from './diagnosticStream';
export type DiagnosticSelection = { source: 'nifi'; pipelineId: string; errorId: string } | { source: 'client'; clientError: string };
const base = (import.meta.env.VITE_DATA_ELEMENTS_API_BASE_URL || (import.meta.env.DEV ? '/dev-api' : '/prod-api')).replace(/\/$/, '');
export const diagnosticUrl = (suffix = '') => base + '/sym/ai/diagnostics' + suffix;
export async function diagnosticRequest(suffix: string, method: string, body: unknown, signal: AbortSignal) {
  const revision = getSessionRevision(), token = await waitForToken();
  if (revision !== getSessionRevision() || token !== getBridgeToken() || signal.aborted) throw new DOMException('登录会话已切换', 'AbortError');
  const response = await fetch(diagnosticUrl(suffix), { method, headers: { token, 'Content-Type': 'application/json', Accept: 'text/event-stream, application/json' },
    body: body === undefined ? undefined : JSON.stringify(body), signal, cache: 'no-store' });
  if (revision !== getSessionRevision() || token !== getBridgeToken()) { await response.body?.cancel(); throw new DOMException('登录会话已切换', 'AbortError'); }
  return response;
}
export async function diagnosticJson(suffix: string, method: string, body: unknown, signal: AbortSignal) {
  const response = await diagnosticRequest(suffix, method, body, signal);
  const result = await response.json();
  if (!response.ok || result.success === false) throw new Error(result.message || result.msg || '诊断请求失败');
  return result;
}

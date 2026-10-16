export interface DiagnosticEvidence { id: string; source: string; observedAt: string; content: string }
export interface DiagnosticEvent { event: string; data: Record<string, unknown> | DiagnosticEvidence[] }

/** Strict SSE framing: never replay a partial answer or treat an EOF as completion. */
export async function readDiagnosticStream(response: Response, signal: AbortSignal, onEvent: (event: DiagnosticEvent) => void) {
  if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream') || !response.body) {
    let message = '排错服务暂不可用';
    try { const body = await response.json(); message = body.message || body.msg || message; } catch { /* safe generic error */ }
    throw new Error(message);
  }
  const reader = response.body.getReader(), decoder = new TextDecoder();
  let buffer = '', complete = false;
  try {
    while (!signal.aborted) {
      const { value, done } = await reader.read();
      if (signal.aborted) throw new DOMException('已停止', 'AbortError');
      if (done) break;
      buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '');
      if (buffer.length > 262144) throw new Error('排错响应超过限制');
      let end: number;
      while ((end = buffer.indexOf('\n\n')) >= 0) {
        const frame = buffer.slice(0, end); buffer = buffer.slice(end + 2);
        const lines = frame.split('\n');
        const event = lines.find(line => line.startsWith('event:'))?.slice(6).trim() || 'message';
        const data = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n');
        if (!data) continue;
        const payload = JSON.parse(data);
        if (event === 'error') throw new Error(payload.message || '生成中断，已返回内容仅供参考');
        onEvent({ event, data: payload });
        if (event === 'done') { complete = true; return; }
      }
    }
    if (signal.aborted) throw new DOMException('已停止', 'AbortError');
    if (!complete) throw new Error('连接已中断，回答可能不完整；请手动重试');
  } finally { await reader.cancel().catch(() => {}); reader.releaseLock(); }
}

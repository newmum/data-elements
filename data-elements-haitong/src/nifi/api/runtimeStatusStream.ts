import { apiClient } from './client';
import { getBridgeToken, getSessionRevision } from './bridgeSession';

type RuntimeEvent = { revision: string; observedAt: number; fresh: boolean; message: string; transportAvailable?: boolean };
const listeners = new Set<(event: RuntimeEvent) => void>();
let active: AbortController | null = null;
let timer: ReturnType<typeof setTimeout> | undefined;
let retry = 1000;

async function connect() {
  if (active || !listeners.size || document.hidden || !getBridgeToken()) return;
  const controller = new AbortController(), revision = getSessionRevision();
  let stalled = false, watchdog: ReturnType<typeof setTimeout> | undefined;
  const armWatchdog = () => { clearTimeout(watchdog); watchdog = setTimeout(() => { stalled = true; controller.abort(); }, 20000); };
  armWatchdog();
  active = controller;
  try {
    const response = await fetch(apiClient.getUri({ url: '/task-runtime/events' }), {
      headers: { token: getBridgeToken()!, Accept: 'text/event-stream' }, signal: controller.signal, cache: 'no-store',
    });
    if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream') || !response.body) throw new Error('实时状态连接不可用');
    retry = 1000;
    const reader = response.body.getReader(), decoder = new TextDecoder();
    let buffer = '';
    try {
      while (revision === getSessionRevision()) {
        const { value, done } = await reader.read(); if (done) break;
        armWatchdog();
        buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '');
        if (buffer.length > 65536) throw new Error('实时状态消息过大');
        let end: number;
        while ((end = buffer.indexOf('\n\n')) >= 0) {
          const event = buffer.slice(0, end); buffer = buffer.slice(end + 2);
          const data = event.split('\n').filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n');
          if (data && !controller.signal.aborted && revision === getSessionRevision()) {
            const payload = JSON.parse(data) as RuntimeEvent;
            listeners.forEach(listener => listener(payload));
          }
        }
      }
    } finally { reader.releaseLock(); }
  } catch {
    if ((!controller.signal.aborted || stalled) && revision === getSessionRevision()) {
      listeners.forEach(listener => listener({ revision: 'stream-unavailable', observedAt: Date.now(), fresh: false,
        message: '实时连接中断，正在自动重连', transportAvailable: false }));
    }
  }
  finally {
    clearTimeout(watchdog);
    controller.abort();
    if (active === controller) active = null;
    if (listeners.size && !document.hidden && revision === getSessionRevision()) {
      timer = setTimeout(connect, retry + Math.random() * 300); retry = Math.min(30000, retry * 2);
    }
  }
}
function pause() { clearTimeout(timer); active?.abort(); active = null; }
function visibility() { if (document.hidden) pause(); else void connect(); }

/** One shared connection for Toolbar, Canvas and error/status panels in this frame. */
export function subscribeRuntimeStatus(listener: (event: RuntimeEvent) => void) {
  listeners.add(listener);
  if (listeners.size === 1) document.addEventListener('visibilitychange', visibility);
  void connect();
  return () => {
    listeners.delete(listener);
    if (!listeners.size) { document.removeEventListener('visibilitychange', visibility); pause(); }
  };
}

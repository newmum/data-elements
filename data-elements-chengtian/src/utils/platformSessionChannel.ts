import { AuthStorage } from './auth';

// BroadcastChannel is restricted by the browser to the platform's own origin.
// This shares an active non-remembered session between platform tabs and its
// bridge iframe, without persisting credentials or exposing them in a URL.
const channel = typeof BroadcastChannel === 'undefined' ? undefined : new BroadcastChannel('data-elements:platform-session');
const waiters = new Map<string, (token: string) => void>();
let sessionEpoch = 0;
function clearSession() {
  sessionEpoch++;
  AuthStorage.clearAuth();
  for (const finish of [...waiters.values()]) finish('');
  window.dispatchEvent(new Event('data-elements:platform-logout'));
}
/** Clear both remembered and per-tab credentials, including other platform tabs. */
export function clearPlatformSession() {
  clearSession();
  channel?.postMessage({ type: 'logout', nonce: crypto.randomUUID() });
}
channel?.addEventListener('message', event => {
  const data = event.data;
  if (typeof data?.nonce !== 'string') return;
  if (data.type === 'logout') {
    clearSession();
  } else if (data.type === 'request') {
    const token = AuthStorage.getAccessToken();
    if (token) channel.postMessage({ type: 'response', nonce: data.nonce, token });
  } else if (data.type === 'response' && typeof data.token === 'string' && data.token) {
    waiters.get(data.nonce)?.(data.token);
  }
});

export async function activePlatformToken(): Promise<string> {
  const token = AuthStorage.getAccessToken();
  if (token || !channel) return token;
  return new Promise(resolve => {
    const epoch = sessionEpoch;
    const nonce = crypto.randomUUID();
    const finish = (value: string) => { clearTimeout(timeout); waiters.delete(nonce); resolve(epoch === sessionEpoch ? value : ''); };
    const timeout = setTimeout(() => finish(''), 750);
    waiters.set(nonce, finish);
    channel.postMessage({ type: 'request', nonce });
  });
}

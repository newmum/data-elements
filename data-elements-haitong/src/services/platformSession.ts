import { setBridgeToken } from '../nifi/api/bridgeSession';

/** The platform owns login and tenant selection. HaiTong never persists a second credential. */
export function getPortalBaseUrl(): URL {
  const configured = import.meta.env.VITE_SOURCE_PLATFORM_HOME_URL?.trim();
  const fallback = import.meta.env.DEV ? 'http://localhost:3000/#/home' : '/wanxiang/#/home';
  return new URL(configured || fallback, window.location.href);
}

/** The platform validates this return address against its configured capability centres. */
export function getPlatformLoginUrl(): string {
  const portal = getPortalBaseUrl();
  const destination = new URL(window.location.href);
  for (const key of ['token', 'accessToken']) destination.searchParams.delete(key);
  const hash = destination.hash.slice(1);
  const queryStart = hash.indexOf('?');
  if (queryStart >= 0) {
    const params = new URLSearchParams(hash.slice(queryStart + 1));
    for (const key of ['token', 'accessToken']) params.delete(key);
    destination.hash = `${hash.slice(0, queryStart)}${params.size ? `?${params}` : ''}`;
  }
  portal.hash = `/login?redirect=${encodeURIComponent(destination.href)}`;
  return portal.href;
}

const LOGIN_REDIRECT_KEY = 'haitong:platform-login-redirect';
let loginRedirectPending = false;

/** Avoid a redirect loop if the platform sends us straight back with the same invalid session. */
export function redirectToPlatformLogin(): boolean {
  if (loginRedirectPending || window.parent !== window) return false;
  const destination = new URL(window.location.href);
  const route = `${destination.pathname}${destination.search}${destination.hash}`;
  try {
    const previous = JSON.parse(sessionStorage.getItem(LOGIN_REDIRECT_KEY) || 'null') as { route?: string; at?: number } | null;
    if (previous && Date.now() - Number(previous.at) < 60_000) return false;
    sessionStorage.setItem(LOGIN_REDIRECT_KEY, JSON.stringify({ route, at: Date.now() }));
  } catch { /* Storage may be disabled; the in-memory guard still prevents repeated redirects. */ }
  loginRedirectPending = true;
  const loginUrl = getPlatformLoginUrl();
  setBridgeToken(null);
  window.location.replace(loginUrl);
  return true;
}

function sameOriginToken(): string {
  try {
    const remember = localStorage.getItem('remember_me') === 'true';
    const raw = (remember ? localStorage : sessionStorage).getItem('token') || '';
    if (!raw) return '';
    try {
      const parsed: unknown = JSON.parse(raw);
      return typeof parsed === 'string' ? parsed.trim() : '';
    } catch {
      return raw.trim();
    }
  } catch {
    return '';
  }
}

let bridge: Promise<HTMLIFrameElement> | undefined;
let loggingOut: Promise<void> | undefined;

function platformBridge(portal: URL): Promise<HTMLIFrameElement> {
  if (!bridge) bridge = new Promise((resolve, reject) => {
    const frame = document.createElement('iframe');
    frame.hidden = true;
    frame.title = '数据中台登录态同步';
    frame.referrerPolicy = 'no-referrer';
    const timeout = window.setTimeout(() => {
      frame.remove();
      bridge = undefined;
      reject(new Error('无法连接数据中台会话，请检查平台地址后重试'));
    }, 10_000);
    frame.onload = () => { window.clearTimeout(timeout); resolve(frame); };
    frame.src = new URL('capability-session.html', portal).href;
    document.body.appendChild(frame);
  });
  return bridge;
}

/** Request the current platform token through its existing exact-origin bridge. */
export async function getPlatformSessionToken(): Promise<string> {
  if (loggingOut) { await loggingOut; throw new Error('正在退出登录'); }
  const portal = getPortalBaseUrl();
  if (portal.origin === window.location.origin) return sameOriginToken();
  const frame = await platformBridge(portal);
  return new Promise((resolve, reject) => {
    const nonce = crypto.randomUUID();
    const dispose = () => {
      window.clearTimeout(timeout);
      window.removeEventListener('message', receive);
    };
    const receive = (event: MessageEvent) => {
      if (event.origin !== portal.origin || event.source !== frame.contentWindow ||
          event.data?.type !== 'data-elements:session-response' || event.data.nonce !== nonce) return;
      dispose();
      resolve(typeof event.data.token === 'string' ? event.data.token.trim() : '');
    };
    const timeout = window.setTimeout(() => {
      dispose();
      frame.remove();
      bridge = undefined;
      reject(new Error('平台会话同步失败，请返回数据中台登录后重试'));
    }, 8_000);
    window.addEventListener('message', receive);
    frame.contentWindow?.postMessage({ type: 'data-elements:session-request', nonce }, portal.origin);
  });
}

/** Ask the platform to invalidate the shared server session, then return to its login page. */
export function logoutPlatformSession(): Promise<void> {
  if (loggingOut) return loggingOut;
  loggingOut = (async () => {
    const portal = getPortalBaseUrl();
    const frame = await platformBridge(portal);
    await new Promise<void>((resolve, reject) => {
      const nonce = crypto.randomUUID();
      const dispose = () => { window.clearTimeout(timeout); window.removeEventListener('message', receive); };
      const receive = (event: MessageEvent) => {
        if (event.origin !== portal.origin || event.source !== frame.contentWindow ||
            event.data?.type !== 'data-elements:logout-response' || event.data.nonce !== nonce) return;
        dispose();
        if (event.data.success === true) resolve();
        else reject(new Error(event.data.message || '退出登录失败，请重试'));
      };
      const timeout = window.setTimeout(() => { dispose(); frame.remove(); bridge = undefined; reject(new Error('退出登录服务响应超时，请重试')); }, 30_000);
      window.addEventListener('message', receive);
      frame.contentWindow?.postMessage({ type: 'data-elements:logout-request', nonce }, portal.origin);
    });
    setBridgeToken(null);
    window.location.replace(getPlatformLoginUrl());
  })().catch(error => { loggingOut = undefined; throw error; });
  return loggingOut;
}

let syncEpoch = 0;

/** Keep the original NiFi request client in step with the platform session. */
export async function syncNifiPlatformSession(): Promise<void> {
  // Embedded integrations keep their existing INIT/SET_TOKEN contract.
  if (window.parent !== window) return;
  const epoch = ++syncEpoch;
  try {
    const token = await getPlatformSessionToken();
    if (epoch === syncEpoch) setBridgeToken(token || null);
  } catch (error) {
    // A failed refresh must not leave a previous tenant's credential active.
    if (epoch === syncEpoch) setBridgeToken(null);
    throw error;
  }
}

export function startPlatformSessionSync(): () => void {
  if (window.parent !== window) return () => {};
  const refresh = () => { void syncNifiPlatformSession().catch(() => { /* Requests display their own session error. */ }); };
  const onVisible = () => { if (document.visibilityState === 'visible') refresh(); };
  refresh();
  window.addEventListener('focus', refresh);
  document.addEventListener('visibilitychange', onVisible);
  return () => {
    window.removeEventListener('focus', refresh);
    document.removeEventListener('visibilitychange', onVisible);
  };
}

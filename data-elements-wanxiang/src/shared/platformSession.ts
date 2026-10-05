export const dataPlatformHomeUrl = (): string => import.meta.env.VITE_DATA_ELEMENTS_PLATFORM_URL?.trim()
  || import.meta.env.VITE_SOURCE_PLATFORM_HOME_URL?.trim()
  // The platform resolves its root to the account's accessible landing menu.
  // /home is not a guaranteed dynamic route (for example, in the broadcast tenant).
  || (import.meta.env.DEV ? 'http://localhost:3000/#/' : '/wanxiang/#/');

export function isAuthenticationFailure(code: unknown, message: unknown): boolean {
  return Number(code) === 401 || Number(code) === 100120 || /user_no_login|not[ _-]?login|未登录|登录.*(?:失效|过期)|token.*(?:expired|invalid)/i.test(String(message || ''));
}

let redirecting = false;
export function redirectToPlatformLogin() {
  if (redirecting) return;
  redirecting = true;
  const target = new URL(dataPlatformHomeUrl(), location.href);
  target.hash = `/login?redirect=${encodeURIComponent(location.href)}`;
  location.replace(target.href);
}

let bridge: Promise<HTMLIFrameElement> | undefined;
let pending: Promise<string> | undefined;
let loggingOut: Promise<void> | undefined;
async function sessionBridge(): Promise<HTMLIFrameElement> {
  const platform = new URL(dataPlatformHomeUrl(), location.href);
  if (!bridge) bridge = new Promise((resolve, reject) => {
    const frame = document.createElement('iframe');
    frame.hidden = true;
    frame.title = '数据中台登录态同步';
    frame.referrerPolicy = 'no-referrer';
    const timeout = setTimeout(() => { frame.remove(); bridge = undefined; reject(new Error('无法连接数据中台登录服务，请检查服务后重试')); }, 10000);
    frame.onload = () => { clearTimeout(timeout); resolve(frame); };
    frame.src = new URL('capability-session.html', platform).href;
    document.body.appendChild(frame);
  });
  return bridge;
}
async function readSession(): Promise<string> {
  const platform = new URL(dataPlatformHomeUrl(), location.href);
  if (platform.origin === location.origin) {
    const storage = localStorage.getItem('remember_me') === 'true' ? localStorage : sessionStorage;
    const raw = storage.getItem('token') || '';
    try { const value: unknown = JSON.parse(raw); return typeof value === 'string' ? value : ''; } catch { return raw; }
  }
  const frame = await sessionBridge();
  return new Promise((resolve, reject) => {
    const nonce = crypto.randomUUID();
    const dispose = () => { clearTimeout(timeout); removeEventListener('message', receive); };
    const receive = (event: MessageEvent) => {
      if (event.origin !== platform.origin || event.source !== frame.contentWindow || event.data?.type !== 'data-elements:session-response' || event.data.nonce !== nonce) return;
      dispose();
      resolve(typeof event.data.token === 'string' ? event.data.token : '');
    };
    const timeout = setTimeout(() => { dispose(); frame.remove(); bridge = undefined; reject(new Error('登录态同步超时，请检查数据中台地址后重试')); }, 8000);
    addEventListener('message', receive);
    frame.contentWindow?.postMessage({ type: 'data-elements:session-request', nonce }, platform.origin);
  });
}

/** Deduplicate concurrent bridge requests; never keep a stale token across requests. */
export async function requirePlatformSessionToken(): Promise<string> {
  if (loggingOut) { await loggingOut; throw new Error('正在退出登录'); }
  if (!pending) pending = readSession().finally(() => { pending = undefined; });
  const token = await pending;
  if (!token) { redirectToPlatformLogin(); throw new Error('正在前往数据中台登录'); }
  return token;
}

/** The trusted platform bridge invalidates the server session and clears all
 * platform tabs. No credentials are copied into the center's own storage. */
export function logoutPlatformSession(): Promise<void> {
  if (loggingOut) return loggingOut;
  loggingOut = (async () => {
    // Finish any outstanding token read before sending a logout bridge request.
    if (pending) await pending.catch(() => undefined);
    const platform = new URL(dataPlatformHomeUrl(), location.href);
    const frame = await sessionBridge();
    await new Promise<void>((resolve, reject) => {
      const nonce = crypto.randomUUID();
      const dispose = () => { clearTimeout(timeout); removeEventListener('message', receive); };
      const receive = (event: MessageEvent) => {
        if (event.origin !== platform.origin || event.source !== frame.contentWindow || event.data?.type !== 'data-elements:logout-response' || event.data.nonce !== nonce) return;
        dispose();
        if (event.data.success === true) resolve();
        else reject(new Error(event.data.message || '退出登录失败，请重试'));
      };
      const timeout = setTimeout(() => { dispose(); frame.remove(); bridge = undefined; reject(new Error('退出登录服务响应超时，请重试')); }, 30000);
      addEventListener('message', receive);
      frame.contentWindow?.postMessage({ type: 'data-elements:logout-request', nonce }, platform.origin);
    });
    redirectToPlatformLogin();
  })().catch(error => { loggingOut = undefined; throw error; });
  return loggingOut;
}

import { activePlatformToken, clearPlatformSession } from './platformSessionChannel';

export async function logoutPlatformSession(): Promise<void> {
  const token = await activePlatformToken();
  if (token) {
    const base = (import.meta.env.VITE_APP_BASE_API || '/dev-api').replace(/\/$/, '');
    const response = await fetch(`${base}/portal/logout`, {
      method: 'POST', headers: { token, 'Content-Type': 'application/json' },
      body: '{}', cache: 'no-store', signal: AbortSignal.timeout(20000),
    });
    if (response.status !== 401) {
      if (!response.ok) throw new Error(`退出登录失败（${response.status}），请重试`);
      const result = await response.json();
      const message = String(result.message || result.msg || '退出登录失败，请重试');
      const expired = [401, 100120].includes(Number(result.code)) || /user_no_login|未登录|登录.*(?:失效|过期)/i.test(message);
      if (!expired && (result.success === false || ![0, 1, 200].includes(Number(result.code)))) throw new Error(message);
    }
  }
  // Do not clear a working session after a network or server error.
  clearPlatformSession();
}

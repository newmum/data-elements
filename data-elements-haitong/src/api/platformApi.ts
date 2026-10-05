import { getPlatformSessionToken, redirectToPlatformLogin } from '../services/platformSession';
import { getBridgeToken, getSessionRevision } from '../nifi/api/bridgeSession';

const baseUrl = (import.meta.env.VITE_DATA_ELEMENTS_API_BASE_URL
  || (import.meta.env.DEV ? '/dev-api' : '/prod-api')).replace(/\/$/, '');

export class PlatformApiError extends Error {
  constructor(public readonly path: string, message: string, public readonly status?: number) {
    super(message);
    this.name = 'PlatformApiError';
  }
}

export function isPlatformAuthenticationFailure(body: unknown, status?: number): boolean {
  if (status === 401) return true;
  if (!body || typeof body !== 'object' || Array.isArray(body)) return false;
  const value = body as Record<string, unknown>;
  if ([401, 100120].includes(Number(value.code ?? value.status))) return true;
  const message = String(value.msg ?? value.message ?? value.error ?? '');
  return /user_no_login|未登录|登录.{0,8}(?:失效|过期)/i.test(message);
}

function unwrap<T>(response: unknown, path: string): T {
  if (!response || typeof response !== 'object' || Array.isArray(response)) return response as T;
  const body = response as Record<string, unknown>;
  if (isPlatformAuthenticationFailure(body)) {
    redirectToPlatformLogin();
    throw new PlatformApiError(path, '数据中台登录已失效，正在前往登录页', 401);
  }
  if (body.success === false || (body.code !== undefined && Number(body.code) !== 0 && Number(body.code) !== 200)) {
    throw new PlatformApiError(path, String(body.msg || body.message || '接口操作失败'));
  }
  return ('data' in body ? body.data : response) as T;
}

/** Shared platform contract. Tenant identity comes only from the backend token session. */
export async function platformApi<T>(path: string, body?: unknown): Promise<T> {
  if (!path.startsWith('/') || path.startsWith('//')) throw new PlatformApiError(path, '接口路径无效');
  const token = await getPlatformSessionToken();
  if (!token) {
    redirectToPlatformLogin();
    throw new PlatformApiError(path, '数据中台登录已失效，正在前往登录页', 401);
  }
  const sessionRevision = getSessionRevision();
  if (getBridgeToken() && getBridgeToken() !== token) throw new PlatformApiError(path, '登录会话已切换，请重试', 401);
  const assertCurrentSession = () => {
    if (getSessionRevision() !== sessionRevision) throw new PlatformApiError(path, '登录会话已切换，旧请求结果已丢弃', 401);
  };
  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method: body === undefined ? 'GET' : 'POST',
      headers: { token, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      body: body === undefined ? undefined : JSON.stringify(body),
      cache: 'no-store',
      signal: AbortSignal.timeout(20000),
    });
  } catch (cause) {
    assertCurrentSession();
    const timedOut = cause instanceof DOMException && cause.name === 'TimeoutError';
    throw new PlatformApiError(path, timedOut ? '请求超时，请稍后重试' : '无法连接数据中台，请检查网络或服务状态');
  }
  assertCurrentSession();
  if (!response.ok) {
    if (response.status === 401) redirectToPlatformLogin();
    throw new PlatformApiError(path, response.status === 401 ? '数据中台登录已失效，正在前往登录页' : `接口请求失败（${response.status}）`, response.status);
  }
  try {
    const data: unknown = await response.json();
    assertCurrentSession();
    return unwrap<T>(data, path);
  }
  catch (cause) {
    if (cause instanceof PlatformApiError) throw cause;
    throw new PlatformApiError(path, '接口返回格式无法识别');
  }
}

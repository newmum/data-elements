import { dataPlatformHomeUrl, isAuthenticationFailure, redirectToPlatformLogin, requirePlatformSessionToken } from './platformSession';
export { dataPlatformHomeUrl };

const apiBase = (import.meta.env.VITE_DATA_ELEMENTS_API_BASE_URL
  || (import.meta.env.DEV ? '/dev-api' : '/prod-api')).replace(/\/$/, '');

export class PlatformApiError extends Error {
  constructor(public readonly path: string, message: string, public readonly status?: number, public readonly timedOut = false) {
    super(message);
    this.name = 'PlatformApiError';
  }
}


function unwrap(result: unknown, path: string): unknown {
  if (!result || typeof result !== 'object' || Array.isArray(result)) return result;
  const response = result as Record<string, unknown>;
  if (response.success === false || (response.code !== undefined && response.code !== 0 && response.code !== 200)) {
    const message = String(response.message || response.msg || response.error || '接口操作失败');
    if (isAuthenticationFailure(response.code, message)) redirectToPlatformLogin();
    throw new PlatformApiError(path, /login|未登录/i.test(message) ? '尚未登录数据中台，请先返回数据中台登录' : message);
  }
  const value = 'data' in response ? response.data : response;
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const nested = value as Record<string, unknown>;
    // Connection tests return a domain result (connected:false), not a failed HTTP envelope.
    const qualityPrecheck = path === '/dwm/quality/task/precheck' && typeof nested.success === 'boolean';
    if ((nested.success === false && typeof nested.connected !== 'boolean' && !qualityPrecheck) || (typeof nested.code === 'number' && nested.code !== 0 && nested.code !== 200)) return unwrap(nested, path);
  }
  return value;
}

/** Shared data-elements request contract. Business tenant identity always comes from the server session. */
export interface PlatformRequestOptions { timeoutMs?: number; signal?: AbortSignal; method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'; headers?: Record<string, string>; }
export async function platformApi<T>(path: string, body?: unknown, options?: PlatformRequestOptions): Promise<T> {
  const token = await requirePlatformSessionToken();
  if (!token) throw new PlatformApiError(path, '尚未登录数据中台，请先返回数据中台登录');
  let response: Response;
  try {
    response = await fetch(`${apiBase}${path}`, {
      method: options?.method ?? (body === undefined ? 'GET' : 'POST'),
      headers: { ...options?.headers, token, ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: options?.signal ? AbortSignal.any([options.signal, AbortSignal.timeout(options.timeoutMs ?? 20000)]) : AbortSignal.timeout(options?.timeoutMs ?? 20000),
      cache: 'no-store',
    });
  } catch (cause) {
    if (options?.signal?.aborted) throw cause;
    const timedOut = cause instanceof DOMException && cause.name === 'TimeoutError';
    throw new PlatformApiError(path, timedOut ? '请求超时，请稍后重试' : '无法连接数据中台，请检查网络或服务状态', undefined, timedOut);
  }
  if (response.status === 401) redirectToPlatformLogin();
  if (!response.ok) {
    let detail = '';
    try { const error = await response.json(); detail = String(error.message || error.msg || error.error || ''); } catch { /* A proxy may return HTML instead of an API envelope. */ }
    throw new PlatformApiError(path, response.status === 401 ? '登录已失效，请返回数据中台重新登录' : detail || `接口请求失败（${response.status}）`, response.status);
  }
  try { return unwrap(await response.json(), path) as T; }
  catch (cause) {
    if (cause instanceof PlatformApiError) throw cause;
    throw new PlatformApiError(path, '接口返回格式无法识别');
  }
}

/** Authenticated GET variant for established Magic APIs that expose query parameters. */
export async function platformApiGet<T>(path: string, query: Record<string, string | number | boolean | undefined | null> = {}, options?: { timeoutMs?: number; signal?: AbortSignal }): Promise<T> {
  const entries = Object.entries(query).filter(([, value]) => value !== undefined && value !== null && value !== '');
  const suffix = entries.length ? `${path}${path.includes('?') ? '&' : '?'}${new URLSearchParams(entries.map(([key, value]) => [key, String(value)])).toString()}` : path;
  return platformApi<T>(suffix, undefined, options);
}

/** Upload a user-selected file to the same authenticated platform backend. */
export async function platformUpload<T>(path: string, form: FormData, signal?: AbortSignal): Promise<T> {
  const token = await requirePlatformSessionToken();
  if (!token) throw new PlatformApiError(path, '尚未登录数据中台，请先返回数据中台登录');
  let response: Response;
  try {
    response = await fetch(`${apiBase}${path}`, { method: 'POST', headers: { token }, body: form,
      signal: signal ? AbortSignal.any([signal, AbortSignal.timeout(30000)]) : AbortSignal.timeout(30000), cache: 'no-store' });
  } catch (cause) {
    if (signal?.aborted) throw cause;
    throw new PlatformApiError(path, '文件上传失败，请检查网络连接');
  }
  if (response.status === 401) redirectToPlatformLogin();
  let body: unknown;
  try { body = await response.json(); } catch { throw new PlatformApiError(path, `接口返回格式无法识别（${response.status}）`, response.status); }
  if (!response.ok) {
    const detail = body && typeof body === 'object' ? body as Record<string, unknown> : {};
    throw new PlatformApiError(path, String(detail.message || detail.msg || `文件导入失败（${response.status}）`), response.status);
  }
  return unwrap(body, path) as T;
}

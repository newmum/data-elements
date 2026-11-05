import { getPlatformSessionToken, redirectToPlatformLogin,reconcilePlatformSessionToken } from '../services/platformSession';
import { getSessionRevision } from '../nifi/api/bridgeSession';
import { PlatformApiError, platformResponseError } from './platformError';
export { PlatformApiError } from './platformError';

const platformEnv = import.meta.env ?? {};
const baseUrl = (platformEnv.VITE_DATA_ELEMENTS_API_BASE_URL
  || (platformEnv.DEV ? '/dev-api' : '/prod-api')).replace(/\/$/, '');

export function isPlatformAuthenticationFailure(body: unknown, status?: number): boolean {
  if (status === 401) return true;
  if (!body || typeof body !== 'object' || Array.isArray(body)) return false;
  const value = body as Record<string, unknown>;
  if ([401, 100120].includes(Number(value.code ?? value.status))) return true;
  const message = String(value.msg ?? value.message ?? value.error ?? '');
  return /user_no_login|未登录|登录.{0,8}(?:失效|过期)/i.test(message);
}

function unwrap<T>(response: unknown, path: string, method: string, status: number): T {
  if (!response || typeof response !== 'object' || Array.isArray(response)) return response as T;
  const body = response as Record<string, unknown>;
  if (isPlatformAuthenticationFailure(body)) {
    redirectToPlatformLogin();
    throw new PlatformApiError(path, '数据中台登录已失效，正在前往登录页', 401);
  }
  if (body.success === false || (body.code !== undefined && Number(body.code) !== 0 && Number(body.code) !== 200)) {
    throw platformResponseError(body, path, '接口操作失败', status, method);
  }
  return ('data' in body ? body.data : response) as T;
}

export interface PlatformUploadProgress { loaded: number; total?: number; complete: boolean }
interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT';
  timeoutMs?: number;
  signal?: AbortSignal;
  onUploadProgress?: (progress: PlatformUploadProgress) => void;
}

/** Native upload byte events; completion of upload is distinct from server delivery. */
function uploadRequest(url: string, body: FormData, headers: Record<string, string>, options: RequestOptions, current: () => boolean): Promise<Response> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const cleanup = () => options.signal?.removeEventListener('abort', abort);
    const fail = (error: Error) => { cleanup(); reject(error); };
    const abort = () => xhr.abort();
    xhr.open(options.method || 'POST', url);
    xhr.timeout = options.timeoutMs ?? 20000;
    for (const [name, value] of Object.entries(headers)) xhr.setRequestHeader(name, value);
    const progress = (event: ProgressEvent, complete: boolean) => {
      if (current() && !options.signal?.aborted) options.onUploadProgress?.({ loaded: event.loaded, total: event.lengthComputable ? event.total : undefined, complete });
    };
    xhr.upload.onprogress = event => progress(event, false);
    xhr.upload.onload = event => progress(event, true);
    xhr.onload = () => { cleanup(); resolve(new Response(xhr.responseText || null, { status: xhr.status })); };
    xhr.onerror = () => fail(new TypeError('Network request failed'));
    xhr.ontimeout = () => fail(new DOMException('Upload timed out', 'TimeoutError'));
    xhr.onabort = () => fail(new DOMException('Upload aborted', 'AbortError'));
    if (options.signal?.aborted) { fail(new DOMException('Upload aborted', 'AbortError')); return; }
    options.signal?.addEventListener('abort', abort, { once: true });
    xhr.send(body);
  });
}

/** Shared platform contract. Tenant identity comes only from the backend token session. */
export async function platformApi<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  if (!path.startsWith('/') || path.startsWith('//')) throw new PlatformApiError(path, '接口路径无效');
  const sessionRevision = getSessionRevision();
  const token = await getPlatformSessionToken();
  if(getSessionRevision()!==sessionRevision)throw new PlatformApiError(path,'登录会话已切换，旧请求结果已丢弃',401);
  if (!token) {
    redirectToPlatformLogin();
    throw new PlatformApiError(path, '数据中台登录已失效，正在前往登录页', 401);
  }
  if (!reconcilePlatformSessionToken(token,sessionRevision)) throw new PlatformApiError(path, '登录会话已切换，请重试', 401);
  const assertCurrentSession = () => {
    if (getSessionRevision() !== sessionRevision) throw new PlatformApiError(path, '登录会话已切换，旧请求结果已丢弃', 401);
  };
  let response: Response;
  const multipart = body instanceof FormData;
  // AbortController + a timer also works on the supported Chrome 100 baseline.
  const requestAbort = new AbortController();
  let fetchTimedOut = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  const cancel = () => requestAbort.abort();
  if (!multipart || !options.onUploadProgress) {
    timer = setTimeout(() => { fetchTimedOut = true; requestAbort.abort(); }, options.timeoutMs ?? 20000);
    options.signal?.addEventListener('abort', cancel, { once: true });
    if (options.signal?.aborted) requestAbort.abort();
  }
  const cleanup = () => { clearTimeout(timer); options.signal?.removeEventListener('abort', cancel); };
  try {
    const headers = { 'X-Audit-Route': window.location.hash.slice(1).split('?')[0], token, ...(body === undefined || multipart ? {} : { 'Content-Type': 'application/json' }) };
    response = multipart && options.onUploadProgress
      ? await uploadRequest(`${baseUrl}${path}`, body as FormData, headers, options, () => getSessionRevision() === sessionRevision)
      : await fetch(`${baseUrl}${path}`, {
        method: options.method ?? (body === undefined ? 'GET' : 'POST'), headers,
        body: body === undefined ? undefined : multipart ? body as FormData : JSON.stringify(body),
        cache: 'no-store',
        signal: requestAbort.signal,
      });
  } catch (cause) {
    cleanup();
    assertCurrentSession();
    const timedOut = fetchTimedOut || (cause instanceof DOMException && cause.name === 'TimeoutError');
    throw new PlatformApiError(path, timedOut ? '请求超时，请稍后重试' : '无法连接数据中台，请检查网络或服务状态');
  }
  try {
    assertCurrentSession();
    const method = options.method ?? (body === undefined ? 'GET' : 'POST');
    let data: unknown;
    try {
      data = await response.json();
      assertCurrentSession();
    } catch (cause) {
      if (cause instanceof PlatformApiError) throw cause;
      if (response.ok) throw new PlatformApiError(path, fetchTimedOut ? '请求超时，请稍后重试' : '接口返回格式无法识别', response.status, { method });
    }
    if (isPlatformAuthenticationFailure(data, response.status)) {
      redirectToPlatformLogin();
      throw platformResponseError(data, path, '数据中台登录已失效，正在前往登录页', 401, method);
    }
    if (!response.ok) throw platformResponseError(data, path,
      response.status === 413 ? '上传文件超过服务端或网关允许的大小，请联系管理员检查上传限制' : `接口请求失败（${response.status}）`, response.status, method);
    return unwrap<T>(data, path, method, response.status);
  } finally { cleanup(); }
}

import { DomainError } from '../domain/types.ts';

export interface HttpOptions {
    token?: string;
    method?: 'GET' | 'POST';
    body?: unknown;
    signal?: AbortSignal;
}

/** Decode the existing Magic API response envelope without treating failures as data. */
export async function request<T>(path: string, options: HttpOptions = {}): Promise<T> {
    let response: Response;
    try {
        response = await fetch(`/api${path}`, {
            method: options.method || 'GET',
            headers: { Accept: 'application/json', ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(options.token ? { token: options.token } : {}) },
            body: options.body === undefined ? undefined : JSON.stringify(options.body),
            signal: options.signal,
            credentials: 'same-origin',
        });
    } catch (error) {
        if (error instanceof Error && error.name === 'AbortError') throw error;
        throw new DomainError('服务器无响应，请稍后再试。', 'NETWORK_ERROR');
    }
    if (response.status >= 500) throw new DomainError('服务器无响应，请稍后再试。', 'SERVER_UNAVAILABLE');
    let payload: unknown;
    try { payload = await response.json(); }
    catch { throw new DomainError('服务器无响应，请稍后再试。', 'INVALID_RESPONSE'); }
    const value = payload as { code?: number | string; message?: string; msg?: string; data?: T } | null;
    const code = value?.code;
    const loginRequest = path === '/idaas/auth/login';
    const unauthenticated = !loginRequest && (response.status === 401 || code === 401 || code === '401' || code === 100120 || code === '100120' || code === 'UNAUTHENTICATED');
    if (!response.ok || (code !== undefined && code !== 0 && code !== 200 && code !== '0' && code !== '200')) {
        const message = unauthenticated ? '登录已过期，请重新登录。' : value?.message || value?.msg || '请求未完成，请稍后重试。';
        throw new DomainError(message, unauthenticated ? 'UNAUTHENTICATED' : String(code || response.status));
    }
    if (!value || typeof value !== 'object' || !Object.hasOwn(value, 'data')) {
        throw new DomainError('服务器无响应，请稍后再试。', 'INVALID_RESPONSE');
    }
    return value.data as T;
}

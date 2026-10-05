import { platformApi, platformUpload, type PlatformRequestOptions } from '../shared/platformApi';
export type * from './assets.types';

export const ASSETS_CHANGED = 'haoyue:assets-changed';
type QueryValue = string | number | boolean | null | undefined | readonly string[];
export type AssetQuery = Record<string, QueryValue>;
export interface AssetRequestOptions { signal?: AbortSignal; timeoutMs?: number; idempotencyKey?: string; notifyChanges?: boolean; }
const prefix = '/dwa/assets';
function route(path: string) {
  if (!path.startsWith('/') || path.startsWith('//') || path.includes('://')) throw new Error('无效的资产接口路径');
  return path.startsWith(prefix + '/') ? path : prefix + path;
}
function queryString(query: AssetQuery = {}) {
  const values = Object.entries(query).filter(([,v]) => v !== null && v !== undefined && v !== '');
  return new URLSearchParams(values.map(([k,v]) => [k, Array.isArray(v) ? v.join(',') : String(v)])).toString();
}
async function mutate<T>(method: PlatformRequestOptions['method'], path: string, body: unknown, options: AssetRequestOptions = {}): Promise<T> {
  const result = await platformApi<T>(route(path), body, { ...options, method, headers: { 'Idempotency-Key': options.idempotencyKey ?? crypto.randomUUID() } });
  const readOnly = /\/(?:preview|debug|precheck|visits)$/.test(path) || /\/deliveries\/[^/]+\/access$/.test(path);
  if (options.notifyChanges !== false && !readOnly) window.dispatchEvent(new Event(ASSETS_CHANGED));
  return result;
}
/** Same authenticated backend as governance. No business data is cached in browser storage. */
export const assetsApi = {
  get<T>(path: string, query: AssetQuery = {}, options: AssetRequestOptions = {}) {
    const suffix = queryString(query);
    return platformApi<T>(route(path) + (suffix ? `${path.includes('?') ? '&' : '?'}${suffix}` : ''), undefined, options);
  },
  post<T>(path: string, body: unknown = {}, options?: AssetRequestOptions) { return mutate<T>('POST', path, body, options); },
  put<T>(path: string, body: unknown = {}, options?: AssetRequestOptions) { return mutate<T>('PUT', path, body, options); },
  patch<T>(path: string, body: unknown = {}, options?: AssetRequestOptions) { return mutate<T>('PATCH', path, body, options); },
  delete<T>(path: string, body: unknown = {}, options?: AssetRequestOptions) { return mutate<T>('DELETE', path, body, options); },
  upload<T>(path: string, file: File, signal?: AbortSignal) { const form = new FormData(); form.append('file', file); return platformUpload<T>(route(path), form, signal); },
};

/** Keep one operation key for a unchanged form when retrying an uncertain network response. */
export function assetOperation() {
  let fingerprint = '', idempotencyKey = '';
  return (payload: unknown) => { const next = JSON.stringify(payload); if (next !== fingerprint || !idempotencyKey) { fingerprint = next; idempotencyKey = crypto.randomUUID(); } return { idempotencyKey }; };
}

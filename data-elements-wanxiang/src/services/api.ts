import { platformApi } from '../shared/platformApi';
import { logoutPlatformSession, redirectToPlatformLogin } from '../shared/platformSession';
export type Row = Record<string, any>;
export interface Session { authenticated: boolean; csrf: string; principalId?: string; name?: string; department?: string; user?: Row; tenant?: Row; workspaces?: Array<{ id: string; name: string; role: string }>; }
export class ApiError extends Error { constructor(public status: number, public code: string, message: string, public requestId?: string) { super(message); this.name = 'ApiError'; } }
let workspaceId = ''; let currentSession: Session | null = null;
export const getWorkspaceId = () => workspaceId;
export const getSession = () => currentSession;
export const setWorkspace = (id: string) => { if (!currentSession?.workspaces?.some(w => w.id === id)) throw new Error('工作区不可访问'); workspaceId = id; };
export const role = () => currentSession?.workspaces?.find(w => w.id === workspaceId)?.role ?? 'VIEWER';
export const canWrite = () => ['OWNER', 'STEWARD', 'EDITOR'].includes(role());
export const canReview = () => ['OWNER', 'STEWARD', 'REVIEWER'].includes(role());
export const canManage = () => role() === 'OWNER';
export const CHANGED = 'wanxiang:local-changed';
export function changed() { if (typeof window !== 'undefined') window.dispatchEvent(new Event(CHANGED)); }
export async function request<T = Row>(path: string, options: { method?: string; body?: unknown; version?: string | number; signal?: AbortSignal; idempotencyKey?: string } = {}): Promise<T> {
  const method = options.method ?? 'GET';
  try {
    // Legacy preview screens load their engine only when explicitly opened.
    // Authentication and shared online menus must not seed or poll mock jobs.
    const { localRequest, startLocalRuntime } = await import('../mocks/runtime');
    startLocalRuntime();
    const value = await localRequest<T>(path, options);
    if (method !== 'GET' && !path.startsWith('/auth')) changed();
    return value;
  } catch (error) {
    if (options.signal?.aborted) throw error;
    if (error && typeof error === 'object' && 'status' in error) {
      const e = error as {status:number;code:string;message:string};
      if (e.status === 401 && typeof window !== 'undefined') window.dispatchEvent(new Event('wanxiang:session-expired'));
      throw new ApiError(e.status, e.code, e.message);
    }
    throw error;
  }
}

export async function loadSession(): Promise<Session> {
 const [user, tenant] = await Promise.all([platformApi<Row>('/sym/user/me', {}), platformApi<Row>('/sym/tenant/current')]);
 if (!(user.id || user.userId)) { redirectToPlatformLogin(); throw new Error('当前登录账号无效，正在前往数据中台登录'); }
 const s: Session = { authenticated:true, csrf:'', principalId:String(user.id || user.userId), name:String(user.realName || user.userName || ''), department:String(user.orgName || ''), user, tenant, workspaces:[] };
 currentSession=s;
 if (!s.workspaces?.some(w=>w.id===workspaceId)) workspaceId=s.workspaces?.[0]?.id || '';
 return s;
}
export async function login(_username: string, _password: string): Promise<Session> { redirectToPlatformLogin(); throw new Error('请在数据中台登录'); }
export async function logout(): Promise<void> { await logoutPlatformSession(); currentSession=null; workspaceId=''; }
export function api<T = Row>(path: string, options?: Parameters<typeof request>[1]) { if (!workspaceId) return Promise.reject(new ApiError(401, 'WORKSPACE_REQUIRED', '请选择可访问的工作区')); return request<T>(`/workspaces/${encodeURIComponent(workspaceId)}/${path.replace(/^\//, '')}`, options); }
export async function all<T = Row>(key: string, params: Record<string, string> = {}, signal?: AbortSignal): Promise<T[]> { const result: T[] = []; let offset = 0; for (let page = 0; page < 100; page++) { const query = new URLSearchParams({ ...params, offset: String(offset), limit: '200' }); const response = await api<{ items: T[]; nextCursor?: string | null }>(`queries/${key}?${query}`, { signal }); if (!Array.isArray(response.items)) throw new ApiError(500, 'INVALID_RESPONSE', '列表响应格式不正确'); result.push(...response.items); if (!response.nextCursor) return result; offset = Number(response.nextCursor); } throw new ApiError(422, 'SCOPE_TOO_LARGE', '结果超过20000条，请缩小筛选范围'); }
export const versionOf = (row: Row): number => Number(row.version ?? row.lock_version ?? 1);
export const labelOf = (row?: Row): string => String(row?.displayName || row?.name || row?.title || row?.id || '—');
export async function waitJob(id: string, update: (job: Row) => void, signal?: AbortSignal): Promise<Row> {
  for (;;) { if (signal?.aborted) throw signal.reason; const job = await api<Row>(`jobs/${id}`, { signal }); update(job); if (['SUCCEEDED', 'PARTIAL', 'FAILED', 'CANCELLED'].includes(job.status)) { if (job.status === 'FAILED') throw new ApiError(422, job.error_code ?? 'JOB_FAILED', job.error_message ?? job.error?.message ?? '任务执行失败'); return job; } await new Promise<void>((resolve, reject) => { const finish = () => { signal?.removeEventListener('abort', abort); resolve(); }; const timer = setTimeout(finish, document.hidden ? 4000 : 1200); const abort = () => { clearTimeout(timer); signal?.removeEventListener('abort', abort); reject(signal?.reason); }; signal?.addEventListener('abort', abort, { once: true }); }); }
}

export async function switchIdentity(username:string):Promise<Session> {
  const s=await request<Session>('/auth/switch',{method:'POST',body:{username}});
  currentSession=s;workspaceId=s.workspaces?.[0]?.id??'';return s;
}

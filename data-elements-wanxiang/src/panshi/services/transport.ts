import { platformApi, platformApiGet } from '../../shared/platformApi';
import { getSession, type Row } from '../../services/api';

export interface ResourceRequestOptions { signal?: AbortSignal; timeoutMs?: number; }

/** The server session owns tenant selection. Resource requests never send a tenant ID. */
export function resourceSessionScope(): string {
  const session = getSession();
  const tenantId = String(session?.tenant?.tid || session?.tenant?.tenantId || '');
  if (!session?.authenticated || !session.principalId || !tenantId) {
    throw new Error('登录上下文尚未就绪，请返回数据中台登录');
  }
  return `${tenantId}:${session.principalId}`;
}

export async function resourceGet<T>(path: string, query: Record<string, string | number | boolean | undefined> = {}, options: ResourceRequestOptions = {}): Promise<T> {
  const scope = resourceSessionScope();
  const result = await platformApiGet<T>(path, query, options);
  if (scope !== resourceSessionScope()) throw new Error('登录或租户已切换，请重新加载数据资源中心');
  return result;
}

export async function resourcePost<T>(path: string, body: unknown, options: ResourceRequestOptions = {}): Promise<T> {
  const scope = resourceSessionScope();
  const result = await platformApi<T>(path, body, options);
  if (scope !== resourceSessionScope()) throw new Error('登录或租户已切换，请重新加载并核实操作结果');
  return result;
}

export function resourceObject(value: unknown, label: string): Row {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error(`${label}返回格式不正确`);
  return value as Row;
}

export function resourceList(value: unknown, label: string): Row[] {
  if (!Array.isArray(value)) throw new Error(`${label}未返回有效记录集合`);
  return value.map(row => resourceObject(row, label));
}

export class ResourceValidationError extends Error {
  readonly name = 'ResourceValidationError';
  constructor(public readonly status: number, public readonly code: string, message: string) { super(message); }
}

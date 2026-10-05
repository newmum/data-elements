import { registeredDatasourceId } from './datasourceIdentity';
type ResponseObject = Record<string, unknown>;

function asObject(value: unknown): ResponseObject | undefined {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as ResponseObject : undefined;
}

function isFailure(body: ResponseObject): boolean {
  if (body.success === false) return true;
  const code = body.code ?? body.status;
  return code !== undefined && code !== null && String(code).trim() !== ''
    && Number.isFinite(Number(code)) && ![0, 200].includes(Number(code));
}

/** Preserve failed/empty envelopes: data:null must not erase the server's error. */
export function unwrapResponse<T = unknown>(body: unknown): T {
  let current = body;
  for (let depth = 0; depth < 10; depth++) {
    const object = asObject(current);
    if (!object || isFailure(object)) break;
    if (!('code' in object || 'status' in object || 'success' in object)) break;
    const key = ['data', 'result', 'list'].find((name) => object[name] !== undefined);
    if (!key || object[key] === null || object[key] === current) break;
    current = object[key];
  }
  return current as T;
}

export function getApiErrorMessage(value: unknown, fallback: string): string {
  const object = asObject(value);
  if (!object) return typeof value === 'string' && value.trim() ? value : fallback;
  const httpResponse = asObject(object.response);
  if (httpResponse?.data != null) return getApiErrorMessage(httpResponse.data, String(object.message ?? fallback));
  const nestedError = asObject(object.error);
  const candidates = [nestedError?.message, nestedError?.msg, object.error, object.msg, object.message];
  const message = candidates.find((item) => typeof item === 'string' && item.trim()) as string | undefined;
  const code = object.errorCode ?? nestedError?.code ?? (isFailure(object) ? object.code ?? object.status : undefined);
  const trace = object.traceId ?? object.requestId ?? nestedError?.traceId;
  return [message ?? fallback, code == null ? '' : `错误码：${code}`, trace == null ? '' : `跟踪号：${trace}`]
    .filter(Boolean).join('；');
}

/** Runtime-check metadata endpoints before a component can read result.success. */
export function normalizeProbeResponse<T extends { success: boolean; error?: string }>(
  body: unknown, label: string, listKey?: 'tables' | 'columns',
): T {
  const payload = unwrapResponse(body);
  const object = asObject(payload);
  const fail = (error: string): T => ({ ...object, success: false, error, ...(listKey ? { [listKey]: [] } : {}) }) as T;
  if (!object) return fail(`${label}接口返回了空数据或无效响应`);
  if (isFailure(object)) return fail(getApiErrorMessage(object, `${label}失败`));
  if (object.success !== true) return fail(getApiErrorMessage(object, `${label}接口未返回有效的探查结果`));
  if (listKey && (!Array.isArray(object[listKey]) || object[listKey].some((row) => !asObject(row)))) {
    return fail(`${label}接口返回的${listKey === 'tables' ? '表' : '字段'}列表格式异常`);
  }
  return object as T;
}

export function hiveMetadataConfigError(manifestKey: string, config: Record<string, unknown>): string | undefined {
  if (!['source.hive', 'sink.hive'].includes(manifestKey)
    && !(manifestKey === 'sink.jdbc' && String(config.dbType).toUpperCase() === 'HIVE')) return undefined;
  if (registeredDatasourceId(config)) return undefined;
  if (!String(config.database ?? config.dbName ?? '').trim()) return '请先填写 Hive 数据库名，再探查数据表';
  return undefined;
}

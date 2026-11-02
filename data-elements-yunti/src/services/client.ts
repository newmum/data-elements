import type { Envelope } from "./types";
import { requestToken } from "./embeddedSession";

export type HttpVerb = "GET" | "POST" | "PUT" | "DELETE";

export class ApiClientError extends Error {
  code: number;
  constructor(code: number, message: string) {
    super(message);
    this.code = code;
    this.name = "ApiClientError";
  }
}

export const API_BASE = import.meta.env.VITE_API_BASE ?? "/dws/flowserve";
export const PLATFORM_API_BASE =
  import.meta.env.VITE_PLATFORM_API_BASE ?? "/dst";

async function realRequest<T>(
  base: string,
  verb: HttpVerb,
  path: string,
  body?: Record<string, unknown>,
): Promise<Envelope<T>> {
  // Bound canvas/metadata reads without interrupting an in-flight save or execution task.
  const readOnly =
    verb === "GET" || (base === PLATFORM_API_BASE && path === "/database/list");
  const controller = readOnly ? new AbortController() : undefined;
  const timer = controller
    ? window.setTimeout(() => controller.abort(), 15_000)
    : undefined;
  try {
    const res = await fetch(`${base}${path}`, {
      method: verb,
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...(requestToken() ? { token: requestToken() } : {}),
      },
      body: body ? JSON.stringify(body) : undefined,
      signal: controller?.signal,
    });
    let envelope: Envelope<T>;
    try { envelope = (await res.json()) as Envelope<T>; }
    catch { throw new ApiClientError(res.status || 502, '服务未返回有效响应，请检查连接后重试。'); }
    if (res.ok === false) throw new ApiClientError(res.status, envelope.message || (envelope as Envelope<T> & { msg?: string }).msg || '服务暂时无法响应');
    return envelope;
  } catch (error) {
    if (controller?.signal.aborted)
      throw new ApiClientError(408, "读取超时，请检查后端服务状态后重新加载。");
    if (error instanceof ApiClientError) throw error;
    throw new ApiClientError(0, "无法连接服务，请检查网络后重新加载。");
  } finally {
    window.clearTimeout(timer);
  }
}

function legacyStatus(code: number, message?: string): number {
  // Older backend envelopes wrap a Spring 404/403/401 in a generic 500 response.
  const status = message?.match(/^\s*(401|403|404)\s+(?:UNAUTHORIZED|FORBIDDEN|NOT_FOUND)\b/);
  return status ? Number(status[1]) : code;
}

export async function request<T>(
  verb: HttpVerb,
  path: string,
  body?: Record<string, unknown>,
): Promise<T> {
  const env = await realRequest<T>(API_BASE, verb, path, body);
  if (env.code !== 0 && env.code !== 200) {
    throw new ApiClientError(
      legacyStatus(env.code, env.message || (env as Envelope<T> & { msg?: string }).msg),
      env.message || (env as Envelope<T> & { msg?: string }).msg || "请求失败",
    );
  }
  return env.data;
}

/** Calls an existing main-platform API instead of creating a FlowServe duplicate. */
export async function platformRequest<T>(
  verb: HttpVerb,
  path: string,
  body?: Record<string, unknown>,
): Promise<T> {
  const env = await realRequest<T>(PLATFORM_API_BASE, verb, path, body);
  if (env.code !== 0 && env.code !== 200) {
    throw new ApiClientError(
      legacyStatus(env.code, env.message || (env as Envelope<T> & { msg?: string }).msg),
      env.message || (env as Envelope<T> & { msg?: string }).msg || "请求失败",
    );
  }
  return env.data;
}

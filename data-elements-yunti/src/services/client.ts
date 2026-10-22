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
    return (await res.json()) as Envelope<T>;
  } catch (error) {
    if (controller?.signal.aborted)
      throw new ApiClientError(408, "读取超时，请检查后端服务状态后重新加载。");
    throw error;
  } finally {
    window.clearTimeout(timer);
  }
}

export async function request<T>(
  verb: HttpVerb,
  path: string,
  body?: Record<string, unknown>,
): Promise<T> {
  const env = await realRequest<T>(API_BASE, verb, path, body);
  if (env.code !== 0 && env.code !== 200) {
    throw new ApiClientError(
      env.code,
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
      env.code,
      env.message || (env as Envelope<T> & { msg?: string }).msg || "请求失败",
    );
  }
  return env.data;
}

export interface PlatformErrorInfo {
  message: string;
  code?: string;
  reason?: string;
  detail?: string;
  traceId?: string;
  type?: string;
  method?: string;
  path?: string;
}

function object(value: unknown): Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
    ? value as Record<string, unknown> : {};
}

/** Diagnostics never include a request body, file contents or session headers. */
export function safeDiagnostic(value: unknown): string {
  if (typeof value !== 'string' && typeof value !== 'number') return '';
  return String(value)
    .replace(/(https?:\/\/)[^\s/@:]+:[^\s/@]+@/gi, '$1[已隐藏]@')
    .replace(/(["']?(?:authorization|proxy-authorization)["']?\s*[:=]\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\r\n]+)/gi, '$1[已隐藏]')
    .replace(/(["']?(?:password|passwd|pwd|token|access[_-]?key|secret[_-]?key|client[_-]?secret|secret|x-amz-credential|x-amz-signature|x-amz-security-token)["']?\s*[:=]\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\s,;&}\r\n]+)/gi, '$1[已隐藏]')
    .trim();
}

function causeSummary(detail: string): string | undefined {
  const lines = detail.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
  const causes = lines.filter(line => /^Caused by:/i.test(line));
  const reason = causes.at(-1)?.replace(/^Caused by:\s*/i, '')
    || lines.find(line => !/^(?:at\s|\.\.\.|\^)/.test(line));
  return reason ? reason.replace(/^(?:[\w$]+\.)*[\w$]*(?:Exception|Error):\s*/, '').slice(0, 600) : undefined;
}

export class PlatformApiError extends Error {
  readonly info: PlatformErrorInfo;
  constructor(public readonly path: string, message: string, public readonly status?: number, info: Partial<PlatformErrorInfo> = {}) {
    super(safeDiagnostic(message));
    this.name = 'PlatformApiError';
    this.info = { ...info, message: this.message, path: safeDiagnostic(info.path || path) };
  }
}

/** Preserve the same structured error contract used by the source-management app. */
export function platformResponseError(payload: unknown, path: string, fallback: string, status?: number, method?: string): PlatformApiError {
  const body = object(payload), error = object(body.error);
  const structured = safeDiagnostic(error.message), top = safeDiagnostic(body.msg) || safeDiagnostic(body.message);
  const generic = /^(接口脚本执行失败|接口处理失败|请求处理失败|系统出错|系统异常|服务器内部错误)/.test(structured);
  const message = (generic ? top || structured : structured || top) || fallback;
  const detail = safeDiagnostic(error.detail) || safeDiagnostic(body.detail);
  return new PlatformApiError(path, message, status, {
    code: safeDiagnostic(error.code) || safeDiagnostic(body.code) || (status ? `HTTP-${status}` : undefined),
    reason: detail ? causeSummary(detail) : undefined,
    detail: detail || message,
    traceId: safeDiagnostic(error.traceId) || safeDiagnostic(body.traceId) || undefined,
    type: safeDiagnostic(error.type) || undefined,
    method: safeDiagnostic(error.method) || method,
    path: safeDiagnostic(error.path) || path,
  });
}

export function platformErrorInfo(cause: unknown): PlatformErrorInfo {
  return cause instanceof PlatformApiError ? cause.info : {
    message: safeDiagnostic(cause instanceof Error ? cause.message : cause) || '操作失败，请重试',
  };
}

export function diagnosticText(info: PlatformErrorInfo): string {
  return [
    `错误提示：${info.message}`, info.reason && `具体原因：${info.reason}`,
    info.code && `错误码：${info.code}`, info.traceId && `跟踪号：${info.traceId}`,
    (info.method || info.path) && `请求：${[info.method, info.path].filter(Boolean).join(' ')}`,
    info.type && `异常类型：${info.type}`, info.detail && `完整错误信息：\n${info.detail}`,
  ].filter(Boolean).join('\n');
}

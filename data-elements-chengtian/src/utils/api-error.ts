import { h } from "vue";
import { ElButton, ElMessage, ElMessageBox } from "element-plus";

export interface ApiErrorInfo {
  code: string;
  message: string;
  detail: string;
  traceId?: string;
  type?: string;
  method?: string;
  path?: string;
}

export interface ApiErrorFallback {
  code: string;
  message: string;
  detail?: string;
  method?: string;
  path?: string;
}

/**
 * 当前仍在展示的接口错误提示。后端同一异常常会被并发请求同时触发，按
 * error.message 合并即可避免连续堆叠相同弹窗，而不会吞掉不同的错误。
 */
const activeErrorMessages = new Map<string, true>();

type MessagePayload = Parameters<typeof ElMessage.error>[0];
type MessageHandler = ReturnType<typeof ElMessage.error>;

function messageText(payload: MessagePayload): string {
  if (typeof payload === "string") {
    return payload.trim();
  }
  if (payload && typeof payload === "object" && "message" in payload) {
    const message = (payload as { message?: unknown }).message;
    return typeof message === "string" ? message.trim() : "";
  }
  return "";
}

/**
 * Low-code pages use both `ElMessage` and the same function exposed as
 * `$message`.  Their catch blocks predate the global request interceptor, so
 * a failed request can otherwise create a second local toast.  Guard the two
 * error-level entry points at the common Element Plus object: every already
 * displayed API error then remains a single global notification without
 * changing local validation or `errorPolicy: "silent"` feedback.
 */
function installDuplicateMessageGuard(): void {
  const messageApi = ElMessage as typeof ElMessage & {
    __apiErrorDuplicateGuardInstalled?: boolean;
  };
  if (messageApi.__apiErrorDuplicateGuardInstalled) {
    return;
  }
  messageApi.__apiErrorDuplicateGuardInstalled = true;

  const guard =
    (original: (payload: MessagePayload) => MessageHandler) =>
    (payload: MessagePayload): MessageHandler => {
      const text = messageText(payload);
      if (text && activeErrorMessages.has(text)) {
        // Keep the Element Plus return contract for callers that save the
        // handler and later invoke it to close a message.
        return (() => undefined) as MessageHandler;
      }
      return original(payload);
    };

  messageApi.error = guard(messageApi.error.bind(messageApi));
  messageApi.warning = guard(messageApi.warning.bind(messageApi));
}

installDuplicateMessageGuard();

/**
 * 统一的接口错误对象。handled 用于告诉页面：全局拦截器已经展示过错误，不要重复弹窗。
 */
export class ApiRequestError extends Error {
  /**
   * Only becomes true after the global request layer has actually displayed the
   * message.  Page-level catch blocks use this flag to avoid showing the same
   * server failure a second time, while callers using `errorPolicy: "silent"`
   * remain free to render their own contextual feedback.
   */
  handled = false;
  readonly errorCode: string;
  readonly detail: string;
  readonly traceId?: string;
  readonly info: ApiErrorInfo;

  constructor(info: ApiErrorInfo) {
    super(info.message);
    this.name = "ApiRequestError";
    this.errorCode = info.code;
    this.detail = info.detail;
    this.traceId = info.traceId;
    this.info = info;
  }
}

/**
 * Whether this request failure has already been presented by the global error
 * handler.  This deliberately accepts structural errors as well: low-code
 * pages and iframe integrations can receive an error created in another
 * bundle, where `instanceof ApiRequestError` would not be reliable.
 */
export function isApiErrorAlreadyHandled(error: unknown): boolean {
  return Boolean(
    error && typeof error === "object" && (error as { handled?: unknown }).handled === true
  );
}

function readableJson(value: unknown): string {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value ?? "");
  }
}

/**
 * 兼容新旧接口结构；新 Magic 响应优先使用 error 中的结构化错误信息。
 */
export function createApiRequestError(payload: any, fallback: ApiErrorFallback): ApiRequestError {
  const serverError = payload?.error ?? {};
  const message =
    serverError.message || payload?.msg || payload?.message || fallback.message || "请求处理失败";
  const info: ApiErrorInfo = {
    code: String(serverError.code || fallback.code || "API-UNKNOWN-001"),
    message,
    detail:
      serverError.detail || fallback.detail || (payload == null ? message : readableJson(payload)),
    traceId: serverError.traceId,
    type: serverError.type,
    method: serverError.method || fallback.method,
    path: serverError.path || fallback.path,
  };
  return new ApiRequestError(info);
}

function detailRow(label: string, value?: string) {
  if (!value) return null;
  return h("div", { style: "display:flex;gap:12px;margin-bottom:6px;line-height:22px;" }, [
    h(
      "span",
      {
        style: "flex:0 0 64px;color:var(--el-text-color-secondary);font-weight:600;",
      },
      label
    ),
    h(
      "span",
      {
        style: "min-width:0;overflow-wrap:anywhere;color:var(--el-text-color-primary);",
      },
      value
    ),
  ]);
}

function openErrorDetail(info: ApiErrorInfo) {
  const rows = [
    detailRow("错误码", info.code),
    detailRow("跟踪号", info.traceId),
    detailRow("请求", [info.method, info.path].filter(Boolean).join(" ")),
    detailRow("异常类型", info.type),
  ].filter(Boolean);

  void ElMessageBox.alert(
    h("div", { style: "text-align:left;" }, [
      ...rows,
      h(
        "div",
        {
          style: "margin:12px 0 6px;color:var(--el-text-color-secondary);font-weight:600;",
        },
        "完整错误信息"
      ),
      h(
        "pre",
        {
          style:
            "max-height:52vh;margin:0;padding:12px;overflow:auto;white-space:pre-wrap;overflow-wrap:anywhere;border:1px solid var(--el-border-color);border-radius:6px;background:var(--el-fill-color-light);font:12px/1.6 Consolas,Monaco,monospace;color:var(--el-text-color-primary);",
        },
        info.detail || info.message
      ),
    ]),
    "错误详情",
    {
      confirmButtonText: "关闭",
      customClass: "api-error-detail-dialog",
      customStyle: {
        width: "min(900px, calc(100vw - 48px))",
        maxWidth: "900px",
      },
      closeOnClickModal: true,
      draggable: true,
    }
  );
}

/**
 * 在页面顶部展示简洁错误摘要，并允许用户按需查看后端返回的完整技术信息。
 */
export function showApiError(error: ApiRequestError): void {
  // Mark the original rejected error before invoking Element Plus.  All
  // consumers of that promise can then suppress their duplicate local toast.
  error.handled = true;
  const messageKey = String(error.message || "").trim();
  if (messageKey && activeErrorMessages.has(messageKey)) {
    return;
  }

  if (messageKey) {
    activeErrorMessages.set(messageKey, true);
  }

  ElMessage({
    // 后端调用失败属于需要用户注意、但不应与表单校验混淆的异常；统一使用
    // Element Plus 的 warning 图标（黄色感叹号），与页面内的错误提示保持一致。
    type: "warning",
    duration: 8000,
    showClose: true,
    customClass: "api-error-message",
    onClose: () => {
      if (messageKey) {
        activeErrorMessages.delete(messageKey);
      }
    },
    message: h(
      "div",
      {
        style:
          "display:flex;align-items:center;gap:10px;max-width:min(760px,calc(100vw - 160px));line-height:20px;",
      },
      [
        h(
          "span",
          { style: "min-width:0;flex:1;overflow-wrap:anywhere;" },
          `${error.message}（错误码：${error.errorCode}）`
        ),
        h(
          ElButton,
          {
            link: true,
            type: "primary",
            style: "flex:none;padding:0;min-height:20px;",
            onClick: () => openErrorDetail(error.info),
          },
          () => "查看详情"
        ),
      ]
    ),
  });
}

/** Local cancellation/session errors never mean that the backend is offline. */
interface RequestFailure {
  isAxiosError?: boolean; code?: string; message?: string; response?: { status?: number }; request?: unknown;
  config?: { url?: string; nifiSilentGlobalErrors?: boolean };
}

export function transportFailureMessage(error: RequestFailure | null | undefined): string | undefined {
  if (!error || error.response || !error.isAxiosError || !error.request || error.code === 'ERR_CANCELED') return undefined;
  if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') return '请求超时，请稍后重试';
  if (error.code === 'ERR_NETWORK') return '网络连接异常，请检查后端服务是否可用';
  return undefined;
}

/** Polling keeps its error state for inline feedback; only automatic global popups are quiet. */
export function globalRequestFailureMessage(error: RequestFailure | null | undefined): string | undefined {
  if (error?.config?.nifiSilentGlobalErrors || error?.code === 'ERR_CANCELED') return undefined;
  const transport = transportFailureMessage(error);
  if (transport) return transport;
  if ((error?.response?.status ?? 0) >= 500 && !/\/pipelines\/[^/]+\/deploy(?:\?|$)/.test(error?.config?.url ?? '')) {
    return `服务器异常 (${error!.response!.status})，请稍后重试`;
  }
  return undefined;
}

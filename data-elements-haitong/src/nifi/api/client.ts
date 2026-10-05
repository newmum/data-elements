import axios from 'axios';
import type { InternalAxiosRequestConfig } from 'axios';
import { message as antdMessage } from 'antd';
import { unwrapResponse } from './response';
import { getBridgeToken, getSessionRevision, waitForToken } from './bridgeSession';
import { isPlatformAuthenticationFailure } from '../../api/platformApi';
import { redirectToPlatformLogin } from '../../services/platformSession';

type SessionRequestConfig = InternalAxiosRequestConfig & { nifiSessionRevision?: number };

function rejectStaleSession(config?: SessionRequestConfig) {
  if (config?.nifiSessionRevision !== undefined && config.nifiSessionRevision !== getSessionRevision()) {
    throw new Error('登录会话已切换，旧请求结果已丢弃');
  }
}

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API || '/nifi/api',
  timeout: Number(import.meta.env.VITE_APP_API_TIMEOUT_MS || 45000),
  headers: { 'Content-Type': 'application/json' },
});

apiClient.interceptors.request.use(async (config) => {
  const requestRevision = getSessionRevision();
  const token = await waitForToken();
  // A request queued by the old editor must never resume under another
  // tenant's token. Revision 0 is the initial iframe handshake.
  if ((requestRevision !== 0 && requestRevision !== getSessionRevision()) || token !== getBridgeToken()) {
    throw new Error('登录会话已切换，请重试');
  }
  (config as SessionRequestConfig).nifiSessionRevision = getSessionRevision();
  config.headers.set('token', token);
  config.headers.set('Authorization', `Bearer ${token}`);
  return config;
});

// Track if we've shown a network error toast recently to avoid spam
let lastNetworkErrorTime = 0;
const NETWORK_ERROR_COOLDOWN = 5000; // 5s cooldown between network error toasts

apiClient.interceptors.response.use(
  (response) => {
    rejectStaleSession(response.config as SessionRequestConfig);
    if (isPlatformAuthenticationFailure(response.data, response.status)) {
      redirectToPlatformLogin();
      return Promise.reject(new Error('数据中台登录已失效，正在前往登录页'));
    }
    // Automatically unwrap { code, data, message } wrapper so callers get the inner data directly
    response.data = unwrapResponse(response.data);
    return response;
  },
  (error) => {
    try {
      rejectStaleSession(error?.config as SessionRequestConfig | undefined);
    } catch (staleError) {
      return Promise.reject(staleError);
    }
    if (isPlatformAuthenticationFailure(error?.response?.data, error?.response?.status)) {
      redirectToPlatformLogin();
      return Promise.reject(new Error('数据中台登录已失效，正在前往登录页'));
    }
    // Only show global toast for unexpected errors
    // React Query handles retry logic; we just provide user feedback here
    if (!error.response) {
      // Network error (server unreachable, timeout, CORS, etc.)
      const now = Date.now();
      if (now - lastNetworkErrorTime > NETWORK_ERROR_COOLDOWN) {
        lastNetworkErrorTime = now;
        if (error.code === 'ECONNABORTED' || error.message?.includes('timeout')) {
          antdMessage.warning('请求超时，请检查网络连接');
        } else {
          antdMessage.warning('网络连接异常，请检查后端服务是否可用');
        }
      }
    } else if (error.response.status >= 500) {
      // Server error — not 404 (which some APIs handle locally)
      const now = Date.now();
      if (now - lastNetworkErrorTime > NETWORK_ERROR_COOLDOWN) {
        lastNetworkErrorTime = now;
        antdMessage.error(`服务器异常 (${error.response.status})，请稍后重试`);
      }
    }
    // Always reject so individual callers can handle specific errors
    return Promise.reject(error);
  },
);

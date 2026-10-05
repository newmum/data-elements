import axios, { type InternalAxiosRequestConfig, type AxiosResponse } from "axios";
import qs from "qs";
import { useUserStoreHook } from "@/store/modules/user-store";
import { useSettingStoreHook } from "@/store/modules/setting-store";
import { AuthStorage } from "@/utils/auth";
import router from "@/router";
import { Storage } from "./storage";
import { AUTH_KEYS } from "@/enums";
import { sendMessage } from "@/plugins/frame";
import { isExternal } from "@/utils/index";
import { createApiRequestError, showApiError } from "@/utils/api-error";

/**
 * 创建 HTTP 请求实例
 */
const httpRequest = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 30000,
  headers: { "Content-Type": "application/json;charset=utf-8" },
  paramsSerializer: (params) => qs.stringify(params),
});

/**
 * 请求拦截器 - 添加 Authorization 头
 */
httpRequest.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const accessToken = AuthStorage.getAccessToken();
    if (accessToken) {
      config.headers[AUTH_KEYS.ACCESS_TOKEN] = accessToken;
    }
    // 出入境token
    config.headers["Access-Token"] = Storage.get("Access-Token");

    return config;
  },
  (error) => {
    console.error("请求拦截器异常:", error);
    return Promise.reject(error);
  }
);

/**
 * 响应拦截器 - 统一处理响应和错误
 */
httpRequest.interceptors.response.use(
  async (response: AxiosResponse<ApiResponse>) => {
    // 如果响应是二进制流，则直接返回（用于文件下载、Excel 导出等）
    if (response.config.responseType === "blob") {
      return response;
    }

    // API 调试等场景：透传完整响应对象（含 headers、status、data 原始结构）
    if (responsePolicy(response.config) === "raw") {
      return response;
    }

    const { code, data, msg: ms, message } = response.data;
    const msg = ms || message;

    // 请求成功
    if (code === 200 || code === 0 || code === 1) {
      return data;
    }

    // 检验code
    switch (code) {
      case 100120:
        // 登录过期，跳转登录页
        await redirectToLogin("登录已过期，请重新登录");
        return Promise.resolve(new Error(msg || "登录已过期，请重新登录"));
    }

    // 表数据预览等场景：不提示错误信息
    const apiError = createApiRequestError(response.data, {
      code: `API-BUSINESS-${code ?? "UNKNOWN"}`,
      message: msg || "系统出错",
      method: response.config.method?.toUpperCase(),
      path: response.config.url,
    });
    if (shouldNotifyError(response.config)) {
      showApiError(apiError);
    }
    return Promise.reject(apiError);
  },
  async (error) => {
    console.error("响应拦截器异常:", error);

    // API 调试场景：透传完整错误对象（含 response）
    if (responsePolicy(error.config) === "raw") {
      return Promise.reject(error);
    }

    const { response } = error;

    // 检查是否是重定向错误
    if (response && response.status >= 300 && response.status < 400) {
      const redirectUrl = error.response.headers.location;

      // 当前系统iframe嵌套于其他系统时，若无权限需通知顶层窗口
      if (redirectUrl.includes("/login") && top !== self) {
        sendMessage("noAuth", {});
      }
    }

    // 网络错误或服务器无响应
    if (!response) {
      const timeout =
        error.code === "ECONNABORTED" ||
        String(error.message || "")
          .toLowerCase()
          .includes("timeout");
      const timeoutSeconds = Math.round(Number(error.config?.timeout || 0) / 1000);
      const apiError = createApiRequestError(null, {
        code: timeout ? "HTTP-TIMEOUT-001" : "HTTP-NETWORK-001",
        message: timeout
          ? `请求超时${timeoutSeconds > 0 ? `（${timeoutSeconds} 秒）` : ""}，请稍后重试`
          : "无法连接服务器，请检查网络或服务运行状态",
        detail: error.stack || error.message,
        method: error.config?.method?.toUpperCase(),
        path: error.config?.url,
      });
      if (shouldNotifyError(error.config)) {
        showApiError(apiError);
      }
      return Promise.reject(apiError);
    }

    const { msg: ms, message } = (response.data || {}) as ApiResponse;
    const msg = ms || message;
    const apiError = createApiRequestError(response.data, {
      code: `HTTP-${response.status}`,
      message: msg || `请求失败（HTTP ${response.status}）`,
      detail: error.stack || readableResponse(response.data),
      method: error.config?.method?.toUpperCase(),
      path: error.config?.url,
    });
    if (shouldNotifyError(error.config)) {
      showApiError(apiError);
    }
    return Promise.reject(apiError);
  }
);

function readableResponse(data: unknown): string {
  try {
    return JSON.stringify(data, null, 2);
  } catch {
    return String(data ?? "");
  }
}

function responsePolicy(config: unknown): "data" | "raw" {
  return (config as { responsePolicy?: "data" | "raw" } | undefined)?.responsePolicy || "data";
}

function shouldNotifyError(config: unknown): boolean {
  return (config as { errorPolicy?: "notify" | "silent" } | undefined)?.errorPolicy !== "silent";
}

/**
 * 重定向到登录页面（防并发：多个请求同时 无权限 时只执行一次）
 */
let isRedirecting = false;
async function redirectToLogin(message: string = "请重新登录"): Promise<void> {
  if (isRedirecting) return;
  isRedirecting = true;

  try {
    ElNotification({
      title: "提示",
      message,
      type: "warning",
      duration: 3000,
    });

    await useUserStoreHook().resetAllState();

    // 跳转前强制刷新设置，确保 loginUrl 是最新值
    const settingsStore = useSettingStoreHook();
    await settingsStore.loadingSettings(true);

    // 跳转到登录页，保留当前路由用于登录后跳转
    const currentPath = router.currentRoute.value.fullPath;
    const loginUrl = settingsStore.loginUrl || "/login";
    const redirect = encodeURIComponent(currentPath);

    if (isExternal(loginUrl)) {
      // 外部登录页：全量跳转
      window.location.href = `${loginUrl}`;
    } else {
      // 内部登录页：路由跳转
      await router.push({ path: loginUrl, query: { redirect } });
    }
  } catch (error) {
    console.error("重定向登录页异常:", error);
  } finally {
    // 登录页加载后重置标志，允许下次过期时再次触发
    setTimeout(() => {
      isRedirecting = false;
    }, 1000);
  }
}

export default httpRequest;

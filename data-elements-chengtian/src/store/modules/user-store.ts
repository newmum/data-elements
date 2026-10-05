import { store } from "@/store";

import AuthAPI, { type LoginFormData } from "@/api/auth-api";
import TenantAPI from "@/api/tenant-api";
import UserAPI, { type UserInfo } from "@/api/system/user-api";

import { AuthStorage } from "@/utils/auth";
import { usePermissionStoreHook } from "@/store/modules/permission-store";
import { useDictStoreHook } from "@/store/modules/dict-store";
import { useSettingStoreHook, useTagsViewStore } from "@/store";
import { cleanupWebSocket } from "@/plugins/websocket";
import { PingaoSsoAPI } from "@/api/pingao-sso-api";

export type UserType = UserInfo & {
  isAdmin: boolean; // 是否管理员
};

export const useUserStore = defineStore("user", () => {
  // 用户信息
  const userInfo = ref<UserType>({} as UserType);
  // 记住我状态
  const rememberMe = ref(AuthStorage.getRememberMe());

  /**
   * 登录
   *
   * @param {LoginFormData}
   * @returns
   */
  async function login(loginFormData: LoginFormData): Promise<string> {
    const data = await AuthAPI.login(loginFormData);
    const requestedTenantId = loginFormData.tenantId?.trim() || "";
    const boundTenantId = typeof data.tenantId === "string" ? data.tenantId.trim() : "";
    if (requestedTenantId && boundTenantId && requestedTenantId !== boundTenantId) {
      throw new Error("登录返回的租户与所选行业场景不一致，请重新登录");
    }

    rememberMe.value = loginFormData.rememberMe;
    AuthStorage.setTokens(data.token, rememberMe.value);
    const settingsStore = useSettingStoreHook();
    const cachedBrandTenantId =
      typeof settingsStore.brandTenantId === "string" ? settingsStore.brandTenantId.trim() : "";
    const targetTenantId = boundTenantId || requestedTenantId || cachedBrandTenantId;
    try {
      // 新版登录接口已在服务端绑定租户。仅旧版接口仍需补做一次租户切换。
      if (targetTenantId && !boundTenantId) {
        await TenantAPI.switchTenant(targetTenantId);
      }
      resetTenantScopedState();
      if (targetTenantId) {
        // 登录页已加载过该租户的公开设置时，直接复用；失败过的设置仍会重试。
        await settingsStore.loadingSettings(Boolean(settingsStore.settingsError), targetTenantId);
      }
      return data.url;
    } catch (error) {
      AuthStorage.clearAuth();
      throw error;
    }
  }

  /**
   * 获取用户信息
   *
   * @returns {UserInfo} 用户信息
   */
  function getUserInfo() {
    return new Promise<UserInfo>((resolve, reject) => {
      UserAPI.getInfo()
        .then((data) => {
          if (!data) {
            reject("获取用户信息失败，请重新登录");
            return;
          }
          // 给userInfo添加isAdmin字段
          const userData = {
            ...data,
            isAdmin: data.roles?.some((role) => role.toLowerCase() === "admin") || false,
          };
          Object.assign(userInfo.value, userData);
          $user = userInfo.value;
          resolve(data);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  /**
   * SSO 统一认证登录
   *
   * @param {string} credential - ticket / token / code
   * @param {"ticket" | "code"} paramKey - 参数名，用于构造 query 参数
   */
  function ssoLogin(credential: string, paramKey: "ticket" | "code" | "token" = "ticket") {
    return new Promise<void>((resolve, reject) => {
      if (paramKey === "token") {
        AuthStorage.setTokens(credential, true);
        resolve();
        return;
      }
      AuthAPI.ssoLogin({ [paramKey]: credential })
        .then((data) => {
          AuthStorage.setTokens(data.token, true);
          resolve();
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  /**
   * 登出
   */
  function logout() {
    if (sessionStorage.getItem("pingao-sso-session") === "1") {
      return PingaoSsoAPI.logout().then(async ({ logoutUrl }) => {
        await resetAllState();
        window.location.assign(logoutUrl);
      });
    }
    return new Promise<any>((resolve, reject) => {
      AuthAPI.logout()
        .then((res) => {
          // 重置所有系统状态
          resetAllState();
          resolve(res);
        })
        .catch((error) => {
          reject(error);
        });
    });
  }

  /**
   * 重置所有系统状态
   * 统一处理所有清理工作，包括用户凭证、路由、缓存等
   */
  function resetAllState() {
    // 1. 重置用户状态
    resetUserState();

    // 2. 重置租户相关模块状态
    resetTenantScopedState();

    return Promise.resolve();
  }

  /**
   * Clear state that can contain rows or options from a previous tenant.
   * Authentication is retained so this is safe immediately after login switch.
   */
  function resetTenantScopedState() {
    usePermissionStoreHook().resetRouter();
    useDictStoreHook().clearDictCache();
    useTagsViewStore().delAllViews();
    cleanupWebSocket();
  }

  /**
   * 重置用户状态
   * 仅处理用户模块内的状态
   */
  function resetUserState() {
    // 清除用户凭证
    AuthStorage.clearAuth();
    sessionStorage.removeItem("pingao-sso-session");
    // 重置用户信息
    userInfo.value = {} as UserType;
  }

  return {
    userInfo,
    rememberMe,
    isLoggedIn: () => !!AuthStorage.getAccessToken(),
    getUserInfo,
    login,
    ssoLogin,
    logout,
    resetAllState,
    resetUserState,
  };
});

/**
 * 在组件外部使用UserStore的钩子函数
 * @see https://pinia.vuejs.org/core-concepts/outside-component-usage.html
 */
export function useUserStoreHook() {
  return useUserStore(store);
}

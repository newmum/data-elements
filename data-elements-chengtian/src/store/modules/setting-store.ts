import { store } from "@/store";
import { defaultSettings } from "@/settings";
import request from "@/utils/request";
import { AuthStorage } from "@/utils/auth";
import { applyTheme, generateThemeColors } from "@/utils/theme";

/**
 * 系统配置状态类型定义
 */
export interface ConfigState {
  // 系统状态
  device: string;
  appId: string;

  // 界面显示设置
  settingsVisible: boolean;
  showTagsView: boolean;
  showLogo: boolean;
  showWatermark: boolean;
  watermarkContent: string;
  sidebarOpen: boolean;

  // 系统设置
  themeColor: string;
  systemName: string;
  systemCode: string;
  logo: string;
  version: string;
  needLogin: boolean;
  loginUrl: string;
}

/**
 * 可变更的设置项类型
 */
type MutableSetting = Exclude<
  keyof ConfigState,
  "settingsVisible" | "sidebar" | "device" | "sidebarStatus"
>;
type SettingValue<K extends MutableSetting> = ConfigState[K];

/**
 * 系统配置 Store
 * 负责管理应用全局配置，包括界面布局、系统设置等
 */
export const useSettingStore = defineStore("setting", () => {
  // 系统状态
  const appId = useStorage("appId", ""); // 应用Id，统一认证用

  // 非持久化设置
  const settingsVisible = ref<boolean>(false);
  const navVisible = ref<boolean>(false); // 全局导航显示状态
  const settingsLoading = ref<boolean>(false);
  const settingsReady = ref<boolean>(false);
  const settingsError = ref<string>("");
  const brandTenantId = useStorage<string>("brandTenantId", "");
  const showShoppingCar = ref<string>("");

  // 持久化系统配置
  const showSettings = useStorage<boolean>("showSettings", defaultSettings.showSettings);
  const showTagsView = useStorage<boolean>("showTagsView", defaultSettings.showTagsView);
  const showLogo = useStorage<boolean>("showLogo", defaultSettings.showLogo);
  const showWatermark = useStorage<boolean>("showWatermark", defaultSettings.showWatermark);
  const watermarkContent = useStorage<string>("watermarkContent", defaultSettings.watermarkContent);
  const themeColor = useStorage<string>("themeColor", defaultSettings.themeColor);
  const systemName = useStorage<string>("systemName", defaultSettings.systemName);
  const systemCode = useStorage("systemCode", ""); // 系统编码
  const logo = useStorage<string>("logo", defaultSettings.logo);
  const version = useStorage<string>("version", defaultSettings.version);
  const needLogin = useStorage<boolean>("needLogin", defaultSettings.needLogin);
  const loginUrl = useStorage<string>("loginUrl", defaultSettings.loginUrl);
  const sidebarOpen = useStorage<boolean>("sidebarOpen", defaultSettings.sidebarOpen);

  // 设置项映射 - 用于类型安全的更新
  const settingsMap = {
    appId,
    showTagsView,
    showLogo,
    showWatermark,
    watermarkContent,
    systemName,
    systemCode,
    themeColor,
    logo,
    version,
    needLogin,
    loginUrl,
    sidebarOpen,
    showShoppingCar,
  } as const;

  // ===== 副作用 =====
  // 监听主题变化并应用
  watch(
    [themeColor],
    ([newThemeColor]) => {
      const colors = generateThemeColors(newThemeColor);
      applyTheme(colors);
    },
    { immediate: true }
  );

  // 监听系统名称和图标变化
  watch(
    [systemName, logo],
    ([newSystemName, logo]) => {
      const oldLink = document.querySelector('link[rel~="icon"]') as any;
      if (oldLink) {
        oldLink["href"] = logo;
      }

      if (newSystemName) {
        document.title = newSystemName;
      }
    },
    { immediate: true }
  );

  // ===== 设置更新 =====
  function updateSetting<K extends keyof typeof settingsMap>(key: K, value: SettingValue<K>): void {
    const setting = settingsMap[key];
    if (setting) {
      (setting as Ref<any>).value = value;
    }
  }

  function updateThemeColor(newColor: string): void {
    themeColor.value = newColor;
  }

  // ===== 设置面板控制 =====
  function toggleSettingsPanel(): void {
    settingsVisible.value = !settingsVisible.value;
  }

  function showSettingsPanel(): void {
    settingsVisible.value = true;
  }

  function hideSettingsPanel(): void {
    settingsVisible.value = false;
  }

  // ===== 设置重置 =====
  function resetSettings(): void {
    showTagsView.value = defaultSettings.showTagsView;
    showLogo.value = defaultSettings.showLogo;
    showWatermark.value = defaultSettings.showWatermark;
    watermarkContent.value = defaultSettings.watermarkContent;
    themeColor.value = defaultSettings.themeColor;
    systemName.value = defaultSettings.systemName;
  }

  // ===== 数据加载 =====
  let settingsRequestSequence = 0;

  async function loadingSettings(force = false, tenantId?: string): Promise<void> {
    let requestedTenantId = tenantId?.trim() || "";
    if (!requestedTenantId && AuthStorage.getAccessToken()) {
      try {
        const currentTenant = (await request({ url: "/sym/tenant/current" })) as Record<string, unknown>;
        requestedTenantId = String(currentTenant?.tid || "").trim();
      } catch (error) {
        console.warn("读取当前租户失败，系统设置将使用全局配置：", error);
      }
    }
    if (!requestedTenantId && !AuthStorage.getAccessToken()) {
      requestedTenantId = brandTenantId.value;
    }
    if (
      settingsReady.value &&
      !force &&
      (!requestedTenantId || requestedTenantId === brandTenantId.value)
    ) {
      return;
    }

    const requestSequence = ++settingsRequestSequence;
    settingsLoading.value = true;
    settingsError.value = "";

    try {
      const data = (await request({
        url: requestedTenantId ? "/sym/tenant/login-settings" : "/portal/index",
        params: requestedTenantId ? { tenantId: requestedTenantId } : undefined,
      })) as unknown;

      if (requestSequence !== settingsRequestSequence) return;

      if (data && typeof data === "object") {
        const settings = data as Record<string, unknown>;
        for (const key in settings) {
          updateSetting(key as any, settings[key] as any);
        }
        if (requestedTenantId) {
          brandTenantId.value = requestedTenantId;
        }
      }
    } catch (error: any) {
      if (requestSequence !== settingsRequestSequence) return;
      const errorMessage = error?.message || "";
      settingsError.value = errorMessage.toLowerCase().includes("timeout")
        ? "系统参数接口 15 秒内未响应，请确认后端服务已启动或检查接口代理配置。"
        : "无法读取系统参数，请确认后端服务已启动或检查接口代理配置。";
      console.error("加载系统设置失败:", error);
    } finally {
      if (requestSequence === settingsRequestSequence) {
        settingsReady.value = true;
        settingsLoading.value = false;
      }
    }
  }

  // ===== 返回状态和方法 =====
  return {
    // 布局状态
    appId,
    showShoppingCar,

    // 设置状态
    showSettings,
    settingsVisible,
    navVisible,
    settingsLoading,
    settingsReady,
    settingsError,
    brandTenantId,
    showTagsView,
    showLogo,
    showWatermark,
    watermarkContent,
    themeColor,
    systemName,
    systemCode,
    logo,
    version,
    needLogin,
    loginUrl,
    sidebarOpen,

    // 设置管理
    updateSetting,
    updateThemeColor,
    loadingSettings,

    // 面板控制
    toggleSettingsPanel,
    showSettingsPanel,
    hideSettingsPanel,

    // 重置功能
    resetSettings,
  };
});

/**
 * 用于在组件外部使用 Pinia Store
 * 参考: https://pinia.vuejs.org/core-concepts/outside-component-usage.html
 */
export function useSettingStoreHook() {
  return useSettingStore(store);
}

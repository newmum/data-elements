export interface AppRuntimeConfig {
  title?: string;
  brandName?: string;
}

declare global {
  interface Window {
    __DATA_ELEMENT_NIFI_CONFIG__?: AppRuntimeConfig;
  }
}

const runtimeConfig = window.__DATA_ELEMENT_NIFI_CONFIG__ ?? {};

export const appConfig = {
  title: runtimeConfig.title || import.meta.env.VITE_APP_TITLE || '海通数据流开发平台',
  brandName: runtimeConfig.brandName || import.meta.env.VITE_APP_BRAND_NAME || runtimeConfig.title || import.meta.env.VITE_APP_TITLE || '海通数据流开发平台',
};

export const isChengtianIntegration = import.meta.env.VITE_PLATFORM_MODE === 'chengtian';

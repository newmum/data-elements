// https://cn.vitejs.dev/guide/env-and-mode

// TypeScript 类型提示都为 string： https://github.com/vitejs/vite/issues/6930
interface ImportMetaEnv {
  /** 应用端口 */
  VITE_APP_PORT: number;
  /** 应用名称 */
  VITE_APP_NAME: string;
  /** API 基础路径(代理前缀) */
  VITE_APP_BASE_API: string;
  /** nifi API 基础路径(代理前缀) */
  VITE_APP_NIFI_API: string;
  /** API 地址 */
  VITE_APP_API_URL: string;
  /** 祺智系统入口地址；支持同源子路径或完整 URL */
  VITE_QIZHI_BASE_URL: string;
  VITE_REPORT_APP_URL: string;
  VITE_HAITONG_BASE_URL: string;
  VITE_WANXIANG_GOVERNANCE_URL: string;
  VITE_SEARCH_APP_URL: string;
  VITE_IDAAS_CENTER_URL: string;
  /** 是否开启 Mock 服务 */
  VITE_MOCK_DEV_SERVER: boolean;
  /** 永久token 免登录 */
  VITE_APP_FOREVER_TOKEN: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

/**
 * 平台的名称、版本、运行所需的`node`版本、依赖、构建时间的类型提示
 */
declare const __APP_INFO__: {
  pkg: {
    name: string;
    version: string;
    engines: {
      node: string;
    };
    dependencies: Record<string, string>;
    devDependencies: Record<string, string>;
  };
  buildTimestamp: number;
};

declare module "sm-crypto";

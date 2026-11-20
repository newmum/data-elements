/** 单一配置入口；业务页面不读取 mock 开关。 */
export const mockEnabled = import.meta.env.VITE_MOCK_ENABLED === "true";
export const mockScenario = import.meta.env.VITE_MOCK_SCENARIO || "police";
export const mockRole = import.meta.env.VITE_MOCK_ROLE || "admin";
export const mockLatency = Number(import.meta.env.VITE_MOCK_LATENCY_MS || 120);
const deploymentStoragePrefix = import.meta.env.VITE_APP_STORAGE_PREFIX?.trim();

// 同源子路径部署可配置独立的会话和偏好键。Mock 仍使用按角色隔离的键。
export const scopedStorageKey = (key: string) =>
  mockEnabled
    ? `data-elements:mock:ui:v3:${mockRole}:${key}`
    : deploymentStoragePrefix
      ? `data-elements:${deploymentStoragePrefix}:${key}`
      : key;

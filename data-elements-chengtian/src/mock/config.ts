/** 单一配置入口；业务页面不读取 mock 开关。 */
export const mockEnabled = import.meta.env.VITE_MOCK_ENABLED === "true";
export const mockScenario = import.meta.env.VITE_MOCK_SCENARIO || "police";
export const mockRole = import.meta.env.VITE_MOCK_ROLE || "admin";
export const mockLatency = Number(import.meta.env.VITE_MOCK_LATENCY_MS || 120);
// 行业切换沿用现有会话和登录选择流程；UI 缓存仅隔离真实模式与 Mock 角色。
export const scopedStorageKey = (key: string) =>
  mockEnabled ? `data-elements:mock:ui:v3:${mockRole}:${key}` : key;

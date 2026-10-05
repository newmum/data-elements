/** One address registry for navigation, login recovery and the session bridge. */
export const capabilityCenterBases = {
  qizhi: import.meta.env.VITE_QIZHI_BASE_URL?.trim() || (import.meta.env.DEV ? "http://localhost:3001/" : "/qizhi/"),
  report: import.meta.env.VITE_REPORT_APP_URL?.trim() || (import.meta.env.DEV ? "http://localhost:3004/" : "/report/"),
  haitong: import.meta.env.VITE_HAITONG_BASE_URL?.trim() || (import.meta.env.DEV ? "http://localhost:3002/" : "/haitong/"),
  wanxiang: import.meta.env.VITE_WANXIANG_GOVERNANCE_URL?.trim() || (import.meta.env.DEV ? "http://localhost:3010/" : "/wanxiang-governance/"),
  search: import.meta.env.VITE_SEARCH_APP_URL?.trim() || (import.meta.env.DEV ? "http://localhost:3300/" : "/search/"),
};

/** 统一身份管理平台只接入产品导航，独立身份适配器不加入数据中台会话桥的接收范围。 */
export const identityCenterBase = import.meta.env.VITE_IDAAS_CENTER_URL?.trim() || (import.meta.env.DEV ? "http://localhost:3005/" : "/idaas/");

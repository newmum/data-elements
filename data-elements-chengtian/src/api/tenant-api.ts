import request from "@/utils/request";

export interface TenantOption {
  tid: string;
  code: string;
  name: string;
  status: number;
  is_default?: number;
}

export interface TenantContextInfo extends TenantOption {
  available: TenantOption[];
}

const TenantAPI = {
  loginOptions() {
    return request<any, TenantOption[]>({
      url: "/sym/tenant/login-options",
      method: "get",
    });
  },

  loginSettings(tenantId: string) {
    return request<any, Record<string, unknown>>({
      url: "/sym/tenant/login-settings",
      method: "get",
      params: { tenantId },
    });
  },

  current() {
    return request<any, TenantContextInfo>({
      url: "/sym/tenant/current",
      method: "get",
    });
  },

  switchTenant(tenantId: string) {
    return request<any, TenantContextInfo>({
      url: "/sym/tenant/switch",
      method: "post",
      data: { tenantId },
    });
  },
};

export default TenantAPI;

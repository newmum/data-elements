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
      url: "/portal/sso/options",
      method: "get",
    });
  },

  loginSettings(tenantId: string) {
    return request<any, Record<string, unknown>>({
      url: "/portal/sso/settings",
      method: "get",
      params: { tenantId },
    });
  },

  current() {
    return request<any, TenantContextInfo>({
      url: "/portal/sso/session",
      method: "get",
    });
  },

  switchTenant(tenantId: string) {
    return request<any, TenantContextInfo>({
      url: "/portal/sso/switch",
      method: "post",
      data: { tenantId },
    });
  },
};

export default TenantAPI;

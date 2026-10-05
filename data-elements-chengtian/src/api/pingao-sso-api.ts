import request from "@/utils/request";

export const pingaoSsoEnabled = () => import.meta.env.VITE_PINGAO_SSO_ENABLED === "true";

export function pingaoLoginUrl(): string {
  const base = (import.meta.env.VITE_APP_BASE_API || "").replace(/\/$/, "");
  return `${base}/identity/pingao/sso/login`;
}

export const PingaoSsoAPI = {
  exchange(ticket: string) {
    return request<any, { token: string; tenantId: string }>({
      url: "/identity/pingao/sso/exchange", method: "post", data: { ticket },
    });
  },
  logout() {
    return request<any, { logoutUrl: string }>({ url: "/identity/pingao/sso/logout", method: "post" });
  },
};

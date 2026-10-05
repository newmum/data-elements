import request from "@/utils/request";
import sm from "sm-crypto";
import { useSettingStore } from "@/store";
const AUTH_BASE_URL = "/portal";

const AuthAPI = {
  /** 登录接口*/
  login(data: LoginFormData) {
    const formData = {
      account: data.username,
      password: sm.sm3(data.password),
      appId: useSettingStore().appId,
      // The login page's hidden industry selector must determine the initial
      // tenant context, not merely the displayed portal brand.
      tenantId: data.tenantId || undefined,
    };
    return request<any, LoginResult>({
      url: `${AUTH_BASE_URL}/login`,
      method: "post",
      data: formData,
    });
  },

  /** 退出登录接口 */
  logout() {
    return request({
      url: `${AUTH_BASE_URL}/logout`,
      method: "post",
    });
  },

  /** SSO 统一认证登录 GET 请求，通过 ticket 或 code 参数认证 */
  ssoLogin(params: { ticket?: string; code?: string }) {
    return request<any, { token: string }>({
      url: `${AUTH_BASE_URL}/ssoLogin`,
      method: "get",
      params,
    });
  },
};

export default AuthAPI;

/** 登录表单数据 */
export interface LoginFormData {
  /** 用户名 */
  username: string;
  /** 密码 */
  password: string;
  /** 验证码缓存key */
  captchaKey: string;
  /** 验证码 */
  captchaCode: string;
  /** 记住我 */
  rememberMe: boolean;
  /** 登录后需要切换的租户，空值使用账号默认租户 */
  tenantId?: string;
}

/** 登录响应 */
export interface LoginResult {
  /** 访问令牌 */
  token: string;
  /** 登录首页 */
  url: string;
  /** 后端已绑定的租户；旧版登录接口可能不返回 */
  tenantId?: string;
}

/** 验证码信息 */
export interface CaptchaInfo {
  /** 验证码缓存key */
  captchaKey: string;
  /** 验证码图片Base64字符串 */
  captchaBase64: string;
}

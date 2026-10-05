import { Storage } from "./storage";
import { AUTH_KEYS } from "@/enums";

// 负责本地凭证与偏好的读写
export const AuthStorage = {
  getAccessToken(): string {
    const isRememberMe = Storage.get<boolean>(AUTH_KEYS.REMEMBER_ME, false);
    return isRememberMe
      ? Storage.get(AUTH_KEYS.ACCESS_TOKEN, "")
      : Storage.sessionGet(AUTH_KEYS.ACCESS_TOKEN, "");
  },

  setTokens(accessToken: string, rememberMe: boolean): void {
    Storage.set(AUTH_KEYS.REMEMBER_ME, rememberMe);
    if (rememberMe) {
      Storage.set(AUTH_KEYS.ACCESS_TOKEN, accessToken);
    } else {
      Storage.sessionSet(AUTH_KEYS.ACCESS_TOKEN, accessToken);
      Storage.remove(AUTH_KEYS.ACCESS_TOKEN);
    }
  },

  clearAuth(): void {
    Storage.remove(AUTH_KEYS.ACCESS_TOKEN);
    Storage.sessionRemove(AUTH_KEYS.ACCESS_TOKEN);
  },

  getRememberMe(): boolean {
    return Storage.get<boolean>(AUTH_KEYS.REMEMBER_ME, true);
  },
};

// 导出常量
export const ROLE_ROOT = "ROOT";

// 🔗 导出所有存储键常量
/**
 * 存储键常量统一管理
 * 包括 localStorage、sessionStorage 等各种存储的键名
 */
// 认证相关键集合
export const enum AUTH_KEYS {
  ACCESS_TOKEN = "token",
  REMEMBER_ME = "remember_me",
  REMEMBERED_ACCOUNT = "remembered_login_account",
  REMEMBERED_PASSWORD = "remembered_login_password",
  /** Last valid industry tenant selected on the login page. Kept independently of password remembrance. */
  LAST_SELECTED_TENANT = "last_selected_tenant",
}

// 缓存相关键集合
export const enum CACHE_KEYS {
  DICT_CACHE = "dict_cache",
}

import { isString } from "lodash-es";
import { scopedStorageKey } from "@/mock/config";

/**
 * 存储工具类
 * 提供localStorage和sessionStorage操作方法
 */
export class Storage {
  /**
   * localStorage 存储
   */
  static set(key: string, value: any): void {
    localStorage.setItem(scopedStorageKey(key), isString(value) ? value : JSON.stringify(value));
  }

  static get<T>(key: string, defaultValue?: T): T {
    const value = localStorage.getItem(scopedStorageKey(key));
    if (!value) return defaultValue as T;

    try {
      return JSON.parse(value);
    } catch {
      // 如果解析失败，返回原始字符串
      return value as unknown as T;
    }
  }

  /** 文本值保持原样，避免数字账号、长 ID 或密文被 JSON.parse 转成数字。 */
  static getString(key: string, defaultValue = ""): string {
    return localStorage.getItem(scopedStorageKey(key)) ?? defaultValue;
  }

  static remove(key: string): void {
    localStorage.removeItem(scopedStorageKey(key));
  }

  /**
   * sessionStorage 存储
   */
  static sessionSet(key: string, value: any): void {
    sessionStorage.setItem(scopedStorageKey(key), JSON.stringify(value));
  }

  static sessionGet<T>(key: string, defaultValue?: T): T {
    const value = sessionStorage.getItem(scopedStorageKey(key));
    if (!value) return defaultValue as T;

    try {
      return JSON.parse(value);
    } catch {
      // 如果解析失败，返回原始字符串
      return value as unknown as T;
    }
  }

  static sessionRemove(key: string): void {
    sessionStorage.removeItem(scopedStorageKey(key));
  }
}

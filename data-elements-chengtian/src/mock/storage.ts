import { useStorage, useSessionStorage } from "@vueuse/core";
import { scopedStorageKey } from "./config";

export function useScopedStorage<T>(key: string, value: T | null) {
  return value === null
    ? useStorage<T>(scopedStorageKey(key), null)
    : useStorage<T>(scopedStorageKey(key), value);
}

export function useScopedSessionStorage<T>(key: string, value: T | null) {
  return value === null
    ? useSessionStorage<T>(scopedStorageKey(key), null)
    : useSessionStorage<T>(scopedStorageKey(key), value);
}

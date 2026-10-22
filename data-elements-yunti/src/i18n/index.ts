import { useUiStore, type Locale } from '@/store/uiStore';
import { zh } from './zh';
import { en } from './en';

const dictionaries: Record<Locale, Record<string, string>> = { zh, en };

/** Translate a key for a given locale; falls back to zh, then to the key itself. */
export function translate(key: string, locale: Locale): string {
  return dictionaries[locale]?.[key] ?? zh[key] ?? key;
}

/** Hook returning a `t(key)` bound to the current locale. */
export function useT(): (key: string) => string {
  const locale = useUiStore((s) => s.locale);
  return (key: string) => translate(key, locale);
}

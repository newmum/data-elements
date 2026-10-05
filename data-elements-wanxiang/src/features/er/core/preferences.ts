/** Browser preferences are independent of business metadata and never follow OS dark mode implicitly. */
export const THEME_KEY = 'data-elements-wanxiang:theme';
export const UI_KEY = 'data-elements-wanxiang:preferences';
export type Preferences = { libraryWidth: number; inspectorWidth: number; minimap: boolean };
export const DEFAULT_PREFERENCES: Preferences = { libraryWidth: 280, inspectorWidth: 440, minimap: false };
export function clampPanelWidth(value: unknown, side: 'library' | 'inspector'): number {
  const fallback = side === 'library' ? 280 : 440;
  return typeof value === 'number' && Number.isFinite(value)
    ? Math.min(side === 'library' ? 380 : 620, Math.max(side === 'library' ? 240 : 380, Math.round(value)))
    : fallback;
}
export function readTheme(storage?: Pick<Storage, 'getItem'>): 'light' | 'dark' {
  try { return (storage ?? globalThis.localStorage)?.getItem(THEME_KEY) === 'dark' ? 'dark' : 'light'; }
  catch { return 'light'; }
}
export function readPreferences(): Preferences {
  try {
    const raw = JSON.parse(globalThis.localStorage?.getItem(UI_KEY) || '{}') as Partial<Preferences>;
    return { libraryWidth: clampPanelWidth(raw?.libraryWidth, 'library'), inspectorWidth: clampPanelWidth(raw?.inspectorWidth, 'inspector'), minimap: raw?.minimap === true };
  } catch { return { ...DEFAULT_PREFERENCES }; }
}
export function savePreferences(value: Preferences): void {
  try { globalThis.localStorage?.setItem(UI_KEY, JSON.stringify(value)); } catch { /* Preferences remain usable in memory. */ }
}

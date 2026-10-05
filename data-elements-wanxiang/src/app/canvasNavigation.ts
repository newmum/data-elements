/** Fullscreen canvas routing. Keep only a safe, local background location in history. */
export interface CanvasBackground {
  pathname: string;
  search: string;
  hash: string;
  key: string;
  state: null;
}
export const MODEL_PATH = '/governance/metadata/models';
export const CANVAS_BASE = '/governance/metadata/er';
export function isCanvasPath(pathname: string): boolean {
  return /^\/governance\/metadata\/er(?:\/[^/]+)?\/?$/.test(pathname);
}
export function canvasDiagramId(pathname: string): string | undefined {
  if (!isCanvasPath(pathname)) return undefined;
  const encoded = pathname.slice(CANVAS_BASE.length).replace(/^\//, '').replace(/\/$/, '');
  if (!encoded) return undefined;
  try { return decodeURIComponent(encoded); } catch { return encoded; }
}
export function canvasBackground(value: unknown): CanvasBackground | null {
  if (!value || typeof value !== 'object') return null;
  const item = value as Record<string, unknown>;
  const pathname = item.pathname;
  if (typeof pathname !== 'string' || !pathname.startsWith('/governance/') || isCanvasPath(pathname) || /[?#\\\r\n]/.test(pathname)) return null;
  const search = typeof item.search === 'string' && (item.search === '' || item.search.startsWith('?')) ? item.search : '';
  const hash = typeof item.hash === 'string' && (item.hash === '' || item.hash.startsWith('#')) ? item.hash : '';
  return { pathname, search, hash, key: typeof item.key === 'string' ? item.key : 'canvas-background', state: null };
}
export function backgroundFromState(state: unknown): CanvasBackground | null {
  return state && typeof state === 'object' ? canvasBackground((state as Record<string, unknown>).canvasBackground) : null;
}
export function fallbackCanvasBackground(): CanvasBackground {
  return { pathname: MODEL_PATH, search: '', hash: '', key: 'canvas-default-models', state: null };
}
export function backgroundUrl(background: CanvasBackground): string {
  return `${background.pathname}${background.search}${background.hash}`;
}

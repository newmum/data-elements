export const CONNECTION_PANE_MIN = 160;
export const CONNECTION_PANE_DEFAULT = 220;
export const CONNECTION_PANE_MAX = 420;
export const CONNECTION_PANE_WIDTH_KEY = 'fengchi-connection-pane-width-v2';
export const INSPECTOR_PANE_MIN = 180;
export const INSPECTOR_PANE_WIDTH_KEY = 'fengchi-inspector-pane-width-v1';

function paneWidth(saved: string | null, fallback: number, min: number): number {
  if (saved === null || !saved.trim()) return fallback;
  const value = Number(saved);
  return Number.isFinite(value) ? Math.max(min, Math.min(420, value)) : fallback;
}

export const connectionPaneWidth = (saved: string | null) => paneWidth(saved, CONNECTION_PANE_DEFAULT, CONNECTION_PANE_MIN);
export const inspectorPaneWidth = (saved: string | null) => paneWidth(saved, INSPECTOR_PANE_MIN, INSPECTOR_PANE_MIN);

/** Preserve manual widths, but migrate the earlier transient left-minimum default. */
export function readConnectionPaneWidth(storage?: Pick<Storage, 'getItem'>): number {
  try {
    const target = storage ?? localStorage;
    const saved = target.getItem(CONNECTION_PANE_WIDTH_KEY);
    if (saved !== null) return connectionPaneWidth(saved);
    const legacy = connectionPaneWidth(target.getItem('fengchi-connection-pane-width-v1'));
    return legacy === CONNECTION_PANE_MIN ? CONNECTION_PANE_DEFAULT : legacy;
  } catch { return CONNECTION_PANE_DEFAULT; }
}

export function readInspectorPaneWidth(storage?: Pick<Storage, 'getItem'>): number {
  try { return inspectorPaneWidth((storage ?? localStorage).getItem(INSPECTOR_PANE_WIDTH_KEY)); }
  catch { return INSPECTOR_PANE_MIN; }
}

export function rememberConnectionPaneWidth(width: number, storage?: Pick<Storage, 'setItem'>): number {
  const value = connectionPaneWidth(String(width));
  try { (storage ?? localStorage).setItem(CONNECTION_PANE_WIDTH_KEY, String(value)); }
  catch { /* Persistence failure must not stop resizing in this session. */ }
  return value;
}

export function rememberInspectorPaneWidth(width: number, storage?: Pick<Storage, 'setItem'>): number {
  const value = inspectorPaneWidth(String(width));
  try { (storage ?? localStorage).setItem(INSPECTOR_PANE_WIDTH_KEY, String(value)); }
  catch { /* Persistence failure must not stop resizing in this session. */ }
  return value;
}

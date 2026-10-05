import { useCallback, useEffect, useRef, useState } from 'react';
import { CHANGED } from './api';
/** Reads only from the workspace service; stale requests and errors remain explicit. */
export function useWorkspaceQuery<T>(key: string, loader: (signal: AbortSignal) => Promise<T>, live: boolean | number = false) {
  const [data, setData] = useState<T | null>(null), [error, setError] = useState<string | null>(null), [loading, setLoading] = useState(true);
  const loadRef = useRef(loader); loadRef.current = loader; const controller = useRef<AbortController | null>(null); const seq = useRef(0);
  const refresh = useCallback(() => { controller.current?.abort(); const request = new AbortController(); controller.current = request; const n = ++seq.current; setLoading(true); loadRef.current(request.signal).then(value => { if (n === seq.current) { setData(value); setError(null); } }).catch(e => { if (!request.signal.aborted && n === seq.current) setError(e instanceof Error ? e.message : String(e)); }).finally(() => { if (n === seq.current) setLoading(false); }); }, []);
  useEffect(() => { setData(null); refresh(); const change = () => refresh(); window.addEventListener(CHANGED, change); const timer = live ? window.setInterval(() => { if (!document.hidden) refresh(); }, typeof live === 'number' ? Math.max(1000, live) : 3000) : undefined; return () => { seq.current++; controller.current?.abort(); window.removeEventListener(CHANGED, change); if (timer) clearInterval(timer); }; }, [key, live, refresh]);
  return { data, error, loading, refresh };
}

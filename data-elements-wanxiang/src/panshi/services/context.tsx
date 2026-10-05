import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { Alert, Button, Result } from 'antd';
import { ResourceLoading } from '../components/ResourceLoading';
import type { Row } from '../../services/api';
import { readResourceData, resourceCommand, type ResourceData } from '../domain/service';
import { resourceSessionScope } from './transport';
export type { ResourceData } from '../domain/service';
interface ResourceContext {
  data: ResourceData; refresh: () => Promise<void>;
  act: <T = Row>(path: string, body?: unknown, version?: number, method?: string) => Promise<T>;
}
const Context = createContext<ResourceContext | null>(null);
export function useResource() {
  const context = useContext(Context);
  if (!context) throw new Error('资源上下文未就绪');
  return context;
}
export function ResourceProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<ResourceData | null>(null);
  const [error, setError] = useState('');
  const request = useRef<AbortController | null>(null);
  const mounted = useRef(true), loadedScope = useRef(''), loading = useRef(false);
  const refresh = useCallback(async () => {
    request.current?.abort();
    const controller = new AbortController(); request.current = controller;
    loading.current = true;
    try {
      const scope = resourceSessionScope();
      if (loadedScope.current && loadedScope.current !== scope) setData(null);
      const next = await readResourceData({ signal: controller.signal });
      if (!mounted.current || controller.signal.aborted) return;
      loadedScope.current = scope; setData(next); setError('');
    } catch (cause) {
      if (controller.signal.aborted || !mounted.current) return;
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally { if (request.current === controller) loading.current = false; }
  }, []);
  useEffect(() => {
    mounted.current = true; void refresh();
    const update = () => { void refresh(); };
    window.addEventListener('wanxiang:local-changed', update);
    window.addEventListener('panshi:server-changed', update);
    return () => {
      mounted.current = false; request.current?.abort();
      window.removeEventListener('wanxiang:local-changed', update);
      window.removeEventListener('panshi:server-changed', update);
    };
  }, [refresh]);
  const runningJobs = data?.state.materializations.some(job => ['QUEUED', 'RUNNING', 'CANCELLING'].includes(job.state)) ?? false;
  useEffect(() => {
    if (!runningJobs) return;
    const timer = window.setInterval(() => { if (!document.hidden && !loading.current) void refresh(); }, 3000);
    return () => window.clearInterval(timer);
  }, [runningJobs, refresh]);
  const act = useCallback(async <T = Row,>(path: string, body: unknown = {}, version?: number, method = 'POST'): Promise<T> => {
    if (loadedScope.current !== resourceSessionScope()) throw new Error('登录或租户已切换，请刷新后重试');
    if (method !== 'GET') {
      const permission = /^materializations\/[^/]+\/(execute|retry)$/.test(path) ? 'execute' : /^catalogs\/[^/]+\/review$/.test(path) ? 'review' : path === 'policy' ? 'manage' : 'write';
      if (!data?.capabilities[permission]) throw new Error('当前账号没有此项资源操作权限');
    }
    const result = await resourceCommand<T>(path, { body, version, method });
    await refresh(); return result;
  }, [data, refresh]);
  if (!data) return error
    ? <Result status="error" title="数据资源中心读取失败" subTitle={error} extra={<Button onClick={() => void refresh()}>重新加载</Button>} />
    : <ResourceLoading/>;
  return <Context.Provider value={{ data, refresh, act }}>
    {error && <Alert type="error" showIcon title="刷新失败，当前仍显示上次读取结果" description={error} action={<Button onClick={() => void refresh()}>重试</Button>} />}
    {children}
  </Context.Provider>;
}

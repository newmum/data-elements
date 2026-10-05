import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Alert, Button, Empty, Skeleton, Space, Tooltip } from 'antd';
import { FolderOutlined, ReloadOutlined } from '@ant-design/icons';
import { ASSETS_CHANGED } from '../../services/assets';

export function AssetPage({ title, description, actions, children }: { title: string; description?: string; actions?: ReactNode; children: ReactNode }) {
  return <section className="hy-page"><header className="hy-heading"><div><span className="hy-eyebrow">皓月数据资产 · HAOYUE</span><h1>{title}</h1>{description && <p>{description}</p>}</div><Space wrap className="hy-heading-actions">{actions}</Space></header>{children}</section>;
}
export function AssetPanel({ title, extra, className = '', children }: { title?: ReactNode; extra?: ReactNode; className?: string; children: ReactNode }) {
  return <section className={`hy-panel ${className}`}>{(title || extra) && <header className="hy-panel-head"><h2>{title}</h2>{extra}</header>}<div className="hy-panel-body">{children}</div></section>;
}
const labels: Record<string,string> = { DRAFT:'草稿',REGISTERED:'已登记',VALIDATED:'待发布',LISTED:'已上架',NOT_LISTED:'未上架',OFFLINE:'已下架',PUBLISHED:'已发布',ABANDONED:'已作废',IN_REVIEW:'审批中',SUBMITTED:'待受理',APPROVED:'审批通过',REJECTED:'审批不通过',RETURNED:'退回补正',WITHDRAWN:'已撤回',ACTIVE:'已生效',SUSPENDED:'已暂停',EXPIRED:'已到期',REVOKED:'已撤销',PREPARING:'准备交付',PROVISIONING:'交付中',READY:'交付就绪',FAILED:'失败',OPEN:'待受理',ACCEPTED:'已受理',MATCHING:'对接中',MATCHED:'已匹配',COMPLETED:'已完成',CLOSED:'已关闭',UNFULFILLED:'暂无法满足',NOT_CHECKED:'未检查',PASSED:'检查通过',ISSUES:'发现问题',UNAVAILABLE:'暂不可用',NOT_CONNECTED:'未接入',AVAILABLE:'可用',PENDING:'待审批',RUNNING:'执行中',CONNECTED:'连接正常' };
export function AssetStatus({ value }: { value?: unknown }) {
  const text=String(value ?? '');
  const tone=['ACTIVE','READY','PASSED','LISTED','PUBLISHED','COMPLETED','CONNECTED','APPROVED'].includes(text)?'success':['FAILED','REJECTED','ISSUES'].includes(text)?'danger':['IN_REVIEW','PENDING','MATCHING','PROVISIONING','SUBMITTED'].includes(text)?'review':['DRAFT','NOT_LISTED','WITHDRAWN','NOT_CHECKED','UNAVAILABLE','NOT_CONNECTED',''].includes(text)?'neutral':'warning';
  return <span className={`hy-status is-${tone}`}><i/>{labels[text] ?? (text || '—')}</span>;
}
export function AssetName({ name, code, icon, onClick }: { name?: string; code?: string; icon?: ReactNode; onClick?: () => void }) {
  return <div className="hy-name"><span className="hy-object-icon">{icon ?? <FolderOutlined/>}</span><div><Tooltip title={name}><button className="hy-name-title" type="button" disabled={!onClick} onClick={onClick}>{name || '未命名'}</button></Tooltip>{code && <span className="hy-name-code" title={code}>{code}</span>}</div></div>;
}
export function AssetError({ error, retry }: { error?: unknown; retry?: () => void }) {
  return error ? <Alert className="hy-error" type="error" showIcon title={error instanceof Error ? error.message : String(error)} action={retry ? <Button icon={<ReloadOutlined/>} onClick={retry}>重试</Button> : undefined}/> : null;
}
export function AssetLoading({ loading, empty, children }: { loading: boolean; empty?: boolean; children: ReactNode }) {
  return loading ? <Skeleton active paragraph={{rows:5}}/> : empty ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无数据"/> : <>{children}</>;
}
export function useAssetQuery<T>(key: string, loader: (signal: AbortSignal) => Promise<T>) {
  const [data,setData]=useState<T|null>(null),[loading,setLoading]=useState(true),[error,setError]=useState<Error|null>(null);
  const ref=useRef(loader),controller=useRef<AbortController|null>(null),seq=useRef(0);ref.current=loader;
  const refresh=useCallback(()=>{controller.current?.abort();const next=new AbortController();controller.current=next;const request=++seq.current;setLoading(true);ref.current(next.signal).then(value=>{if(request===seq.current&&!next.signal.aborted){setData(value);setError(null);}}).catch(e=>{if(request===seq.current&&!next.signal.aborted)setError(e instanceof Error?e:new Error(String(e)));}).finally(()=>{if(request===seq.current)setLoading(false);});},[]);
  useEffect(()=>{setData(null);setError(null);refresh();window.addEventListener(ASSETS_CHANGED,refresh);return()=>{seq.current++;controller.current?.abort();window.removeEventListener(ASSETS_CHANGED,refresh);};},[key,refresh]);
  return {data,loading,error,refresh};
}
export const assetDate=(value?: string | number | null)=>{if(!value)return '—';const d=new Date(value);return Number.isNaN(d.getTime())?'—':d.toLocaleString('zh-CN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false});};
export const assetCount=(value?:number|null)=>value==null?'—':value.toLocaleString('zh-CN');

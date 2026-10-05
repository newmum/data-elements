import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { Alert, App, Button, Empty, Tag, Tooltip } from 'antd';
import { ASSETS_CHANGED, assetOperation } from '../../../services/assets';
import type { TimelineEvent } from '../../../services/assets.types';
import './consumer.css';

export const channelNames: Record<string, string> = { API: 'API 服务', FILE: '文件快照', RESTRICTED_QUERY: '受控查询', TABLE_DISTRIBUTION: '库表分发' };
export const kindNames: Record<string, string> = { APPLICATION: '应用系统', DATASOURCE: '业务库', TABLE: '数据表', CATALOG: '资源目录', API: 'API 服务', FILE: '文件资源' };
export const stateNames: Record<string, string> = { DRAFT: '草稿', IN_REVIEW: '审批中', APPROVED: '审批通过', REJECTED: '已拒绝', RETURNED: '退回补正', WITHDRAWN: '已撤回', SUBMITTED: '待受理', TRIAGED: '已受理', MATCHING: '对接中', MATCHED: '已匹配', FULFILLED: '已完成', CLOSED: '已关闭', PENDING: '待办理', COMPLETED: '已办结', CANCELLED: '已取消', ACTIVE: '授权有效', PENDING_ACTIVATION: '待授权生效', ACTIVATION_FAILED: '授权失败', SUSPENDED: '已暂停', SUPERSEDED: '已替代', PREPARING: '准备交付', PROVISIONING: '交付中', READY: '交付就绪', FAILED: '交付失败', EXPIRED: '已到期', REVOKED: '已撤销', LISTED: '已上架', OFFLINE: '已下架', NOT_LISTED: '未上架', PUBLISHED: '已发布', VALIDATED: '预检通过', ABANDONED: '已放弃' };
export const formatTime = (value?: string) => value && !Number.isNaN(Date.parse(value)) ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value)) : '—';
export const displayType = (value: { dataType?: string; length?: number; precision?: number; scale?: number }) => { const type = value.dataType || '—'; const size = value.length ?? value.precision; return type.includes('(') || size == null ? type : `${type}(${size}${value.scale != null ? `,${value.scale}` : ''})`; };
export const can = (actions: string[] | undefined, action: string) => actions?.includes(action) ?? false;
export const toLocalDate = (value?: string) => value ? value.slice(0, 10) : '';
export const dateToIso = (value?: string, end = false) => value ? new Date(`${value}T${end ? '23:59:59' : '00:00:00'}`).toISOString() : undefined;

export function ConsumerPage({ title, description, actions, children }: { title: string; description: string; actions?: ReactNode; children: ReactNode }) {
  return <section className="hy-consumer"><header className="hy-consumer-heading"><div><span className="hy-eyebrow">皓月数据资产 · HAOYUE</span><h1>{title}</h1><p>{description}</p></div><div className="hy-toolbar-actions">{actions}</div></header>{children}</section>;
}
export function ConsumerError({ error, retry }: { error: unknown; retry?: () => void }) { return error ? <Alert className="hy-request-error" type="error" showIcon title={error instanceof Error ? error.message : String(error)} action={retry && <Button onClick={retry}>重试</Button>}/> : null; }
export function ConsumerEmpty({ text = '暂无记录' }: { text?: string }) { return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={text}/>; }
export function ConsumerName({ name, code, onClick }: { name: string; code?: string; onClick?: () => void }) { return <div className="hy-consumer-name"><Tooltip title={name}>{onClick ? <button onClick={onClick}>{name}</button> : <strong>{name}</strong>}</Tooltip>{code && <Tooltip title={code}><small>{code}</small></Tooltip>}</div>; }
export function ConsumerStatus({ value }: { value?: string }) { const color = ['READY', 'ACTIVE', 'APPROVED', 'FULFILLED', 'COMPLETED', 'LISTED', 'PUBLISHED'].includes(value || '') ? 'green' : ['REJECTED', 'FAILED', 'ACTIVATION_FAILED'].includes(value || '') ? 'red' : ['IN_REVIEW', 'SUBMITTED', 'PENDING', 'MATCHING', 'PREPARING', 'PROVISIONING'].includes(value || '') ? 'gold' : undefined; return <Tag color={color}>{stateNames[value || ''] || value || '—'}</Tag>; }

/** Abort obsolete reads. Business mutations refresh the current server view; never cache business data locally. */
export function useConsumerRead<T>(key: string, loader: (signal: AbortSignal) => Promise<T>, enabled = true) {
  const [data, setData] = useState<T | null>(null), [error, setError] = useState<unknown>(null), [loading, setLoading] = useState(enabled);
  const loaderRef = useRef(loader); loaderRef.current = loader;
  const controller = useRef<AbortController | null>(null), sequence = useRef(0);
  const refresh = useCallback(() => { if (!enabled) return; controller.current?.abort(); const next = new AbortController(); controller.current = next; const request = ++sequence.current; setLoading(true); setError(null); loaderRef.current(next.signal).then(result => { if (sequence.current === request) setData(result); }).catch(cause => { if (!next.signal.aborted && sequence.current === request) setError(cause); }).finally(() => { if (sequence.current === request) setLoading(false); }); }, [enabled]);
  useEffect(() => { setData(null); setError(null); setLoading(enabled); refresh(); window.addEventListener(ASSETS_CHANGED, refresh); return () => { sequence.current++; controller.current?.abort(); window.removeEventListener(ASSETS_CHANGED, refresh); }; }, [key, enabled, refresh]);
  return { data, error, loading, refresh };
}

export function useConsumerAction() {
  const { message } = App.useApp(); const [busy, setBusy] = useState(false); const locked = useRef(false); const operation = useRef(assetOperation());
  const run = async <T,>(action: () => Promise<T>, success?: string): Promise<T | undefined> => { if (locked.current) return; locked.current = true; setBusy(true); try { const result = await action(); if (success) message.success(success); return result; } catch (error) { message.error(error instanceof Error ? error.message : String(error)); return undefined; } finally { locked.current = false; setBusy(false); } };
  return { busy, run, operation: operation.current };
}

export function EventHistory({ events }: { events: TimelineEvent[] }) { return events.length ? <ol className="hy-event-history">{events.map(event => <li key={event.id}><span className="hy-event-dot"/><div><strong>{stateNames[event.toStatus || ''] || stateNames[event.action] || event.action}</strong><small>{formatTime(event.at)}{event.operatorName ? ` · ${event.operatorName}` : ''}</small>{event.opinion && <p>{event.opinion}</p>}</div></li>)}</ol> : <ConsumerEmpty text="暂无处理记录"/>; }

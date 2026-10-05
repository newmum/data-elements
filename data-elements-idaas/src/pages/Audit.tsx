import { useEffect, useRef, useState } from 'react';
import { App, Alert, Button, Card, DatePicker, Descriptions, Drawer, Input, Select, Space, Table, Tabs, Tag, Tooltip } from 'antd';
import { ApiOutlined, AuditOutlined, ClockCircleOutlined, DownloadOutlined, GlobalOutlined, LaptopOutlined, LoginOutlined, SafetyCertificateOutlined, WarningOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { useDatabase, useSession, api, foundationApi } from '../mock/store';
import { accessibleAppIds } from '../domain/engine';
import type { Domain, Log } from '../domain/types';
import { CardMetric, DataTable, dateText, exportCsv, options, PageTitle, Text } from '../components/common';
import { AppIcon, Donut, UserAvatar } from '../components/visuals';
import { TrendChart } from './Dashboard';
import { auditActionLabel, auditActorLabel, auditTargetLabel } from '../domain/audit-presentation';
const level = (l: Log) => l.risk || (l.result === '失败' ? 'high' : l.type === 'operation' ? 'medium' : 'low');
const riskNames = { low: '低', medium: '中', high: '高' };
const riskColors = { low: 'success', medium: 'warning', high: 'error' };
interface AuditEvent {
    id: string; event_type: string; event_time: string; identity_domain?: Domain; actor_kind?: string; actor_id?: string; actor_name?: string; actor_username?: string;
    action: string; app_id?: string; object_id?: string; result: string; reason_code?: string; trace_id: string; ip?: string;
    request_path?: string; http_method?: string; http_status?: number; duration_ms?: number; snapshot_json?: string;
}
function FoundationAudit({ type }: { type: 'operation' | 'login' | 'api' }) {
    const db = useDatabase();
    const generation = useRef(0);
    const [rows, setRows] = useState<AuditEvent[]>([]), [total, setTotal] = useState(0), [page, setPage] = useState(1), [size, setSize] = useState(20);
    const [query, setQuery] = useState(''), [result, setResult] = useState('all'), [busy, setBusy] = useState(false), [error, setError] = useState('');
    const [detail, setDetail] = useState<AuditEvent | null>(null);
    const title = type === 'api' ? 'API 日志' : type === 'login' ? '登录日志' : '操作日志';
    const load = async () => {
        const current = ++generation.current;
        setBusy(true); setError('');
        const args = new URLSearchParams({ page: String(page), size: String(size), type: type === 'api' ? 'API' : type === 'login' ? 'AUTH' : 'OPERATION', q: query });
        if (result !== 'all') args.set('result', result);
        try { const response = await foundationApi.read<{ list: AuditEvent[]; total: number }>('/idaas/audit/list?' + args); if (current === generation.current) { setRows(response.list); setTotal(response.total); } }
        catch (e) { if (current === generation.current) { setRows([]); setTotal(0); setError(e instanceof Error ? e.message : '审计查询失败'); } }
        finally { if (current === generation.current) setBusy(false); }
    };
    useEffect(() => { setPage(1); setDetail(null); }, [type]);
    useEffect(() => { void load(); }, [type, page, size, query, result]);
    const actorLabel = (row: AuditEvent) => row.actor_kind === 'SYSTEM' ? '系统任务' : auditActorLabel(row.actor_id, db, null, row.actor_name || row.actor_username);
    const targetLabel = (row: AuditEvent) => row.event_type === 'API' && !row.object_id ? row.request_path || '平台接口' : auditTargetLabel({ action: row.action, target: row.object_id, domain: row.identity_domain, appId: row.app_id }, db);
    const exportRows = () => exportCsv(title + '-当前页', ['发生时间', '操作人', '动作', '目标', '结果', '原因', '来源IP', '追踪ID', '动作编码', '对象ID'], rows.map(r => [r.event_time, actorLabel(r), auditActionLabel(r.action), targetLabel(r), r.result, r.reason_code, r.ip, r.trace_id, r.action, r.object_id]));
    return <>
        <PageTitle title={title} description={type === 'login' ? '查询平台操作账号的登录结果。' : type === 'api' ? '查询平台接口的真实调用记录、路径与耗时。' : '查询平台管理动作与权限拒绝记录。'} extra={<Space><Button loading={busy} onClick={() => void load()}>刷新</Button><Button icon={<DownloadOutlined />} disabled={busy || !rows.length} onClick={exportRows}>导出当前页</Button></Space>}/>
        {error && <Alert type="error" showIcon title={error} style={{ marginBottom: 16 }}/>} 
        <div className="metric-grid four">
            <CardMetric label="符合条件的记录" value={total} icon={<AuditOutlined />} description="按服务端当前查询条件统计"/>
            <CardMetric label="本页成功" value={rows.filter(r => r.result === 'SUCCESS').length} icon={<SafetyCertificateOutlined />} tone="green" description="当前页成功记录"/>
            <CardMetric label="本页拒绝" value={rows.filter(r => r.result === 'DENIED').length} icon={<WarningOutlined />} tone="purple" description="当前页权限拒绝记录"/>
            <CardMetric label="本页失败" value={rows.filter(r => r.result === 'FAILED').length} icon={<WarningOutlined />} tone="orange" description="当前页失败记录"/>
        </div>
        <Card size="small" className="section-gap"><Space wrap>
            <Input.Search aria-label="查询审计记录" placeholder="搜索账号、事件、来源IP或追踪ID" allowClear style={{ width: 330, maxWidth: '100%' }} onSearch={value => { setPage(1); setQuery(value); }}/>
            <Select aria-label="审计结果" value={result} style={{ width: 160 }} onChange={value => { setPage(1); setResult(value); }} options={[{ value: 'all', label: '全部结果' }, { value: 'SUCCESS', label: '成功' }, { value: 'DENIED', label: '拒绝' }, { value: 'FAILED', label: '失败' }]}/>
        </Space></Card>
        <Table<AuditEvent> size="small" rowKey="id" loading={busy} dataSource={rows} scroll={{ x: 1150 }} pagination={{ current: page, pageSize: size, total, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100], showTotal: value => `共 ${value} 条`, onChange: (next, length) => { setPage(next); setSize(length); } }} columns={[
            { title: '发生时间', dataIndex: 'event_time', width: 170, render: dateText },
            { title: '事件', dataIndex: 'action', width: 260, ellipsis: true, render: (value: string) => <span title={auditActionLabel(value)}>{auditActionLabel(value)}</span> },
            { title: '操作人', width: 140, render: (_, row) => actorLabel(row) },
            { title: '对象 / 路径', width: 260, ellipsis: true, render: (_, row) => <span title={targetLabel(row)}>{targetLabel(row)}</span> },
            { title: '来源IP', dataIndex: 'ip', width: 120 },
            { title: '结果', dataIndex: 'result', width: 90, render: value => <Tag color={value === 'SUCCESS' ? 'success' : 'error'}>{value === 'SUCCESS' ? '成功' : value === 'DENIED' ? '拒绝' : '失败'}</Tag> },
            { title: '操作', fixed: 'right', width: 80, render: (_, row) => <Button type="link" onClick={() => setDetail(row)}>详情</Button> },
        ]}/>
        <Drawer title="日志详情" open={!!detail} onClose={() => setDetail(null)} size={560}>{detail && <Descriptions size="small" column={1} items={[
            { key: 'time', label: '发生时间', children: dateText(detail.event_time) }, { key: 'actor', label: '操作账号', children: actorLabel(detail) },
            { key: 'action', label: '动作', children: auditActionLabel(detail.action) }, { key: 'target', label: '对象', children: targetLabel(detail) },
            { key: 'action-code', label: '动作编码', children: detail.action }, { key: 'object-id', label: '对象 ID', children: detail.object_id || '—' },
            { key: 'reason', label: '原因', children: detail.reason_code || '—' },
            { key: 'method', label: '请求方法', children: detail.http_method || '—' }, { key: 'path', label: '请求路径', children: detail.request_path || '—' },
            { key: 'status', label: 'HTTP状态', children: detail.http_status ?? '未记录' }, { key: 'duration', label: '耗时', children: detail.duration_ms == null ? '—' : detail.duration_ms + ' ms' },
            { key: 'trace', label: '追踪ID', children: <Text copyable>{detail.trace_id}</Text> }, { key: 'snapshot', label: '变更摘要', children: <Text style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{detail.snapshot_json || '—'}</Text> },
        ]}/>}</Drawer>
    </>;
}
export default function Audit({ domain, type }: {
    domain: Domain;
    type: 'operation' | 'login' | 'api';
}) {
    const db = useDatabase(), session = useSession()!, nav = useNavigate(), { message } = App.useApp();
    const [detail, setDetail] = useState<Log | null>(null), [range, setRange] = useState<[
        Dayjs,
        Dayjs
    ] | null>(null);
    const [result, setResult] = useState('all'), [appId, setAppId] = useState('all'), [risk, setRisk] = useState('all'), [ip, setIp] = useState('');
    useEffect(() => { setDetail(null); setResult('all'); setAppId('all'); setRisk('all'); setIp(''); setRange(null); }, [domain, type]);
    if (session.realm === 'platform') return <FoundationAudit type={type}/>;
    const allowed = accessibleAppIds(db, session);
    const visible = db.logs.filter(l => l.domain === domain && (session.role !== 'appmanager' || allowed.includes(l.appId)));
    const logs = visible.filter(l => l.type === type);
    const data = logs.filter(l => (result === 'all' || l.result === result) && (appId === 'all' || l.appId === appId) && (risk === 'all' || level(l) === risk) && (!ip || l.ip.includes(ip)) && (!range || (dayjs(l.createdAt).valueOf() >= range[0].startOf('day').valueOf() && dayjs(l.createdAt).valueOf() <= range[1].endOf('day').valueOf())));
    const title = type === 'login' ? '登录日志' : type === 'operation' ? '操作日志' : 'API 日志';
    const recent = visible.filter(l => l.type === 'login' && dayjs(l.createdAt).isAfter(dayjs().startOf('day').subtract(6, 'day')));
    const points = Array.from({ length: 7 }, (_, i) => { const d = dayjs().subtract(6 - i, 'day'), records = recent.filter(l => dayjs(l.createdAt).isSame(d, 'day')); return { date: d.format('MM-DD'), count: records.length, people: new Set(records.map(l => l.actor)).size }; });
    const ranked = Object.entries(logs.reduce<Record<string, number>>((a, l) => (a[l.action] = (a[l.action] || 0) + 1, a), {})).sort((a, b) => b[1] - a[1]).slice(0, 5);
    const exportRows = (rows: Log[]) => { exportCsv(title, ['时间', '操作账号', '动作', '对象', '结果', '来源IP', '风险等级', '追踪ID'], rows.map(l => [dateText(l.createdAt), l.actor, l.action, l.target, l.result, l.ip, riskNames[level(l)], l.traceId])); void api.recordEvent(domain, '导出' + title, `${rows.length}项`, { type }).catch(() => message.warning('文件已生成，但审计记录保存失败。')); };
    const actor = detail ? db.users.find(u => u.account === detail.actor) : undefined;
    const detailApp = detail ? db.apps.find(a => a.id === detail.appId) : undefined;
    return <><PageTitle title="安全审计中心" description="集中记录身份与访问行为，保留事件上下文，让关键操作可查询、可追溯。" extra={<Space><Button type={type === 'login' ? 'primary' : 'default'} onClick={() => nav(`/console/${domain}/audit/logins`)}>登录日志</Button><Button type={type === 'operation' ? 'primary' : 'default'} onClick={() => nav(`/console/${domain}/audit/operations`)}>操作日志</Button><Button type={type === 'api' ? 'primary' : 'default'} onClick={() => nav(`/console/${domain}/audit/api`)}>API 日志</Button></Space>}/>
  <div className="metric-grid four"><CardMetric label={`今日${type === 'login' ? '登录' : type === 'api' ? 'API 调用' : '操作'}事件`} value={logs.filter(l => dayjs(l.createdAt).isSame(dayjs(), 'day')).length} description="当前日志类别 · 今日" icon={type === 'api' ? <ApiOutlined /> : <LoginOutlined />}/><CardMetric label="失败事件" value={logs.filter(l => l.result === '失败').length} description="当前日志类别 · 全部已存记录" tone="red" icon={<WarningOutlined />}/><CardMetric label="涉及应用" value={new Set(logs.filter(l => l.appId).map(l => l.appId)).size} tone="purple" description="有事件记录的业务应用" icon={<SafetyCertificateOutlined />}/><CardMetric label="操作主体" value={new Set(logs.map(l => l.actor)).size} tone="cyan" description="按账号去重统计" icon={<AuditOutlined />}/></div>
  <div className="audit-insight-grid"><Card size="small" title="近 7 天登录趋势" extra={<span className="subtle-label">登录次数 / 独立用户</span>}><TrendChart data={points} compact/><div className="chart-legend"><span><i style={{ background: '#5876FB' }}/>登录次数</span><span><i style={{ background: '#9D82FA' }}/>独立用户</span></div></Card><Card size="small" title="事件类型排行" extra={<span className="subtle-label">{title}</span>}><div className="rank-bars">{ranked.map(([name, count], i) => <div key={name}><span className={`rank-number rank-${i}`}>{i + 1}</span><span className="rank-label" title={name}>{name}</span><div className="rank-track"><i style={{ width: `${count / (ranked[0]?.[1] || 1) * 100}%` }}/></div><strong>{count}</strong></div>)}</div></Card><Card size="small" title="事件结果分布"><Donut label="事件总数" items={[{ name: '成功', value: logs.filter(l => l.result === '成功').length, color: '#657BFA' }, { name: '失败', value: logs.filter(l => l.result === '失败').length, color: '#F56C85' }, { name: '其他', value: logs.filter(l => !['成功', '失败'].includes(l.result)).length, color: '#ADBBDB' }]}/></Card></div>
  <Card className="section-gap" title="高级筛选" size="small"><div className="audit-filters"><label><span>时间范围</span><DatePicker.RangePicker value={range} onChange={v => setRange(v && v[0] && v[1] ? [v[0], v[1]] : null)} aria-label="日志日期范围" style={{ width: '100%' }}/></label><label><span>应用系统</span><Select aria-label="目标应用" value={appId} onChange={setAppId} options={[{ value: 'all', label: '全部应用' }, ...options(db.apps.filter(a => a.domain === domain && allowed.includes(a.id)))]}/></label><label><span>操作结果</span><Select aria-label="事件结果" value={result} onChange={setResult} options={[{ value: 'all', label: '全部结果' }, { value: '成功', label: '成功' }, { value: '失败', label: '失败' }]}/></label><label><span>风险等级 <Tooltip title="分级规则：失败事件为高，管理操作为中，其余为低。"><SafetyCertificateOutlined /></Tooltip></span><Select aria-label="风险等级" value={risk} onChange={setRisk} options={[{ value: 'all', label: '全部等级' }, { value: 'high', label: '高' }, { value: 'medium', label: '中' }, { value: 'low', label: '低' }]}/></label><label><span>来源 IP</span><Input value={ip} onChange={e => setIp(e.target.value)} allowClear placeholder="输入 IP 地址" aria-label="来源IP"/></label><Button onClick={() => { setRange(null); setResult('all'); setAppId('all'); setRisk('all'); setIp(''); }}>重置条件</Button></div></Card>
  <DataTable data={data} title={title} hideStatus searchPlaceholder="搜索操作人、事件或追踪 ID" searchFields={['actor', 'target', 'traceId', 'action']} exporter={exportRows} columns={[
            { title: '发生时间', dataIndex: 'createdAt', width: 165, render: dateText, sorter: (a, b) => a.createdAt.localeCompare(b.createdAt), defaultSortOrder: 'descend' },
            { title: '事件类型', dataIndex: 'action', width: 156, render: (v, l) => <Space size={8}><span className={`event-mini ${level(l)}`}>{l.type === 'login' ? <LoginOutlined /> : l.type === 'api' ? <ApiOutlined /> : <AuditOutlined />}</span>{v}</Space> },
            { title: '操作人', dataIndex: 'actor', width: 145, render: account => { const u = db.users.find(v => v.account === account); return u ? <Space><UserAvatar user={u} size={28}/><span>{u.name}<small className="table-secondary">{account}</small></span></Space> : account; } },
            { title: '应用 / 对象', dataIndex: 'target', width: 175, ellipsis: true },
            { title: '来源 IP', dataIndex: 'ip', width: 138 },
            { title: '风险', width: 80, render: (_, l) => <Tag color={riskColors[level(l)]}>{riskNames[level(l)]}</Tag> },
            { title: '结果', dataIndex: 'result', width: 82, render: v => <Tag color={v === '成功' ? 'success' : 'error'}>{v}</Tag> },
            { title: '操作', fixed: 'right', width: 80, render: (_, l) => <Button type="link" onClick={() => setDetail(l)}>详情</Button> }
        ]}/>
  <Drawer title="日志详情" open={!!detail} size={560} onClose={() => setDetail(null)} destroyOnHidden extra={detail && <Button icon={<DownloadOutlined />} onClick={() => exportRows([detail])}>导出事件</Button>}>{detail && <><div className={`audit-detail-hero ${level(detail)}`}><span><SafetyCertificateOutlined /></span><div><h3>{detail.action} <Tag color={riskColors[level(detail)]}>{riskNames[level(detail)]}风险等级</Tag></h3><p>{detail.target}</p></div></div><Tabs size="small" items={[{ key: 'event', label: '事件详情', children: <><div className="detail-section"><h4>基本信息</h4><Descriptions column={1} size="small" items={[{ key: 'time', label: '发生时间', children: dateText(detail.createdAt) }, { key: 'actor', label: '操作人', children: actor ? `${actor.name} (${actor.account})` : detail.actor }, { key: 'org', label: '所属组织', children: db.orgs.find(o => o.id === actor?.orgId)?.name || '—' }, { key: 'app', label: '应用系统', children: detailApp ? <Space><AppIcon app={detailApp} size={24}/>{detailApp.name}</Space> : '平台管理' }, { key: 'ip', label: '来源 IP', children: detail.ip }, { key: 'result', label: '操作结果', children: <Tag color={detail.result === '成功' ? 'success' : 'error'}>{detail.result}</Tag> }, { key: 'target', label: '目标对象', children: detail.target }]}/></div><div className="detail-section"><h4>请求详情</h4><Descriptions column={1} size="small" items={[{ key: 'method', label: '请求方法', children: detail.httpMethod || '未记录' }, { key: 'path', label: '请求路径', children: <Text code>{detail.requestPath || '未记录'}</Text> }, { key: 'status', label: '响应状态', children: detail.httpStatus ?? '未记录' }, { key: 'duration', label: '响应耗时', children: detail.durationMs !== undefined ? `${detail.durationMs} ms` : '未记录' }, { key: 'trace', label: '追踪 ID', children: <Text code copyable>{detail.traceId}</Text> }]}/></div><div className="detail-section"><h4>终端与位置</h4><p><LaptopOutlined /> {detail.device || '未记录'}</p><p><GlobalOutlined /> {detail.location || '未记录位置'}</p><Text type="secondary" style={{ fontSize: 'var(--iam-font-secondary)' }}>结合来源地址与终端信息核对访问活动。</Text></div></> }, { key: 'diff', label: '数据变更对比', children: <>{detail.result === '失败' && <Alert title="本次操作未成功，未产生数据变更。" type="error" showIcon style={{ marginBottom: 16 }}/>}<div className="detail-section"><h4>变更前</h4><pre className="json-view">{detail.before}</pre></div><div className="detail-section"><h4>变更后</h4><pre className="json-view">{detail.after}</pre></div></> }]}/></>}</Drawer></>;
}

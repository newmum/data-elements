import { useEffect, useId, useState } from 'react';
import { Badge, Button, Card, Empty, Segmented, Table, Tag } from 'antd';
import { AppstoreOutlined, ArrowRightOutlined, CheckCircleFilled, ClockCircleOutlined, KeyOutlined, LoginOutlined, SafetyCertificateOutlined, SyncOutlined, TeamOutlined, WarningOutlined, LockOutlined, ApartmentOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { foundationApi, useDatabase, useSession } from '../mock/store';
import { accessibleAppIds, allowedUser, effectiveAccess, grantIsCurrent } from '../domain/engine';
import { CardMetric, PageTitle } from '../components/common';
import { AppIcon } from '../components/visuals';
import { useElementWidth } from '../components/useElementWidth';
import { chartTickIndices } from '../components/chart-layout';
import { ShieldArtwork } from '../components/Brand';
import { auditActionLabel, auditActorLabel, auditTargetLabel } from '../domain/audit-presentation';
import type { Domain, Log } from '../domain/types';
export interface TrendPoint {
    date: string;
    count: number;
    people: number;
}
export function TrendChart({ data, compact = false }: {
    data: TrendPoint[];
    compact?: boolean;
}) {
    const id = useId().replace(/:/g, '');
    const [hover, setHover] = useState<number | null>(null);
    const { ref, width: w } = useElementWidth();
    const h = compact ? 156 : 220, left = 44, right = 24, top = 16, bottom = 28;
    const ticks = new Set(chartTickIndices(data.length, w - left - right));
    const max = Math.max(...data.map(d => d.count), 1), step = Math.max(1, Math.ceil(max / 4)), limit = step * 4;
    const x = (i: number) => left + (w - left - right) * i / Math.max(1, data.length - 1);
    const y = (v: number) => h - bottom - v / limit * (h - top - bottom);
    const curve = (key: 'count' | 'people') => data.map((d, i) => i ? `C${x(i - 1) + (x(i) - x(i - 1)) * .4},${y(data[i - 1][key])} ${x(i) - (x(i) - x(i - 1)) * .4},${y(d[key])} ${x(i)},${y(d[key])}` : `M${x(i)},${y(d[key])}`).join(' ');
    return <div ref={ref} className="chart-wrap" onMouseLeave={() => setHover(null)}>
    {hover !== null && data[hover] && <div className="chart-tooltip"><div style={{ color: '#b0c0df' }}>{data[hover].date}</div><div>登录次数 <strong style={{ float: 'right' }}>{data[hover].count}</strong></div><div>独立用户 <strong style={{ float: 'right' }}>{data[hover].people}</strong></div></div>}
    <svg viewBox={`0 0 ${w} ${h}`} width="100%" height={h} role="img" aria-label={`最近${data.length}天登录次数和登录人数趋势`} style={{ display: 'block', overflow: 'visible' }}>
      <defs><linearGradient id={id} x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#6783FF" stopOpacity=".24"/><stop offset="100%" stopColor="#6783FF" stopOpacity=".01"/></linearGradient></defs>
      {Array.from({ length: 5 }, (_, i) => <g key={i}><line x1={left} x2={w - right} y1={y(i * step)} y2={y(i * step)} stroke="#EDF1F8"/><text x={left - 10} y={y(i * step) + 4} textAnchor="end" fill="#71809B" fontSize="12">{i * step}</text></g>)}
      {data.length > 0 && <><path d={`${curve('count')} L${x(data.length - 1)},${h - bottom} L${left},${h - bottom} Z`} fill={`url(#${id})`}/><path d={curve('count')} fill="none" stroke="#5876FB" strokeWidth="2.6"/><path d={curve('people')} fill="none" stroke="#9D82FA" strokeWidth="2"/></>}
      {data.map((d, i) => <g key={d.date} onMouseEnter={() => setHover(i)} onFocus={() => setHover(i)} onBlur={() => setHover(null)} tabIndex={0} aria-label={`${d.date} 登录${d.count}次，${d.people}位用户`}><rect x={x(i) - 15} y={top} width="30" height={h - top - bottom} fill="transparent"/>{!compact && <><circle cx={x(i)} cy={y(d.count)} r={hover === i ? 5 : 3.5} fill="#5D79FC" stroke="#fff" strokeWidth="1.5"/><circle cx={x(i)} cy={y(d.people)} r="3" fill="#A085FA" stroke="#fff" strokeWidth="1.5"/></>}{(ticks.has(i)) && <text x={x(i)} y={h - 6} textAnchor="middle" fontSize="12" fill="#71809B">{d.date}</text>}</g>)}
    </svg>
  </div>;
}
export default function Dashboard({ domain, statistics = false }: {
    domain: Domain;
    statistics?: boolean;
}) {
    const db = useDatabase(), session = useSession()!, nav = useNavigate();
    const [days, setDays] = useState(7), [chart, setChart] = useState('chart');
    const [actorNames, setActorNames] = useState<Record<string, string>>({});
    const canReadAudit = session.capabilities?.includes('audit') ?? false;
    useEffect(() => {
        if (session.realm !== 'platform' || !canReadAudit) { setActorNames({}); return; }
        let active = true;
        setActorNames({});
        // The paged audit API already resolves platform operator names; bootstrap retains immutable IDs.
        void foundationApi.read<{ list: { actor_id?: string; actor_name?: string; actor_username?: string }[] }>('/idaas/audit/list?page=1&size=200&type=OPERATION')
            .then(response => {
                if (!active) return;
                const names: Record<string, string> = {};
                for (const row of response.list || []) if (row.actor_id && (row.actor_name || row.actor_username)) names[row.actor_id] = row.actor_name || row.actor_username || '';
                setActorNames(names);
            })
            .catch(() => { if (active) setActorNames({}); });
        return () => { active = false; };
    }, [session.realm, session.userId, canReadAudit]);
    const actorLabel = (log: Log) => auditActorLabel(log.actor, db, session, actorNames[log.actor]);
    const base = `/console/${domain}/`;
    const apps = db.apps.filter(a => accessibleAppIds(db, session).includes(a.id));
    const users = db.users.filter(u => allowedUser(db, session, u) && u.kind !== 'admin');
    const logsAll = db.logs.filter(l => l.domain === domain && (!l.appId || apps.some(a => a.id === l.appId)));
    const logs = logsAll.filter(l => l.type === 'login' && dayjs(l.createdAt).isAfter(dayjs().startOf('day').subtract(days - 1, 'day')));
    const today = logs.filter(l => dayjs(l.createdAt).isSame(dayjs(), 'day'));
    const points: TrendPoint[] = Array.from({ length: days }, (_, i) => { const d = dayjs().subtract(days - 1 - i, 'day'); const records = logs.filter(l => dayjs(l.createdAt).isSame(d, 'day')); return { date: d.format('MM-DD'), count: records.length, people: new Set(records.map(l => l.actor)).size }; });
    const failures = logs.filter(l => l.result !== '成功');
    const rate = logs.length ? ((logs.length - failures.length) / logs.length * 100).toFixed(2) + '%' : '—';
    const grants = db.grants.filter(g => g.domain === domain);
    const expiring = grants.filter(g => grantIsCurrent(g) && g.expiresAt && dayjs(g.expiresAt).isBefore(dayjs().add(30, 'day')));
    const accessByUser = users.map(u => ({ user: u, access: effectiveAccess(db, u.id) }));
    const ranks = apps.map(a => ({ ...a, visits: logs.filter(l => l.appId === a.id).length, people: accessByUser.filter(u => u.access.some(v => v.appId === a.id)).length })).sort((a, b) => b.visits - a.visits).slice(0, 5);
    const currentTasks = db.tasks.filter(t => t.domain === domain);
    const issues = domain === 'workforce' ? [
        { title: '同步任务包含失败项', text: '查看下游响应与失败对象', count: currentTasks.filter(t => ['partial', 'failed'].includes(t.status)).length, to: 'sync/tasks', icon: <SyncOutlined />, color: '#F39736', bg: '#FFF4E9' },
        { title: '账号处于登录锁定状态', text: '核对原因后解除锁定', count: users.filter(u => u.locked).length, to: 'locked-accounts', icon: <LockOutlined />, color: '#F2647B', bg: '#FFEFF2' },
        { title: '应用登记待完善', text: '核对应用负责人和业务资料', count: apps.filter(a => a.status === 'pending').length, to: 'apps', icon: <SafetyCertificateOutlined />, color: '#6682F4', bg: '#ECF1FF' },
        { title: '授权即将到期', text: '未来30天内到期的授权记录', count: expiring.length, to: 'grants/users', icon: <ClockCircleOutlined />, color: '#A375EF', bg: '#F5EFFF' },
    ] : [
        { title: '自然人尚未完成核验', text: '实名状态与启用状态独立管理', count: users.filter(u => !u.verified).length, to: 'persons', icon: <SafetyCertificateOutlined />, color: '#F39736', bg: '#FFF4E9' },
        { title: '法人信息待核验', text: '核对联系人与经办账号关系', count: db.legalEntities.filter(e => !e.verified).length, to: 'entities', icon: <ApartmentOutlined />, color: '#6682F4', bg: '#ECF1FF' },
        { title: '登录失败事件', text: `最近${days}天失败记录`, count: failures.length, to: 'audit/logins', icon: <LockOutlined />, color: '#F2647B', bg: '#FFEFF2' },
    ];
    const operations = logsAll.filter(l => l.type === 'operation' && l.action !== 'profile:domain' && !l.action.endsWith(':read')).slice(0, 5);
    const activity = operations.filter(l => !l.action.startsWith('profile:')).slice(0, 4);
    const departments = domain === 'workforce' ? [
        { name: '内部员工', value: users.filter(u => (u.employmentType || 'employee') === 'employee').length, color: '#6381F6' },
        { name: '外部合作伙伴', value: users.filter(u => u.employmentType === 'partner').length, color: '#A382F5' },
        { name: '临时人员', value: users.filter(u => u.employmentType === 'temporary').length, color: '#55BDEB' },
    ] : [{ name: '已核验', value: users.filter(u => u.verified).length, color: '#6381F6' }, { name: '未核验', value: users.filter(u => !u.verified).length, color: '#A382F5' }];
    return <>
    {statistics && <PageTitle title="统计分析" description="从访问趋势到身份分布，了解当前身份域的使用情况。"/>}
    <div className="metric-grid five">
      <CardMetric label="身份用户总数" value={users.length} description={`${users.filter(u => u.status === 'enabled').length} 位用户已启用`} icon={<TeamOutlined />} onClick={() => nav(base + (domain === 'workforce' ? 'organization' : 'persons'))}/>
      <CardMetric label="登记应用" value={apps.length} description={`${apps.filter(a => a.status === 'enabled').length} 个应用登记已启用`} icon={<AppstoreOutlined />} tone="cyan" onClick={() => nav(base + 'apps')}/>
      <CardMetric label="今日登录次数" value={today.length} description={`${new Set(today.map(l => l.actor)).size} 位独立登录用户`} icon={<LoginOutlined />} tone="purple" onClick={() => nav(base + 'audit/logins')}/>
      <CardMetric label={domain === 'workforce' ? '生效授权记录' : '已核验自然人'} value={domain === 'workforce' ? (session.capabilities?.includes('grants') ? grants.filter(g => grantIsCurrent(g)).length : '—') : users.filter(u => u.verified).length} description={domain === 'workforce' ? (session.capabilities?.includes('grants') ? `${expiring.length} 项将在30天内到期` : '应用授权服务尚未开通') : '核验状态独立于账号状态'} icon={<KeyOutlined />} tone="purple" onClick={() => nav(base + (domain === 'workforce' ? 'grants/users' : 'persons'))}/>
      <CardMetric label="登录失败事件" value={failures.length} description={`最近 ${days} 天 · 点击查看记录`} icon={<WarningOutlined />} tone="red" onClick={() => nav(base + 'audit/logins')}/>
    </div>
    <div className="dashboard-feature-row">
      <section className="hero-card">
        <div className="hero-copy"><div className="hero-kicker">企业数字化的用户信任基石</div><h2>安全的身份<br />更高效的组织</h2><p>将人员、应用与访问权限集中管理，连接组织的每一项业务。</p><div className="hero-bullets"><span><CheckCircleFilled />统一身份管理</span><span><CheckCircleFilled />精细访问控制</span><span><CheckCircleFilled />跨应用协同</span><span><CheckCircleFilled />可追溯操作记录</span></div><Button type="primary" onClick={() => nav(base + 'apps')}>管理应用 <ArrowRightOutlined /></Button></div>
        <div className="hero-visual"><ShieldArtwork /></div>
        <div className="hero-health"><div className="hero-health-title">工作区数据 <span className="hero-live">已加载</span></div>{['身份资料', '应用登记', '角色资源', '审计记录'].map(t => <div className="health-row" key={t}><CheckCircleFilled style={{ color: '#18B789', fontSize: 10 }}/><span>{t}</span><span>已读取</span></div>)}<div className="health-rate"><span>近{days}天认证成功率</span><strong>{rate}</strong></div></div>
      </section>
      <Card size="small" className="dashboard-card" title="登录趋势" extra={<Segmented size="small" value={days} onChange={v => setDays(Number(v))} options={[{ label: '近7天', value: 7 }, { label: '近14天', value: 14 }]}/>} styles={{ body: { padding: '12px 16px' } }}>
        <div className="chart-legend"><Badge color="#5F7BFC" text={<span style={{ fontSize: 'var(--iam-font-caption)', color: '#7B8CAC' }}>总登录次数</span>}/><Badge color="#A085FA" text={<span style={{ fontSize: 'var(--iam-font-caption)', color: '#7B8CAC' }}>独立用户数</span>}/></div><TrendChart data={points}/>
      </Card>
    </div>
    <div className="dashboard-three">
      <Card size="small" className="dashboard-card" title="应用访问排行" extra={<Button type="link" size="small" className="card-link" onClick={() => nav(base + 'apps')}>查看全部 <ArrowRightOutlined /></Button>}>
        {ranks.map((app, i) => <div className="ranking-row" key={app.id}><span className="rank-number" style={{ background: i < 3 ? '#FFF2E4' : undefined, color: i < 3 ? '#EC9A41' : undefined }}>{i + 1}</span><AppIcon app={app} size={30}/><div className="rank-content"><button className="rank-name" style={{ border: 0, background: 'none', padding: 0, textAlign: 'left', cursor: 'pointer' }} onClick={() => nav(base + 'apps/' + app.id)}>{app.name}</button><div className="rank-bar"><span style={{ width: Math.max(4, app.visits / (ranks[0]?.visits || 1) * 100) + '%' }}/></div></div><div className="ranking-count">{app.visits}<small>次访问</small></div></div>)}{!ranks.length && <Empty description="暂无应用"/>}
      </Card>
      <Card size="small" className="dashboard-card" title="近期业务动态" extra={<Button type="link" size="small" className="card-link" onClick={() => nav(base + 'audit/operations')}>查看更多 <ArrowRightOutlined /></Button>}>
        {activity.map((l, i) => <div className="task-mini-row" key={l.id}><span className="attention-icon" style={{ background: ['#FFF2E8', '#EAF5FF', '#F3ECFF', '#EAF8F3'][i], color: ['#F19A51', '#559EEF', '#9B78E6', '#43B797'][i] }}>{[<KeyOutlined />, <AppstoreOutlined />, <TeamOutlined />, <SyncOutlined />][i]}</span><div className="attention-copy"><strong title={auditActionLabel(l.action)}>{auditActionLabel(l.action)}</strong><small title={auditTargetLabel(l, db)}>{auditTargetLabel(l, db)} · {actorLabel(l)}</small></div><time className="dashboard-activity-time" dateTime={l.createdAt}>{dayjs(l.createdAt).format('MM-DD')}</time></div>)}{!activity.length && <Empty description="暂无业务动态"/>}
      </Card>
      <Card size="small" className="dashboard-card" title="需要关注" extra={<Tag color="blue" style={{ fontSize: 'var(--iam-font-caption)' }}>{issues.reduce((n, v) => n + v.count, 0)} 项</Tag>}>
        {issues.map(item => <div className="attention-row" key={item.title} style={{ cursor: 'pointer' }} onClick={() => nav(base + item.to)}><span className="attention-icon" style={{ color: item.color, background: item.bg }}>{item.icon}</span><div className="attention-copy"><strong>{item.title}</strong><small>{item.text}</small></div><span className="attention-count" style={{ color: item.count ? item.color : '#98A8BB' }}>{item.count}</span></div>)}
      </Card>
    </div>
    <div className="dashboard-bottom">
      <Card size="small" className="dashboard-card" title="身份分布" extra={<span className="screen-top-note">共 {users.length} 位</span>}><div className="identity-distribution">{departments.map(item => { const percent = users.length ? Math.round(item.value / users.length * 100) : 0; return <div className="distribution-row" key={item.name}><div className="distribution-summary"><span><i style={{ background: item.color }}/>{item.name}</span><strong>{item.value} 人</strong><span>{percent}%</span></div><div className="distribution-track" role="meter" aria-label={item.name} aria-valuenow={item.value} aria-valuemin={0} aria-valuemax={users.length || 1}><span style={{ width: `${percent}%`, background: item.color }}/></div></div>; })}</div></Card>
      <Card size="small" className="dashboard-card" title="最近操作记录" extra={<Button type="link" size="small" className="card-link" onClick={() => nav(base + 'audit/operations')}>查看全部 <ArrowRightOutlined /></Button>} styles={{ body: { padding: '0 16px 10px' } }}>
        <div className="dashboard-audit-table"><Table<Log> size="small" rowKey="id" dataSource={operations} pagination={false} scroll={{ x: 700 }} locale={{ emptyText: '暂无操作记录' }} columns={[{ title: '时间', dataIndex: 'createdAt', width: 148, render: (value: string) => <time className="dashboard-audit-time" dateTime={value}>{dayjs(value).format('YYYY-MM-DD HH:mm')}</time> }, { title: '操作人', width: 128, ellipsis: true, render: (_, row) => <span className="dashboard-audit-actor" title={actorLabel(row)}>{actorLabel(row)}</span> }, { title: '操作类型', dataIndex: 'action', width: 160, ellipsis: true, render: (value: string) => <span className="dashboard-audit-action" title={auditActionLabel(value)}>{auditActionLabel(value)}</span> }, { title: '目标对象', ellipsis: true, render: (_, row) => <span className="dashboard-audit-target" title={auditTargetLabel(row, db)}>{auditTargetLabel(row, db)}</span> }]}/></div>
        <div className="dashboard-audit-list">{operations.map(row => <div className="dashboard-audit-item" key={row.id}>
          <div className="dashboard-audit-item-top"><strong title={auditActionLabel(row.action)}>{auditActionLabel(row.action)}</strong><time dateTime={row.createdAt}>{dayjs(row.createdAt).format('YYYY-MM-DD HH:mm')}</time></div>
          <div className="dashboard-audit-item-bottom"><span title={actorLabel(row)}>{actorLabel(row)}</span><span title={auditTargetLabel(row, db)}>{auditTargetLabel(row, db)}</span></div>
        </div>)}{!operations.length && <Empty description="暂无操作记录"/>}</div>
      </Card>
    </div>
    {statistics && <Card size="small" title="访问趋势明细" style={{ marginTop: 20 }} extra={<Segmented size="small" value={chart} onChange={v => setChart(String(v))} options={[{ label: '数据表', value: 'table' }, { label: '趋势图', value: 'chart' }]}/>}>
      {chart === 'chart' ? <TrendChart data={points}/> : <Table size="small" rowKey="date" dataSource={points} pagination={false} columns={[{ title: '日期', dataIndex: 'date' }, { title: '登录次数', dataIndex: 'count' }, { title: '独立用户数', dataIndex: 'people' }]} scroll={{ x: 'max-content' }}/>}</Card>}
  </>;
}

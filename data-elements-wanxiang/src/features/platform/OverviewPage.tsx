
import { Button, Empty, Progress, Skeleton } from 'antd';
import { ArrowRightOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { type Row } from '../../services/api';
import { overviewSummary, overviewDetails } from '../../services/overview';
import { useWorkspaceQuery } from '../../services/useWorkspaceQuery';
import { ModuleIcon } from '../../design/ModuleIcon';
import { Metric, Panel, CoverageRing, ModelThumbnail } from '../../design/Visuals';
import { engineClass, formatCount, jobProgress, recentRunPresentation, sourceDistribution } from '../../shared/presentation';
import { Page, ErrorNotice, Status, date, useAction } from './common';
import { refreshStudio } from '../../services/studioRefresh';
import { useStudio } from '../er/store';
export function OverviewPage() {
    const summary = useWorkspaceQuery('overview-summary', overviewSummary);
    const details = useWorkspaceQuery('overview-panels', overviewDetails);
    
    const nav = useNavigate();
    const { run } = useAction();
    const v = summary.data ?? {};
    const [sources, jobs, models] = details.data ?? [[], [], []];
    const distribution = sourceDistribution(sources);
    const total = sources.length;
    const recent = [...jobs].sort((a, b) => String(b.created_at ?? '').localeCompare(String(a.created_at ?? ''))).slice(0, 5);
    const recentModels = [...models].sort((a, b) => String(b.updated_at ?? b.updatedAt ?? '').localeCompare(String(a.updated_at ?? a.updatedAt ?? ''))).slice(0, 3);
    const openModel = (r: Row) => run(async () => { await refreshStudio(); await useStudio.getState().switchDiagram(r.id); }, '');
    const link = (label: string, path: string) => <Button type="link" size="small" onClick={() => nav(path)}>{label}<ArrowRightOutlined /></Button>;
    const busy = summary.loading && !summary.data;
    return <Page title="治理总览" description="连接多源数据，建立可信标准，让每一项治理结果清晰可见。" actions={<Button type="primary" icon={<ModuleIcon name="sources"/>} onClick={() => nav('/governance/metadata/sources')}>登记数据源</Button>}>
 <ErrorNotice error={summary.error ?? details.error} retry={() => { summary.refresh(); details.refresh(); }}/>
 <div className="wx-metric-grid" aria-busy={busy}>
 {busy ? Array.from({ length: 5 }, (_, i) => <div className="wx-metric wx-metric-skeleton" key={i}><Skeleton active paragraph={{ rows: 1 }}/></div>) : <>
 <Metric label="数据源数量" value={v.sources} note="已登记的数据来源" icon={<ModuleIcon name="sources"/>} onClick={() => nav('/governance/metadata/sources')}/>
 <Metric label="已采集实体" value={v.entities} note="表、视图及集合" tone="blue" icon={<ModuleIcon name="catalog"/>} onClick={() => nav('/governance/metadata/catalog')}/>
 <Metric label="数据模型" value={v.models} note="跨来源业务关系视图" tone="indigo" icon={<ModuleIcon name="models"/>} onClick={() => nav('/governance/metadata/models')}/>
 <Metric label="异常检查项" value={v.issues} note="实际质检发现的异常检查项" tone="rose" icon={<ModuleIcon name="workorders"/>} onClick={() => nav('/governance/quality/workorders')}/>
 <Metric label="数据元数量" value={v.standards} note="工作区已登记标准" tone="teal" icon={<ModuleIcon name="elements"/>} onClick={() => nav('/governance/standards/elements')}/>
 </>}
 </div>
 <div className="wx-dashboard-charts">
 <Panel title="数据源类型分布" description="当前工作区的数据结构来源" extra={link('查看详情', '/governance/metadata/sources')}>
 {total ? <div className="distribution-content"><div className="distribution-ring"><svg viewBox="0 0 120 120" role="img" aria-label={`共 ${total} 个来源`}>{distribution.map((d, i) => { const offset = distribution.slice(0, i).reduce((n, x) => n + x.percent, 0); return <circle key={d.engine} className={`source-${engineClass(d.engine)}`} cx="60" cy="60" r="47" pathLength="100" strokeDasharray={`${Math.max(0, d.percent - .9)} 100`} strokeDashoffset={-offset} transform="rotate(-90 60 60)"/>; })}</svg><div><strong>{total}</strong><span>数据源</span></div></div><div className="distribution-legend">{distribution.map(d => <div key={d.engine}><span><i className={`source-${engineClass(d.engine)}`}/>{d.label}</span><b>{d.count}</b><small>{d.percent.toFixed(1)}%</small></div>)}</div></div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={details.loading ? '正在读取数据源' : '登记数据源后查看类型分布'}/>}
 </Panel>
 <Panel title="标准落地进度" description="让业务字段与标准形成对应关系" extra={link('查看详情', '/governance/standards/landing')}><CoverageRing value={v.coverage?.coverage} mapped={v.coverage?.mappedFields} total={v.coverage?.totalFields}/></Panel>
 <Panel title="任务运行概况" description="最近 100 条质检与探查执行记录" extra={link('执行记录', '/governance/quality/reports')}><div className="task-distribution">{[['已完成', ['SUCCEEDED'], 'success'], ['执行中', ['RUNNING', 'CANCEL_REQUESTED'], 'info'], ['排队中', ['QUEUED'], 'purple'], ['需关注', ['FAILED', 'PARTIAL'], 'warning']].map(([label, states, tone]) => { const n = jobs.filter(j => (states as string[]).includes(j.status)).length; return <div key={String(label)} className={`task-tone-${tone}`}><span>{label}</span><Progress percent={jobs.length ? n / jobs.length * 100 : 0} showInfo={false} size="small"/><b>{details.data ? n : '—'}</b></div>; })}<p>按实际执行记录汇总，不包含尚未启动的计划。</p></div></Panel>
 </div>
 <div className="wx-dashboard-work">
 <Panel title="最近执行任务" extra={link('查看全部', '/governance/quality/reports')} className="recent-tasks"><div className="wx-dashboard-table"><table><thead><tr><th>任务名称</th><th>进度</th><th>状态</th><th>开始时间</th><th>操作</th></tr></thead><tbody>{recent.map(r => { const p = jobProgress(r.done_count, r.total_count); const task = recentRunPresentation(r); return <tr key={r.id}><td><b title={task.name}>{task.name}</b><small title={task.context}>{task.context}</small></td><td><div className="task-progress"><Progress percent={p ?? 0} showInfo={false} size="small"/><span>{p === null ? '—' : `${p}%`}</span></div></td><td><Status value={r.status}/></td><td className="muted">{date(r.created_at)}</td><td><Button type="link" onClick={() => nav(`/governance/quality/reports?runId=${encodeURIComponent(r.id)}`)}>详情</Button></td></tr>; })}</tbody></table>{!recent.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={details.loading ? '正在读取任务' : '暂无执行任务'}/>}</div></Panel>
 <Panel title="快捷操作" description="从连接到治理，一步进入任务"><div className="quick-actions">{[['登记数据源', '接入业务库', 'sources', <ModuleIcon name="sources"/>], ['发起采集', '整理表与字段', 'collection', <ModuleIcon name="collection"/>], ['打开 ER 模型', '查看跨库关联', 'models', <ModuleIcon name="models"/>], ['新建数据标准', '定义业务规范', 'standards', <ModuleIcon name="elements"/>]].map(([name, desc, key, icon]) => <button key={String(key)} onClick={() => nav(key === 'standards' ? '/governance/standards/elements' : `/governance/metadata/${key}`)}>{icon}<span><b>{name}</b><small>{desc}</small></span></button>)}</div><div className="workspace-safety"><SafetyCertificateOutlined /><p>元数据与治理记录读取共享后端；模型视图布局保存在当前浏览器。</p></div></Panel>
 </div>
 <div className="wx-dashboard-bottom"><Panel title="最近更新模型" description="接着上次的工作，继续完善业务关系" extra={link('查看全部', '/governance/metadata/models')}><div className="recent-models">{recentModels.map(r => <button className="wx-model-card" key={r.id} onClick={() => openModel(r)}><ModelThumbnail model={r}/><div><h3>{r.name}</h3><p><span>{r.domain ?? r.domain_name ?? '未分类'}</span><span>{(r.layout?.nodes ?? r.layout_json?.nodes ?? []).length} 个实体</span></p></div></button>)}{!recentModels.length && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="创建模型后在此继续设计"/>}</div></Panel>
 <Panel title="需要关注" description="从异常来源和质量问题开始处理" extra={link('去处理', '/governance/quality/workorders')}><div className="risk-list">{[['待处理质量问题', v.issues, '/governance/quality/workorders', 'danger'], ['最近连接测试失败的数据源', details.data ? sources.filter(r => r.status === 'ERROR').length : null, '/governance/metadata/sources', 'warning'], ['失败或部分完成的任务', details.data ? jobs.filter(j => ['FAILED', 'PARTIAL'].includes(j.status)).length : null, '/governance/quality/reports', 'warning'], ['尚待检查的数据源', details.data ? sources.filter(r => r.status === 'REGISTERED').length : null, '/governance/metadata/sources', 'info']].map(([label, n, path, tone]) => <button key={String(label)} onClick={() => nav(String(path))}><i className={`tone-${tone}`}/><span>{label}</span><b>{formatCount(n)}</b><ArrowRightOutlined /></button>)}</div></Panel></div>
 
 </Page>;
}

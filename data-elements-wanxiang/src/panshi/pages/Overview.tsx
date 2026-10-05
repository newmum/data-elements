import { Button, Empty } from 'antd';
import { ArrowRightOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { goCenter } from '../../shared/centers/navigation';
import { useResource } from '../services/context';
import { Hero, Metric, Panel, PTable, PsIcon, StateTag, NameCell, monumentArt, timeText } from '../components/ui';

export function Overview({ catalog = false }: { catalog?: boolean }) {
  const { data, refresh } = useResource();
  const state = data.state;
  const nav = useNavigate();
  const counts = [
    { name: '草稿', value: state.catalogs.filter(c => c.draft.state === 'DRAFT').length, color: '#a6c6f4', path: '/resource/catalog/entries' },
    { name: '待审核', value: state.catalogs.filter(c => c.draft.state === 'PENDING_REVIEW').length, color: '#438bf6', path: '/resource/catalog/reviews' },
    { name: '已审核', value: state.catalogs.filter(c => ['APPROVED', 'SUPERSEDED'].includes(c.draft.state)).length, color: '#08b9b1', path: '/resource/catalog/reviews' },
    { name: '已退回', value: state.catalogs.filter(c => c.draft.state === 'RETURNED').length, color: '#f5af70', path: '/resource/catalog/entries' },
  ];
  const total = state.catalogs.length;
  const cataloged = state.catalogs.filter(entry => entry.draft.resourceId && entry.draft.fieldIds.length > 0);
  let cursor = 0;
  const stops = counts.map(item => {
    const start = cursor;
    cursor += total ? item.value / total * 100 : 0;
    return `${item.color} ${start}% ${cursor}%`;
  }).join(',');
  const recent = state.logs.slice(0, 4);
  const recentCatalogs = [...cataloged].sort((a, b) => (b.updatedAt || b.createdAt || '').localeCompare(a.updatedAt || a.createdAt || '')).slice(0, 6);

  return <div className="ps-page">
    <Hero page={catalog ? 'catalog-overview' : 'overview'} actions={<Button type="primary" onClick={() => nav(catalog ? '/resource/catalog/entries/new' : '/resource/models?create=1')}>{catalog ? '编制资源目录' : '新建逻辑模型'}</Button>}/>
    <div className="ps-metrics">
      {catalog ? <>
        <Metric label="目录记录" value={total} note="含待完善的历史草稿" icon="catalog-entries" onClick={() => nav('/resource/catalog/entries')}/>
        <Metric label="草稿目录" value={counts[0].value} note="待补全业务信息和数据项" icon="catalog-entries" tone="blue" onClick={() => nav('/resource/catalog/entries')}/>
        <Metric label="待审核目录" value={counts[1].value} note="已提交的目录修订" icon="catalog-reviews" onClick={() => nav('/resource/catalog/reviews')}/>
        <Metric label="已审核目录" value={counts[2].value} note="审核通过的目录" icon="catalog-reviews" tone="blue" onClick={() => nav('/resource/catalog/reviews')}/>
      </> : <>
        <Metric label="分层数量" value={state.layers.length} note="企业级数仓分层架构" icon="layers" onClick={() => nav('/resource/planning/layers')}/>
        <Metric label="分库数量" value={state.databases.length} note="已规划与可配置的业务分库" icon="databases" tone="blue" onClick={() => nav('/resource/planning/databases')}/>
        <Metric label="已编目目录" value={cataloged.length} note="已关联来源并编制数据项" icon="catalog-entries" onClick={() => nav('/resource/catalog/entries')}/>
        <Metric label="待审核目录" value={counts[1].value} note="等待核对的目录修订" icon="catalog-reviews" tone="blue" onClick={() => nav('/resource/catalog/reviews')}/>
      </>}
    </div>
    {!catalog && <section className="ps-foundation-strip" aria-label="当前租户已有基础数据">
      <span className="ps-foundation-title">已有基础数据</span>
      <button onClick={() => goCenter('wanxiang', '/governance/metadata/sources')}>数据源 <b>{data.sources.length.toLocaleString('zh-CN')}</b></button>
      <button onClick={() => goCenter('wanxiang', '/governance/metadata/catalog')}>已采集技术表 <b>{(data.entityPagination?.total ?? data.entities.length).toLocaleString('zh-CN')}</b></button>
      <button onClick={() => nav('/resource/catalog/entries')}>待关联目录草稿 <b>{state.catalogs.filter(c => c.draft.state === 'DRAFT' && !c.draft.resourceId).length.toLocaleString('zh-CN')}</b></button>
      <small>上方统计为当前资源规划、编目和审核状态</small>
    </section>}
    <div className="ps-overview-triple">
      <Panel title="数仓分层结构" extra={<Button type="link" onClick={() => nav('/resource/planning/layers')}>查看详情 <ArrowRightOutlined/></Button>}>
        <div className="ps-layer-landscape"><img src={monumentArt} alt="数仓分层结构示意"/><div>{[...state.layers].sort((a, b) => a.order - b.order).map((layer, index) => <button key={layer.id} onClick={() => nav('/resource/planning/databases?layer=' + layer.id)}><i style={{ background: ['#00a9a0', '#3481ec', '#7469d6', '#d89947', '#44b8cd'][index % 5] }}/><b>{layer.name} <small>{layer.code}</small></b><span>{state.databases.filter(db => db.layerId === layer.id).length} 个分库</span></button>)}</div></div>
      </Panel>
      <Panel title="目录编目与审核状态" extra={<span className="ps-subtle">当前租户</span>}>
        <div className="ps-distribution"><div className="ps-donut" style={{ background: total ? `conic-gradient(${stops})` : 'var(--ps-muted)' }}><div><strong>{total}</strong><small>业务目录</small></div></div><div className="ps-legend">{counts.map(item => <button key={item.name} onClick={() => nav(item.path)}><i style={{ background: item.color }}/><span>{item.name}</span><b>{item.value}</b><small>{total ? Math.round(item.value / total * 100) : 0}%</small></button>)}</div></div>
      </Panel>
      <Panel title="近期任务" extra={<Button type="link" onClick={() => nav('/resource/models/logs')}>查看全部 <ArrowRightOutlined/></Button>}>
        <div className="ps-activity">{recent.length ? recent.map((log, index) => <button key={log.id} onClick={() => nav('/resource/models/logs')}><span className={`ps-task-icon tone-${index}`}><PsIcon name={['catalog-reviews', 'materialization', 'bindings', 'models'][index % 4]}/></span><div><b>{log.action}</b><small>{log.name}</small></div><small>{timeText(log.time).slice(5, 16)}</small></button>) : <Empty description="暂无操作记录"/>}</div>
      </Panel>
    </div>
    <div className="ps-quick-actions">{[
      { name: '配置分库数据源', note: '关联已有数据源，查看已采集表', key: 'bindings', path: '/resource/planning/bindings' },
      { name: '创建目录', note: '从技术快照编制业务资源目录', key: 'catalog-entries', path: '/resource/catalog/entries/new' },
      { name: '新建模型', note: '基于标准创建与复用业务模型', key: 'models', path: '/resource/models?create=1' },
    ].map(action => <button key={action.key} onClick={() => nav(action.path)}><span><PsIcon name={action.key}/></span><div><b>{action.name}</b><small>{action.note}</small></div><ArrowRightOutlined/></button>)}</div>
    <PTable title="最近编目的目录" rows={recentCatalogs} onRefresh={refresh} emptyTitle="暂无已编目目录" emptyDescription="目录草稿补全来源和数据项后，将显示在这里。" columns={[
      { title: '目录名称', key: 'name', width: 230, render: (_, entry) => <NameCell name={entry.draft.name} code={entry.draft.code}/> },
      { title: '提供部门', key: 'provider', render: (_, entry) => data.organizations.find(org => org.value === entry.draft.provider)?.label ?? entry.draft.provider },
      { title: '业务分类', key: 'cat', render: (_, entry) => state.classifications.find(item => item.id === entry.draft.classificationId)?.name ?? '未分类' },
      { title: '数据项', key: 'fields', render: (_, entry) => `${entry.draft.fieldIds.length} 项` },
      { title: '版本', key: 'ver', render: (_, entry) => `v${entry.draft.number}` },
      { title: '审核状态', key: 'state', render: (_, entry) => <StateTag state={entry.draft.state}/> },
      { title: '编目时间', key: 'updatedAt', render: (_, entry) => timeText(entry.updatedAt || entry.createdAt), sorter: (a, b) => (a.updatedAt || a.createdAt || '').localeCompare(b.updatedAt || b.createdAt || '') },
      { title: '操作', key: 'ops', fixed: 'right', width: 120, align: 'center', className: 'wx-action-column', render: (_, entry) => <Button type="link" onClick={() => nav('/resource/catalog/entries/' + entry.id)}>查看目录</Button> },
    ]}/>
  </div>;
}

import { Button, Empty, Tag, Tooltip } from 'antd';
import { useStudio } from '../store';
import { Icon } from './Icon';
import { SourceIcon } from '../../../design/Visuals';
import { displayDate } from '../core/model';
export function Capabilities() {
  const data = useStudio(s => s.data)!;
  return <main className="capabilities-page">
    <div className="page-heading"><div><div className="eyebrow">数据治理 / 元数据目录</div><h1>数据目录</h1><p>查看结构快照、来源分布与当前可用能力。</p></div><Button icon={<Icon name="layout" size={16} />} onClick={() => useStudio.getState().setPage('canvas')}>返回关系画布</Button></div>
    <div className="catalog-overview"><div><span>数据源</span><strong>{data.snapshot.sources.length}</strong></div><div><span>数据实体</span><strong>{data.snapshot.entities.length}</strong></div><div><span>字段</span><strong>{data.snapshot.entities.reduce((n, e) => n + e.fields.length, 0)}</strong></div><div><span>结构快照</span><strong className="catalog-date">{displayDate(data.snapshot.capturedAt)}</strong></div></div>
    <div className="section-heading"><h2 className="data-sources-title">来源目录</h2><Tooltip title="目录来自元数据快照，不表示已经建立在线连接。"><span className="catalog-caption"><Icon name="info" size={14} />以当前快照为准</span></Tooltip></div>
    <div className="source-cards">{data.snapshot.sources.map(source => {
      const entities = data.snapshot.entities.filter(e => e.sourceId === source.id);
      return <article className="source-info-card" key={source.id}><SourceIcon engine={source.engine} /><div><h3>{source.name}</h3><p>{source.engine.toUpperCase()}<span>·</span>{entities.length} 个实体<span>·</span>{entities.reduce((n, e) => n + e.fields.length, 0)} 个字段</p><small>{entities[0]?.catalog ?? '元数据目录'}{entities[0]?.schemaName ? ` / ${entities[0]?.schemaName}` : ''}</small></div><Tag bordered={false}>{source.capabilities.catalogConstraints ? '含约束信息' : '观测结构'}</Tag><Button type="text" icon={<Icon name="arrow" size={17}/>} aria-label={`查看${source.name}对象`} onClick={() => { useStudio.getState().setPage('canvas'); useStudio.getState().setLibraryOpen(true); requestAnimationFrame(()=>requestAnimationFrame(()=>window.dispatchEvent(new CustomEvent('wanxiang-scroll-source',{detail:source.id})))); }} /></article>;
    })}{!data.snapshot.sources.length && <Empty description="暂无元数据，请先登记数据源并采集结构。" />}</div>
    <section className="runtime-section"><div className="section-heading"><h2>工作区能力</h2></div><div className="runtime-row"><span><Icon name="spark" />关系发现</span><p>分析表结构、字段类型、键约束与租户作用域</p><Tag color="blue">可用</Tag></div><div className="runtime-row"><span><Icon name="save" />关系与布局保存</span><p>表字段读取共享目录，关系保存到共享台账；仅布局保存在浏览器</p><Tag color="blue">可用</Tag></div><div className="runtime-row"><span><Icon name="activity" />记录读取与数据验证</span><p>需通过授权服务读取记录；当前仅提供结构分析</p><Tag>尚未接入</Tag></div></section>
    <div className="catalog-storage-note"><Icon name="lock" size={16} /><p>画布布局按当前账号、租户和站点隔离。元数据与逻辑关系由共享后端管理，清理浏览器布局不会删除共享台账或修改源数据库。</p></div>
  </main>;
}

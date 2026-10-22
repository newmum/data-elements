import { useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Input, Progress, Select, Table } from 'antd';
import type { SyncTask } from '../core/model';
import { countTask, taskLabel } from '../services/mockSync';
import { SYNC_LABEL } from '../core/model';
import Icon from './Icon';
export default function TaskDrawer({ open, tasks, activeId, onActive, onClose, onRetry, onQuery, busy }: {
    open: boolean;
    tasks: SyncTask[];
    activeId?: string;
    onActive: (id?: string) => void;
    onClose: () => void;
    onRetry: (t: SyncTask) => void;
    onQuery: (t: SyncTask) => void;
    busy: boolean;
}) {
    const [search, setSearch] = useState(''), [filter, setFilter] = useState('all');
    const task = tasks.find(t => t.id === activeId), counts = task ? countTask(task) : undefined;
    return <Drawer title={<span className="dialog-title"><Icon name="history"/>{task ? '上报记录详情' : '上报记录'} <span className="small-count">{tasks.length}</span></span>} open={open} onClose={onClose} width={860} extra={task && <Button type="text" onClick={() => onActive(undefined)}>返回列表</Button>}>
  <Alert showIcon type="info" message="上报记录" description="记录提交时的数据版本、字段结构和逐条处理结果。"/>
  {task && counts ? <><div className="task-head"><div><span className="eyebrow">{task.parentId ? '失败重试任务' : '数据同步任务'}</span><h2>{task.schema.name}</h2><code>{task.id}</code></div><span className={`status-pill ${counts.failed ? 'warning' : counts.unknown ? 'warning' : counts.processing ? 'blue' : 'success'}`}><i />{taskLabel(task)}</span></div>
   <Progress percent={task.stage === 'sending' ? 35 : task.stage === 'waiting' ? 80 : 100} status={counts.processing ? 'active' : counts.failed || counts.unknown ? 'normal' : 'success'} format={() => task.stage === 'done' ? '已结束' : '处理中'}/>
   <div className="task-metrics">{[['提交', counts.total], ['成功', counts.success], ['失败', counts.failed], ['待确认', counts.unknown], ['处理中', counts.processing]].map(([label, v]) => <div key={label}><span>{label}</span><strong>{v}</strong></div>)}</div>
   <Descriptions size="small" column={2} items={[{ key: 'tableZh', label: '表中文名', children: task.schema.name }, { key: 'tableEn', label: '表英文名', children: task.schema.englishName }, { key: 'file', label: '来源文件', children: task.source.filename }, { key: 'sheet', label: '工作表', children: task.source.sheet }, { key: 'version', label: '版本', children: `数据 v${task.dataVersion} / 结构 v${task.schemaVersion}` }, { key: 'time', label: '提交时间', children: new Date(task.createdAt).toLocaleString('zh-CN') }, { key: 'target', label: '目标', children: task.target, span: 2 }, { key: 'pk', label: '主键', children: task.schema.fields.filter(f => f.pk).map(f => f.en).join(' + '), span: 2 }, { key: 'timestamp', label: '时间戳', children: task.schema.fields.find(f => f.id === task.schema.timestamp)?.zh }, { key: 'event', label: '业务发生时间', children: task.schema.fields.find(f => f.id === task.schema.eventTime)?.zh }, { key: 'dict', label: '字典快照', children: task.dictionaries.map(d => `${d.name} v${d.version}`).join('、') || '未关联', span: 2 }, { key: 'parent', label: '原任务', children: task.parentId || '首次提交', span: 2 }]}/>
    <div className="section-heading"><h3>逐条处理结果</h3><div>{counts.unknown > 0 && <Button icon={<Icon name="refresh" size={15}/>} disabled={busy} onClick={() => onQuery(task)}>查询回执</Button>}{counts.failed > 0 && <Button type="primary" disabled={busy} onClick={() => onRetry(task)}>仅重试失败项（{counts.failed}）</Button>}</div></div>
    <Table rowKey="rowId" size="small" dataSource={task.items} pagination={{ pageSize: 10, size: 'small' }} columns={[{ title: '来源行', dataIndex: 'sourceRow', width: 80 }, { title: '业务主键', render: (_, x) => task.schema.fields.filter(f => f.pk).map(f => String(x.values[f.en])).join(' + '), width: 190 }, { title: '处理状态', render: (_, x) => SYNC_LABEL[x.state], width: 145 }, { title: '结果说明', render: (_, x) => x.reason || ((x.state === 'sending' || x.state === 'waiting') ? '等待接收回执' : '同步成功') }]}/>
   <p className="inline-note">任务保留提交时的数据、字段、主键、时间和字典快照；重试不覆盖原任务历史结果。</p>
  </> : <><div className="preview-toolbar" style={{ padding: '20px 0' }}><Input prefix={<Icon name="search" size={15}/>} placeholder="搜索资源或文件名称" value={search} onChange={e => setSearch(e.target.value)}/><Select style={{ width: 160 }} value={filter} onChange={setFilter} options={['all', '全部成功', '部分成功', '全部失败', '结果待确认', '同步中', '等待回执'].map(x => ({ value: x, label: x === 'all' ? '全部状态' : x }))}/></div><Table rowKey="id" size="middle" pagination={{ pageSize: 8, size: 'small' }} dataSource={tasks.filter(t => (t.schema.name + t.schema.englishName + t.source.filename).includes(search) && (filter === 'all' || taskLabel(t) === filter))} locale={{ emptyText: '暂无同步记录。请先选择已校验的数据发起同步。' }} columns={[{ title: '任务 / 资源', render: (_, t) => <div><strong>{t.schema.name}</strong><small className="cell-subtitle">{t.id}</small></div>, width: 250 }, { title: '提交', render: (_, t) => t.items.length, width: 65 }, { title: '成功 / 失败 / 待确认', render: (_, t) => { const c = countTask(t); return `${c.success} / ${c.failed} / ${c.unknown}`; }, width: 160 }, { title: '状态', render: (_, t) => taskLabel(t), width: 110 }, { title: '操作', render: (_, t) => <Button type="link" onClick={() => onActive(t.id)}>查看</Button> }]}/></>}
 </Drawer>;
}

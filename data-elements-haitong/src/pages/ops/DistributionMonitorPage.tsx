import { Alert, Button, Descriptions, Drawer, Input, Progress, Tag } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { integrationPlatformService, type DistributionTask } from '../../api/integrationPlatformService';
import PageHero from '../../components/PageHero';
import MetricCard from '../../components/MetricCard';
import { DataTable, Panel, formatTime } from '../../components/Common';
import Icon from '../../design/Icon';

const field = (row: DistributionTask, ...keys: string[]) => {
  for (const key of keys) if (row[key] !== undefined && row[key] !== null) return String(row[key]);
  return '';
};
const statusLabel = (status: string) => ({ GENERATED: '已生成，待接入执行', RUNNING: '运行中', SUCCESS: '已完成', FAILED: '失败' } as Record<string, string>)[status.toUpperCase()] || status || '未知';

/** A distribution task is a delivery ledger entry; it is not an access-task NiFi run. */
export default function DistributionMonitorPage() {
  const [query, setQuery] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [detail, setDetail] = useState<DistributionTask>();
  const [detailError, setDetailError] = useState('');
  const [detailLoading, setDetailLoading] = useState(false);
  const tasks = useQuery({
    queryKey: ['platform', 'distribution-tasks', page, size, keyword],
    queryFn: () => integrationPlatformService.distributionTaskPage({ page, size, keyword }),
    refetchInterval: 30000,
  });
  const rows = tasks.data?.list || [];
  const search = () => { setPage(1); setKeyword(query.trim()); };
  const openDetail = async (row: DistributionTask) => {
    setDetail(row); setDetailError(''); setDetailLoading(true);
    try { setDetail(await integrationPlatformService.distributionTaskDetail(row.tid)); }
    catch (cause) { setDetailError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setDetailLoading(false); }
  };
  return <div className="ht-page">
    <PageHero kicker="任务运维" title="分发任务监控" description="查看当前租户已保存的分发任务、交付配置和任务记录状态。" kind="distribution" tags={['任务账本', '记录状态', '交付进度']} />
    <div className="metrics-grid">
      <MetricCard label="分发任务" value={tasks.data?.total ?? '—'} hint="当前租户任务账本" icon="tasks" />
      <MetricCard label="当前页待接入执行" value={rows.filter(row => field(row, 'executionMode', 'execution_mode') === 'PENDING_INTEGRATION').length} hint="尚未连接执行引擎" icon="clock" tone="amber" />
      <MetricCard label="当前页运行中" value={rows.filter(row => field(row, 'taskStatus', 'task_status').toUpperCase() === 'RUNNING').length} hint="分发任务记录状态" icon="wave" tone="cyan" />
      <MetricCard label="当前页失败" value={rows.filter(row => field(row, 'taskStatus', 'task_status').toUpperCase() === 'FAILED').length} hint="分发任务记录状态" icon="warning" tone="amber" />
    </div>
    <Alert type="info" showIcon message="分发任务以真实任务账本为准" description="当前已有分发任务表，但执行引擎尚无稳定的 NiFi 流程关联；待接入执行的任务不会显示为已运行或已交付。" />
    {tasks.isError && <Alert type="error" showIcon message="分发任务读取失败" description={tasks.error instanceof Error ? tasks.error.message : '请检查数据中台会话与接口'} action={<Button size="small" onClick={() => void tasks.refetch()}>重试</Button>} />}
    <Panel><div className="ht-toolbar"><Input prefix={<Icon name="search" size={17}/>} allowClear value={query} placeholder="搜索分发任务" onChange={event => setQuery(event.target.value)} onPressEnter={search} style={{ width: 300, maxWidth: '100%' }} /><Button onClick={search}>查询</Button><span className="toolbar-spacer"/><Button loading={tasks.isFetching} onClick={() => void tasks.refetch()}>刷新</Button></div>
      <DataTable<DistributionTask> rowKey="tid" dataSource={rows} loading={tasks.isLoading} pagination={{current:page,pageSize:size,total:tasks.data?.total||0,showSizeChanger:true,pageSizeOptions:[10,20,50],onChange:(next,nextSize)=>{setPage(next);setSize(nextSize);}}} columns={[
        { title: '分发任务', width: 250, render: (_, row) => <div className="entity-name"><button className="text-link strong" onClick={() => void openDetail(row)}>{field(row, 'taskName', 'task_name') || '未命名任务'}</button><small>{field(row, 'taskCode', 'task_code') || row.tid.slice(-8)}</small></div> },
        { title: '来源表', width: 180, render: (_, row) => field(row, 'sourceTableName', 'source_table_name') || '—' },
        { title: '资源目录', width: 180, render: (_, row) => field(row, 'catalogName', 'catalog_name') || '—' },
        { title: '任务状态', width: 155, render: (_, row) => { const status=field(row,'taskStatus','task_status'); return <Tag color={status==='FAILED'?'error':status==='SUCCESS'?'success':status==='RUNNING'?'processing':'default'}>{statusLabel(status)}</Tag>; } },
        { title: '执行进度', width: 150, render: (_, row) => <Progress percent={Math.max(0,Math.min(100,Number(field(row,'progress'))||0))} size="small" /> },
        { title: '创建时间', width: 170, render: (_, row) => { const time=field(row,'createdTime','created_time'); return time?formatTime(time):'—'; } },
        { title: '操作', fixed: 'right', width: 90, render: (_, row) => <Button type="link" onClick={() => void openDetail(row)}>详情</Button> },
      ]}/>
    </Panel>
    <Drawer title="分发任务详情" open={!!detail} onClose={() => setDetail(undefined)} width={680}>
      {detailError && <Alert type="error" showIcon message="详情读取失败" description={detailError} style={{marginBottom:16}} />}
      {detailLoading && <p className="helper">正在读取分发任务详情…</p>}
      {detail && !detailLoading && !detailError && <><Descriptions column={1} items={[
        { key:'name',label:'任务名称',children:field(detail,'taskName','task_name')||'—' },
        { key:'code',label:'任务编码',children:field(detail,'taskCode','task_code')||'—' },
        { key:'status',label:'任务状态',children:statusLabel(field(detail,'taskStatus','task_status')) },
        { key:'mode',label:'执行模式',children:field(detail,'executionMode','execution_mode')||'—' },
        { key:'engine',label:'执行引擎',children:field(detail,'executionEngine','execution_engine')||'未关联' },
        { key:'source',label:'来源表',children:field(detail,'sourceTableName','source_table_name')||'—' },
        { key:'catalog',label:'资源目录',children:field(detail,'catalogName','catalog_name')||'—' },
        { key:'message',label:'状态说明',children:field(detail,'statusMessage','status_message')||'—' },
        { key:'started',label:'开始时间',children:field(detail,'startedTime','started_time')||'—' },
        { key:'finished',label:'结束时间',children:field(detail,'finishedTime','finished_time')||'—' },
      ]}/><p className="helper">执行详情以分发任务账本为准。当前尚无任务与 NiFi 流程的稳定关联，因此不提供虚构的流程跳转。</p></>}
    </Drawer>
  </div>;
}

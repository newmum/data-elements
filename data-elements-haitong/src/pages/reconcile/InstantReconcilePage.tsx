import { Alert, Button, Descriptions, Drawer, Input, Select } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import PageHero from '../../components/PageHero';
import { DataTable, InsightStrip, Panel, StateTag, formatTime } from '../../components/Common';
import MetricCard from '../../components/MetricCard';
import Icon from '../../design/Icon';
import { reconciliationLive, num, str, type ReconcileRun } from '../../api/reconciliationLive';
import { integrationPlatformService } from '../../api/integrationPlatformService';

const displayStatus = (run: ReconcileRun) => {
  const status = str(run, 'status').toUpperCase();
  return status === 'CONSISTENT' ? 'MATCH' : status === 'INCONSISTENT' ? 'DIFFERENT' : status === 'FAILED' ? 'BLOCKED' : status;
};

export default function InstantReconcilePage() {
  const [records, setRecords] = useState<ReconcileRun[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [filter, setFilter] = useState('ALL');
  const [query, setQuery] = useState('');
  const [keyword, setKeyword] = useState('');
  const [detail, setDetail] = useState<ReconcileRun>();
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [loading, setLoading] = useState(false);
  const [summary, setSummary] = useState<Record<string, unknown>>();
  const [summaryError, setSummaryError] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await reconciliationLive.runs(page, size, filter === 'DIFF' ? 'INCONSISTENT' : '', keyword);
      setRecords(result.list);
      setTotal(result.total);
      setError('');
    } catch (cause) {
      setRecords([]);
      setTotal(0);
      setError(cause instanceof Error ? cause.message : String(cause));
    } finally { setLoading(false); }
  }, [page, size, filter, keyword]);

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    let active = true;
    void integrationPlatformService.reconciliationSummary()
      .then(result => { if (active) { setSummary(result); setSummaryError(''); } })
      .catch(cause => { if (active) setSummaryError(cause instanceof Error ? cause.message : String(cause)); });
    return () => { active = false; };
  }, []);

  const openDetail = async (run: ReconcileRun) => {
    setDetail(run);
    setDetailError('');
    try { setDetail(await reconciliationLive.runDetail(run.tid)); }
    catch (cause) { setDetailError(cause instanceof Error ? cause.message : String(cause)); }
  };
  const statuses = Array.isArray(summary?.runStatus) ? summary.runStatus as Record<string, unknown>[] : [];
  const statusCount = (name: string) => statuses.reduce((sum, row) => sum + (str(row, 'status') === name ? num(row, 'count') : 0), 0);
  const runTotal = statuses.reduce((sum, row) => sum + num(row, 'count'), 0) || total;
  const consistent = summary ? statusCount('CONSISTENT') : records.filter(run => displayStatus(run) === 'MATCH').length;
  const different = summary ? statusCount('INCONSISTENT') + statusCount('FAILED') : records.filter(run => ['DIFFERENT','BLOCKED'].includes(displayStatus(run))).length;
  const count = detail ? num(detail, 'source_count', 'sourceCount') : 0;
  const targetCount = detail ? num(detail, 'target_count', 'targetCount') : 0;

  return <div className="ht-page"><PageHero kicker="数据对账" title="即时对账" description="查看当前租户真实的来源与目标对账实例，追踪数量和字段差异。" kind="instant" tags={['对账实例','差异追踪','核销待接入']}/>
    <div className="metrics-grid"><MetricCard icon="instant" label="对账实例" value={runTotal} hint="来自后端对账执行记录"/><MetricCard icon="check" label="数量一致" value={consistent} hint="对账结果一致" tone="cyan"/><MetricCard icon="warning" label="差异 / 不完整" value={different} hint="建议查看差异明细" tone="amber"/><MetricCard icon="statements" label="已核销账单" value="—" hint="尚无核销证据来源"/></div>
    <InsightStrip items={[{label:'核销完成率',value:'—',icon:'check',hint:'待后端核销接口',tone:'cyan'},{label:'最新对账时间',value:records[0]?formatTime(str(records[0], 'created_time', 'createdTime')).slice(5,16):'—',icon:'clock',hint:'当前页最新对账实例'},{label:'差异实例',value:different,icon:'warning',hint:'查看对账结果和差异明细',tone:'amber'}]}/>
    {summaryError && <Alert type="warning" showIcon message={`对账概览读取失败：${summaryError}；概览卡片仅统计当前页实例。`} style={{marginBottom:12}}/>}
    {error && <Alert type="error" showIcon message={error} action={<Button size="small" onClick={()=>void load()}>重试</Button>}/>}
    <Panel><div className="ht-toolbar"><Input prefix={<Icon name="search" size={17}/>} value={query} onChange={event=>setQuery(event.target.value)} onPressEnter={()=>{setPage(1);setKeyword(query.trim());}} placeholder="搜索对账关联任务" style={{width:300,maxWidth:'100%'}}/><Button onClick={()=>{setPage(1);setKeyword(query.trim());}}>查询</Button><Select value={filter} onChange={value=>{setPage(1);setFilter(value);}} style={{width:160}} options={[{value:'ALL',label:'全部结果'},{value:'DIFF',label:'存在差异'}]}/></div>
      <DataTable<ReconcileRun> rowKey="tid" loading={loading} dataSource={records} pagination={{current:page,pageSize:size,total,showSizeChanger:true,pageSizeOptions:[10,20,50,100],showTotal:n=>`共 ${n} 条记录`}} onChange={pagination=>{setPage(pagination.current||1);setSize(pagination.pageSize||10);}} columns={[{title:'关联任务 / 对账实例',width:265,render:(_,run)=><div className="entity-name"><button className="text-link strong" onClick={()=>void openDetail(run)}>{str(run,'policy_name','policyName')||'对账任务'}</button><small>{run.tid.slice(-8)} · {formatTime(str(run,'created_time','createdTime'))}</small></div>},{title:'来源记录',width:96,render:(_,run)=>num(run,'source_count','sourceCount')},{title:'目标记录',width:96,render:(_,run)=>num(run,'target_count','targetCount')},{title:'比对状态',width:120,render:(_,run)=><StateTag state={displayStatus(run)}/>},{title:'操作',fixed:'right',width:110,render:(_,run)=><Button type="link" onClick={()=>void openDetail(run)}>实例详情</Button>}]}/></Panel>
    <Drawer width={840} title="对账实例 · 双边数据" open={!!detail} onClose={()=>setDetail(undefined)}>{detail&&<>{detailError&&<Alert type="error" showIcon message={detailError}/>}
      <div className="paired-bills"><section><span>来源</span><h2>{count}<small> 条读取记录</small></h2><p>{str(detail,'source_table_name','sourceTableName')||'来源表'}</p><b>预期交付：尚无业务证据来源</b></section><Icon name="arrow" size={30}/><section><span>目标</span><h2>{targetCount}<small> 条目标记录</small></h2><p>{str(detail,'target_table_name','targetTableName')||'目标表'}</p><StateTag state={displayStatus(detail)}/></section></div>
      <Descriptions column={1} items={[{key:'time',label:'生成时间',children:formatTime(str(detail,'created_time','createdTime'))},{key:'version',label:'对账实例',children:detail.tid},{key:'count',label:'数量差额',children:count-targetCount}]} />
      <Alert type="info" showIcon message="核销不修改目标数据，也不删除原始差异。" description="当前后端提供对账实例与脱敏差异明细，尚未提供即时账单核销及复核证据接口，因此此处暂不允许核销。"/>
      <div className="detail-actions"><Button disabled>查看运行日志</Button><Button disabled>调整任务后重新运行</Button></div>
      <p className="helper">核销说明与确认核销需要后端账单、证据和核销接口后才能启用。</p>
    </>}</Drawer>
  </div>;
}

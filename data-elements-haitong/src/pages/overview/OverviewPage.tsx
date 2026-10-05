import { Alert, Button, Progress, Tag } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { integrationPlatformService } from '../../api/integrationPlatformService';
import { integrationNodesApi } from '../../api/integrationNodes';
import PageHero from '../../components/PageHero';
import MetricCard from '../../components/MetricCard';
import { DataTable, Panel, formatTime } from '../../components/Common';
import Icon from '../../design/Icon';

type RuntimeRow = Record<string, unknown>;
type ReconciliationSummary = Record<string, unknown>;

const text = (value: unknown) => value == null ? '' : String(value).trim();
const count = (value: unknown): number | null => {
 const number = Number(value);
 return value == null || value === '' || !Number.isFinite(number) ? null : number;
};
const timestamp = (value: unknown) => {
 if (value == null || value === '') return '—';
 const date = typeof value === 'number' ? new Date(value) : new Date(String(value));
 return Number.isFinite(date.getTime()) ? formatTime(date.toISOString()) : '—';
};
const localDateTime = (date: Date) => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')} ${String(date.getHours()).padStart(2,'0')}:${String(date.getMinutes()).padStart(2,'0')}:${String(date.getSeconds()).padStart(2,'0')}`;
const runtimeStatus = (row: RuntimeRow) => {
 const status = text(row.currentStatus).toUpperCase();
 if (status === 'RUNNING') return <Tag color="processing">运行中</Tag>;
 if (status === 'RUN_ERROR' || status === 'DEPLOY_FAILED') return <Tag color="error">运行异常</Tag>;
 if (status === 'DEPLOYING') return <Tag color="processing">部署中</Tag>;
 return <Tag>{({ DRAFT:'草稿', SAVED:'已保存', STOPPED:'已停止', STOPPING:'停止中' } as Record<string,string>)[status] || status || '未知'}</Tag>;
};

/** The overview reports only counters supported by shared platform/NiFi APIs. */
export default function OverviewPage() {
 const navigate = useNavigate();
 const tasks = useQuery({queryKey:['platform','overview','tasks'],queryFn:()=>integrationPlatformService.taskPage({page:1,size:1}),refetchInterval:30000,retry:0});
 const runtime = useQuery({queryKey:['platform','overview','runtime-summary'],queryFn:integrationPlatformService.runtimeOverview,refetchInterval:query=>query.state.data?.legacy?120000:30000,retry:0});
 const reconciliation = useQuery({queryKey:['platform','overview','reconciliation'],queryFn:integrationPlatformService.reconciliationSummary,refetchInterval:60000,retry:0});
 const trend = useQuery({queryKey:['platform','overview','monitor-trend'],queryFn:()=>{const end=new Date();const begin=new Date(end.getTime()-24*60*60*1000);return integrationPlatformService.monitorTrend(localDateTime(begin),localDateTime(end));},refetchInterval:60000,retry:0});
 const nodesQuery = useQuery({queryKey:['platform','nifi-nodes'],queryFn:integrationNodesApi.accessOptions,refetchInterval:60000,retry:0});
 const taskData = tasks.isError ? undefined : tasks.data;
 const runtimeData = runtime.isError ? undefined : runtime.data;
 const flows = runtimeData?.flows || [];
 const nodesData = nodesQuery.isError ? undefined : nodesQuery.data;
 const nodes = nodesData?.nodes || [];
 const running = runtimeData?.running ?? '—';
 const failed = runtimeData?.failed ?? '—';
 const summary = (reconciliation.isError ? undefined : reconciliation.data) as ReconciliationSummary | undefined;
 const statuses = Array.isArray(summary?.runStatus) ? summary.runStatus as Array<Record<string,unknown>> : [];
 const runTotal = statuses.reduce((sum,row)=>sum+(count(row.count)||0),0);
 const consistent = statuses.filter(row=>text(row.status).toUpperCase()==='CONSISTENT').reduce((sum,row)=>sum+(count(row.count)||0),0);
 const nonConsistent = statuses.filter(row=>['INCONSISTENT','FAILED'].includes(text(row.status).toUpperCase())).reduce((sum,row)=>sum+(count(row.count)||0),0);
 const rate = summary && runTotal > 0 ? Math.round(consistent/runTotal*100) : null;
 const trendPoints = Array.isArray(trend.data?.points) ? trend.data.points.slice(-12) : [];
 const trendMax = Math.max(1,...trendPoints.map(point=>Math.max(Number(point.flowFilesIn)||0,Number(point.flowFilesOut)||0)));
 const errorGroups = new Map<string,string[]>();
 const addError = (label: string, cause: unknown) => {
  const message = cause instanceof Error ? cause.message : '接口读取失败';
  errorGroups.set(message, [...(errorGroups.get(message) || []), label]);
 };
 if (tasks.isError) addError('集成任务',tasks.error);
 if (runtime.isError) addError('流程状态',runtime.error);
 if (reconciliation.isError) addError('双边对账',reconciliation.error);
 if (trend.isError) addError('监控趋势',trend.error);
 if (nodesQuery.isError) addError('运行节点',nodesQuery.error);
 const errorDescription = [...errorGroups].map(([message,labels])=>`${labels.join('、')}：${message}`).join('；');
 const openFlow = (row: RuntimeRow) => {
  const id = text(row.id);
  if (id) navigate(`/development/canvas?pipelineId=${encodeURIComponent(id)}`,{state:{returnTo:'/overview'}});
 };
 return <div className="ht-page">
  <PageHero kicker="集成工作台" title="数据集成总览" description="汇聚任务开发、稳定运行与双边对账视图，让集成链路、处理规模和异常位置一屏看清。" kind="overview" tags={['链路全景','运行态势','对账闭环']} primaryAction={<Button type="primary" icon={<Icon name="plus" size={17}/>} onClick={()=>navigate('/development/tasks')}>创建集成任务</Button>}/>
  {errorDescription&&<Alert type="error" showIcon message="部分概览数据读取失败" description={errorDescription} action={<Button size="small" onClick={()=>{if(tasks.isError)void tasks.refetch();if(runtime.isError)void runtime.refetch();if(reconciliation.isError)void reconciliation.refetch();if(trend.isError)void trend.refetch();if(nodesQuery.isError)void nodesQuery.refetch();}}>重试</Button>}/>}
  <div className="metrics-grid"><MetricCard icon="tasks" label="集成任务" value={taskData?.total??'—'} hint="当前租户的接入任务" onClick={()=>navigate('/development/tasks')}/><MetricCard icon="wave" label="运行中的流程" value={running} hint="最近保存的流程运行状态" onClick={()=>navigate('/ops/ingress')} tone="cyan"/><MetricCard icon="warning" label="异常流程" value={failed} hint="已记录的运行或部署失败" onClick={()=>navigate('/ops/ingress')} tone="amber"/><MetricCard icon="database" label="今日确认写入" value="—" hint="需按日成功写入统计接口" onClick={()=>navigate('/ops/distribution')}/></div>
  <div className="overview-main-grid"><Panel title="数据流转趋势" extra={<span className="helper">近 24 小时监控快照 · 展示最近 12 个时段</span>}><div className="chart-legend"><span><i/>任务流入峰值合计</span><span><i className="amber"/>任务流出峰值合计</span></div><div className="bar-chart" style={trendPoints.length?undefined:{alignItems:'center',justifyContent:'center'}}>{trendPoints.length?trendPoints.map(point=><div className="bar-column" key={point.bucketTime} title={`${point.bucketTime} · 任务流入峰值合计 ${point.flowFilesIn} · 任务流出峰值合计 ${point.flowFilesOut} · 样本 ${point.sampleCount}`}><div className="bar-track" style={{flexDirection:'row',alignItems:'flex-end',gap:2}}><div className="bar-value" style={{width:14,height:`${Math.max(0,(Number(point.flowFilesIn)||0)/trendMax*100)}%`}}/><div className="bar-rejected" style={{width:14,height:`${Math.max(0,(Number(point.flowFilesOut)||0)/trendMax*100)}%`}}/></div><small>{String(point.bucketTime).slice(11,16)}</small></div>):<span style={{background:'var(--surface)',color:'var(--secondary)',padding:'8px 16px',borderRadius:6}}>{trend.isLoading?'正在读取小时级监控快照…':trend.isError?'监控趋势读取失败':'所选时间段暂无监控快照'}</span>}</div><p className="helper" style={{margin:'12px 0 0'}}>每个任务取该小时的快照峰值后相加；同一任务的重复采样不会累加，也不代表目标库提交成功。</p></Panel>
   <Panel title="双边对账概况" extra={<button className="text-link" onClick={()=>navigate('/reconcile/instant')}>查看对账 <Icon name="arrow" size={15}/></button>}><div className="reconcile-overview"><Progress type="circle" percent={rate??0} size={126} strokeWidth={8} strokeColor="var(--primary)" format={()=> <div className="circle-label"><b>{rate==null?'—':`${rate}%`}</b><small>近 30 天一致率</small></div>}/><div className="bill-summary"><div><span>对账策略</span><b>{summary ? count(summary.policyTotal)??'—':'—'}</b></div><div><span>近 30 天差异 / 失败</span><b>{summary?nonConsistent:'—'}</b></div><div><span>未处理差异</span><b>{summary ? count(summary.openDiff)??'—':'—'}</b></div></div></div><div className="insight-note"><Icon name="instant" size={19}/><span>一致率来自真实对账执行，未处理差异需在对账页面核查。</span></div></Panel></div>
  <div className="overview-bottom-grid"><Panel className="overview-flow-panel" title="流程状态概况" extra={<Button type="link" onClick={()=>navigate('/ops/ingress')}>全部流程 <Icon name="arrow" size={16}/></Button>}><DataTable<RuntimeRow> rowKey={row=>text(row.id)} pagination={false} loading={runtime.isLoading} dataSource={flows} locale={{emptyText:'当前租户暂无流程记录'}} columns={[{title:'序号',width:56,render:(_,__,index)=><span className={'overview-flow-index'}>{index+1}</span>},{title:'流程名称',width:230,render:(_,row)=><div className="entity-name"><button className="text-link strong" onClick={()=>openFlow(row)}>{text(row.taskName)||'未命名流程'}</button><small>{text(row.id).slice(-8)||'—'}</small></div>},{title:'保存状态',width:106,render:(_,row)=>runtimeStatus(row)},{title:'最近部署',width:152,render:(_,row)=>timestamp(row.latestRunTime).slice(5,16)},{title:'操作',width:70,render:(_,row)=><Button type="link" onClick={()=>openFlow(row)}>详情</Button>}]}/></Panel>
  <Panel title="运行集群" extra={<Tag>{nodesData?`${nodes.length} 个已启用节点`:'—'}</Tag>}><div className="cluster-overview-list">{nodes.map(node=><button key={node.tid} onClick={()=>navigate('/ops/clusters')}><span className="cluster-mini-icon"><Icon name="clusters" size={22}/></span><div><b>{node.nodeName}</b><small>{node.networkName||node.networkCode||'未指定网络'}{node.isDefault?' · 默认节点':''}</small></div><Tag color="success">已启用</Tag></button>)}{!nodes.length&&<div className="helper" style={{minHeight:80,display:'flex',alignItems:'center'}}>{nodesQuery.isLoading?'正在读取节点…':nodesQuery.isError?'节点读取失败':'当前租户暂无已启用节点'}</div>}</div><div className="quick-links"><button onClick={()=>navigate('/development/tasks')}><Icon name="batch"/>集成任务 <Icon name="arrow" size={16}/></button><button onClick={()=>navigate('/reconcile/inventory')}><Icon name="inventory"/>盘点对账 <Icon name="arrow" size={16}/></button></div></Panel></div>
 </div>;
}

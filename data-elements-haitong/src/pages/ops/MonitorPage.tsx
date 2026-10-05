import { Alert, Button, DatePicker, Drawer, Input, Select, Space, Tag } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { integrationPlatformService, type AccessMonitorSnapshot, type AccessTask } from '../../api/integrationPlatformService';
import PageHero from '../../components/PageHero';
import MetricCard from '../../components/MetricCard';
import { DataTable, InsightStrip, Panel, formatTime } from '../../components/Common';
import Icon from '../../design/Icon';

type RuntimeRow=Record<string,unknown>;
const text=(value:unknown)=>value==null?'':String(value).trim();
const count=(value:unknown)=>Number(value)||0;
const localTime=(value:unknown)=>value?formatTime(typeof value==='number'?new Date(value).toISOString():String(value)):'—';
const taskState=(task:AccessTask,runtime?:RuntimeRow)=>{
 const status=text(runtime?.currentStatus).toUpperCase();
 if(status==='RUNNING'||Number(task.taskStatus)===1)return <Tag color="processing">运行中</Tag>;
 if(status.includes('ERROR')||status.includes('FAILED')||Number(task.taskStatus)===2)return <Tag color="error">运行异常</Tag>;
 return <Tag>未启用</Tag>;
};

/** Runtime counters come from NiFi; task metadata comes from the existing Magic API. */
export default function MonitorPage({kind}:{kind:'ingress'|'distribution'|'cross'}){
 const navigate=useNavigate();
 const [query,setQuery]=useState(''),[keyword,setKeyword]=useState(''),[filter,setFilter]=useState('all'),[page,setPage]=useState(1),[size,setSize]=useState(20);
 const [selectedTask,setSelectedTask]=useState<AccessTask>(),[snapPage,setSnapPage]=useState(1),[snapRange,setSnapRange]=useState<{beginTime?:string;endTime?:string}>({});
 const tasksQuery=useQuery({queryKey:['platform','monitor-tasks',page,size,filter,keyword],queryFn:()=>integrationPlatformService.taskPage({page,size,status:filter,keyword}),refetchInterval:30000});
 const runtimeQuery=useQuery({queryKey:['platform','monitor-runtime'],queryFn:integrationPlatformService.runtimeList,refetchInterval:30000});
 const snapQuery=useQuery({queryKey:['platform','monitor-snaps',selectedTask?.tid,snapPage,snapRange],queryFn:()=>integrationPlatformService.monitorSnapPage({taskId:selectedTask!.tid,page:snapPage,size:10,...snapRange}),enabled:!!selectedTask?.tid});
 const runtime=new Map((runtimeQuery.data||[]).map(row=>[text(row.id),row]));
 const rows=tasksQuery.data?.list||[];
 const running=rows.filter(row=>Number(row.taskStatus)===1).length,failed=rows.filter(row=>Number(row.taskStatus)===2).length;
 const titles={ingress:'接入任务监控',distribution:'分发任务监控',cross:'跨网传输'};
 const openCanvas=(task:AccessTask)=>{
  const params=new URLSearchParams();
  if(text(task.pipelineId))params.set('pipelineId',text(task.pipelineId));
  if(task.recordSource!=='pipeline'&&text(task.tid))params.set('accessTaskId',text(task.tid));
  if(params.size)navigate(`/development/canvas?${params.toString()}`,{state:{returnTo:`/ops/${kind==='cross'?'cross-network':kind}`}});
 };
 const search=()=>{setPage(1);setKeyword(query.trim());};
 const refresh=()=>{void tasksQuery.refetch();void runtimeQuery.refetch();};
 return <div className="ht-page"><PageHero kicker="任务运维" title={titles[kind]} description={kind==='cross'?'从发送、签收、回执到目标确认，逐阶段追踪跨网数据流转状态，帮助快速定位链路断点与回执缺失。':'围绕运行阶段、表级处理量、更新时间和异常日志，快速定位未完成、延迟或失败的数据链路。'} kind={kind} tags={['运行证据','表级数据量','日志追踪']}/>
  <div className="metrics-grid"><MetricCard label={kind==='ingress'?'接入任务总数':'接入参考任务'} value={tasksQuery.data?.total??'—'} hint={kind==='ingress'?'当前租户接入任务总数':'当前租户接入任务，非本类任务统计'} icon="tasks"/><MetricCard label={kind==='ingress'?'当前页运行中':'参考任务运行中'} value={running} hint="以平台任务状态为准" icon="wave" tone="cyan"/><MetricCard label={kind==='ingress'?'当前页异常':'参考任务异常'} value={failed} hint="可打开画布查看运行错误" icon="warning" tone="amber"/><MetricCard label={kind==='ingress'?'当前页流出':'参考任务流出'} value={rows.reduce((sum,task)=>sum+count(runtime.get(text(task.pipelineId))?.outputCount),0)} hint="NiFi 最近一次运行快照" icon="database"/></div>
  <InsightStrip items={[{label:'最新运行时间',value:localTime(rows.find(row=>row.lastRunning)?.lastRunning).slice(5,16),icon:'clock',hint:'当前页任务记录'},{label:'当前页排队流文件',value:rows.reduce((sum,task)=>sum+count(runtime.get(text(task.pipelineId))?.queuedCount),0),icon:'instant',hint:'NiFi 快照，非历史累计',tone:'cyan'},{label:'当前页状态异常',value:failed,icon:'warning',hint:'建议优先处理运行异常',tone:'amber'}]}/>
  {kind!=='ingress'&&<Alert type="info" showIcon style={{marginBottom:16}} message={kind==='cross'?'跨网传输接口尚未接入：现有接入任务没有目标网络与签收回执标识。下方仅供查看接入任务运行参考，不能作为跨网传输统计。':'分发监控接口尚未接入：平台另有数据分发任务表，但现有接入任务接口不返回其执行记录。下方仅供查看接入任务运行参考，不能作为分发任务统计。'}/>}
  {(tasksQuery.isError||runtimeQuery.isError)&&<Alert type="error" showIcon style={{marginBottom:16}} message="运行监控读取失败" description={tasksQuery.error instanceof Error?tasksQuery.error.message:runtimeQuery.error instanceof Error?runtimeQuery.error.message:'请检查数据中台会话与接口'} action={<Button size="small" onClick={refresh}>重试</Button>}/>}
  <Panel><div className="ht-toolbar"><Input prefix={<Icon name="search" size={17}/>} allowClear value={query} placeholder="搜索运行任务名称" onChange={event=>setQuery(event.target.value)} onPressEnter={search} style={{width:300,maxWidth:'100%'}}/><Select value={filter} onChange={value=>{setPage(1);setFilter(value);}} style={{width:155}} options={[{value:'all',label:'全部运行状态'},{value:'1',label:'运行中'},{value:'0',label:'未启用'},{value:'2',label:'运行异常'}]}/><Button onClick={search}>查询</Button><span className="toolbar-spacer"/><Button onClick={refresh} loading={tasksQuery.isFetching||runtimeQuery.isFetching}>刷新</Button></div>
   <DataTable<AccessTask> rowKey="tid" dataSource={rows} loading={tasksQuery.isLoading||runtimeQuery.isLoading} pagination={{current:page,pageSize:size,total:tasksQuery.data?.total??0,showSizeChanger:true,pageSizeOptions:[10,20,50],onChange:(next,nextSize)=>{setPage(next);setSize(nextSize);}}} columns={[
    {title:'运行任务',width:252,render:(_,task)=><div className="entity-name"><button className="text-link strong" onClick={()=>openCanvas(task)}>{text(task.taskName)||'未命名任务'}</button><small>{task.recordSource==='pipeline'?'NiFi 流程':'接入任务'} · {task.tid.slice(-8)}</small></div>},
    {title:'来源 / 目标',width:200,render:(_,task)=>`${text(task.sourceDbName||task.sourceTableName)||'待配置'} → ${text(task.targetDbName||task.targetTableName)||'待配置'}`},
    {title:'运行状态',width:105,render:(_,task)=>taskState(task,runtime.get(text(task.pipelineId)))},
    {title:'流入 / 流出',width:110,render:(_,task)=>{const item=runtime.get(text(task.pipelineId));return item?`${count(item.inputCount)} / ${count(item.outputCount)}`:'—';}},
    {title:'排队',width:80,render:(_,task)=>{const item=runtime.get(text(task.pipelineId));return item?count(item.queuedCount):'—';}},
    {title:'最近运行',width:170,render:(_,task)=>localTime(task.lastRunning)},
    {title:'操作',fixed:'right',width:174,render:(_,task)=><Space size={0}><Button type="link" onClick={()=>{setSelectedTask(task);setSnapPage(1);setSnapRange({});}}>历史快照</Button><Button type="link" onClick={()=>openCanvas(task)}>查看流程</Button></Space>},
   ]}/>
  </Panel>
  <Drawer title={selectedTask?`${selectedTask.taskName||'接入任务'} · 历史监控快照`:'历史监控快照'} open={!!selectedTask} onClose={()=>setSelectedTask(undefined)} width={980}>
   <div className="ht-toolbar"><DatePicker.RangePicker onChange={(_,values)=>{setSnapPage(1);setSnapRange({beginTime:values[0]?`${values[0]} 00:00:00`:'',endTime:values[1]?`${values[1]} 23:59:59`:''});}}/><span className="toolbar-spacer"/><Button onClick={()=>void snapQuery.refetch()} loading={snapQuery.isFetching}>刷新</Button></div>
   <p className="helper">来自平台持久化监控快照，可按监控时间查看积压、流入流出和异常；每条记录属于当前选中的接入任务。</p>
   {snapQuery.isError&&<Alert type="error" showIcon message="历史快照读取失败" description={snapQuery.error instanceof Error?snapQuery.error.message:'请检查任务监控接口'} style={{marginBottom:14}}/>}
   <DataTable<AccessMonitorSnapshot> rowKey="tid" dataSource={snapQuery.data?.list||[]} loading={snapQuery.isLoading||snapQuery.isFetching} pagination={{current:snapPage,pageSize:10,total:snapQuery.data?.total||0,onChange:setSnapPage}} columns={[
    {title:'监控时间',width:165,render:(_,snap)=>localTime(snap.monitorTime)},
    {title:'状态',width:105,render:(_,snap)=><Tag color={text(snap.monitorStatus).toUpperCase().includes('ERROR')?'error':'default'}>{text(snap.monitorStatus)||'—'}</Tag>},
    {title:'延迟等级',width:90,dataIndex:'delayLevel',render:value=>text(value)||'—'},
    {title:'排队',width:84,render:(_,snap)=>count(snap.queuedCount)},
    {title:'流入 / 流出',width:110,render:(_,snap)=>`${count(snap.flowFilesIn)} / ${count(snap.flowFilesOut)}`},
    {title:'读 / 写字节',width:126,render:(_,snap)=>`${count(snap.bytesIn)} / ${count(snap.bytesOut)}`},
    {title:'运行线程',width:85,render:(_,snap)=>count(snap.activeThreadCount)},
    {title:'监控信息',render:(_,snap)=><span title={text(snap.monitorMsg)}>{text(snap.monitorMsg)||'—'}</span>},
   ]}/>
  </Drawer>
 </div>;
}

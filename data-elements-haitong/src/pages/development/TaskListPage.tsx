import { Alert, App, Button, Input, Modal, Select, Tag } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { integrationPlatformService, type AccessTable, type AccessTask } from '../../api/integrationPlatformService';
import PageHero from '../../components/PageHero';
import { DataTable, FlowMini, InsightStrip, Panel, formatTime } from '../../components/Common';
import Icon from '../../design/Icon';
import { preloadCanvasReadiness, preloadCanvasWhenIdle } from '../../app/preloadCanvas';
import { useSessionRevision, useTokenReady } from '../../nifi/api/bridgeSession';

const text=(value:unknown)=>value==null?'':String(value).trim();
const statusTag=(value:unknown)=>Number(value)===1?<Tag color="processing">运行中</Tag>:Number(value)===2?<Tag color="error">运行异常</Tag>:<Tag>未启用</Tag>;

/** The existing HaiTong table layout displays authoritative tenant-scoped access tasks. */
export default function TaskListPage({multi=false}:{multi?:boolean}){
 const navigate=useNavigate(),{message}=App.useApp(),queryClient=useQueryClient();
 const tokenReady=useTokenReady(),sessionRevision=useSessionRevision();
 const [query,setQuery]=useState(''),[keyword,setKeyword]=useState(''),[filter,setFilter]=useState('all'),[page,setPage]=useState(1),[size,setSize]=useState(20);
 const [createOpen,setCreateOpen]=useState(false),[sourceQuery,setSourceQuery]=useState(''),[sourceKeyword,setSourceKeyword]=useState(''),[sourcePage,setSourcePage]=useState(1),[creatingId,setCreatingId]=useState<string>();
 const tasksQuery=useQuery({queryKey:['platform','access-tasks',page,size,filter,keyword],queryFn:()=>integrationPlatformService.taskPage({page,size,status:filter,keyword})});
 const sourcesQuery=useQuery({queryKey:['platform','source-tables',sourcePage,sourceKeyword],queryFn:()=>integrationPlatformService.sourceTablePage({page:sourcePage,size:10,keyword:sourceKeyword}),enabled:createOpen});
 const tasks=multi?(tasksQuery.data?.list||[]).filter(task=>(task.syncTables?.length||0)>1):tasksQuery.data?.list||[];
 const warmCanvas=()=>{if(tokenReady)preloadCanvasReadiness(queryClient,sessionRevision);};
 useEffect(() => {
  if (tasksQuery.isLoading || tasks.length === 0 || !tokenReady) return;
  return preloadCanvasWhenIdle(() => preloadCanvasReadiness(queryClient,sessionRevision));
 }, [tasksQuery.isLoading, tasks.length, tokenReady, queryClient, sessionRevision]);
 const openCanvas=(task:AccessTask)=>{
  const params=new URLSearchParams();
  if(text(task.pipelineId))params.set('pipelineId',text(task.pipelineId));
  if(task.recordSource!=='pipeline'&&text(task.tid))params.set('accessTaskId',text(task.tid));
  if(!params.size){message.error('当前任务缺少可打开的流程标识');return;}
  navigate(`/development/canvas?${params.toString()}`,{state:{returnTo:multi?'/development/multi-table':'/development/tasks'}});
 };
 const create=async(source:AccessTable)=>{
  const id=text(source.sourceTableId||source.tableId||source.tid);
  if(!id){message.error('当前来源表缺少标识，无法创建任务');return;}
  setCreatingId(id);
  try{
   const result=await integrationPlatformService.ensureTask(source);
   if(result.repairRequired)throw new Error(`已有任务需要修复：${(result.repairReasons??[]).join('；')||'请在任务管理中检查画布与字段映射'}`);
   const rawTask=result.task||result as AccessTask;
   const taskId=text(rawTask.tid||result.taskId),pipelineId=text(rawTask.pipelineId||result.pipelineId);
   if(!taskId&&!pipelineId)throw new Error('任务接口没有返回任务或流程标识');
   let bindingWarning='';
   if(text(source.datasourceId)&&pipelineId){
    try{await integrationPlatformService.bindApiPullTask(text(source.datasourceId),id,pipelineId);}
    catch{bindingWarning='任务已创建，但 API 拉取规则绑定失败；可先检查画布配置';}
   }
   setCreateOpen(false);
   await queryClient.invalidateQueries({queryKey:['platform','access-tasks']});
   if(bindingWarning)message.warning(bindingWarning);else message.success(result.created===false?'已找到现有接入任务，正在打开 NiFi 画布':'接入任务已创建，正在打开 NiFi 画布');
   openCanvas({tid:taskId,pipelineId});
  }catch(cause){message.error(cause instanceof Error?cause.message:'接入任务创建失败');}
  finally{setCreatingId(undefined);}
 };
 const search=()=>{setPage(1);setKeyword(query.trim());};
 return <div className="ht-page"><PageHero kicker="任务开发" title={multi?'多表同步任务':'集成任务'} description={multi?'查看已有接入任务的多表编组与成员状态；组级执行尚未接入。':'管理来源表到目标表的接入任务，查看 NiFi 关联、调度与运行状态。'} kind={multi?'multi':'tasks'} tags={multi?['已有任务编组','成员状态','组级执行待接入']:['真实接入任务','NiFi 编排','运行状态']} primaryAction={<Button type="primary" icon={<Icon name="plus" size={17}/>} disabled={multi} title={multi?'平台尚无多表任务创建接口，单表任务请到集成任务页创建':undefined} onClick={()=>{setSourcePage(1);setCreateOpen(true);}}>{multi?'新建多表任务':'新建集成任务'}</Button>}/>
  <InsightStrip items={[{label:multi?'当前页多表任务':'任务总数',value:multi?tasks.length:tasksQuery.data?.total??'—',icon:'tasks',hint:'当前登录租户的接入任务'},{label:'当前页运行中',value:tasks.filter(t=>Number(t.taskStatus)===1).length,icon:'check',hint:'以数据中台任务状态为准',tone:'cyan'},{label:'当前页异常',value:tasks.filter(t=>Number(t.taskStatus)===2).length,icon:'warning',hint:'可打开流程查看运行错误',tone:'amber'}]}/>
  <Panel><div className="ht-toolbar"><Input allowClear prefix={<Icon name="search" size={17}/>} value={query} onChange={event=>setQuery(event.target.value)} onPressEnter={search} placeholder="搜索任务名称" style={{width:310,maxWidth:'100%'}}/>{!multi&&<Select value={filter} style={{width:138}} onChange={value=>{setPage(1);setFilter(value);}} options={[{value:'all',label:'全部任务状态'},{value:'1',label:'运行中'},{value:'0',label:'未启用'},{value:'2',label:'运行异常'}]}/>}<Button onClick={search}>查询</Button><span className="toolbar-spacer"/><span className="helper">{multi?tasks.length:tasksQuery.data?.total??0} 项真实任务</span></div>
   {tasksQuery.isError&&<Alert type="error" showIcon message="任务读取失败" description={tasksQuery.error instanceof Error?tasksQuery.error.message:'请检查数据中台会话与接口'} action={<Button size="small" onClick={()=>void tasksQuery.refetch()}>重试</Button>} style={{marginBottom:16}}/>}
   {multi&&<Alert type="info" showIcon message="平台任务接口当前每个任务只关联一张来源表；多表任务的创建、汇总和逐表状态接口尚未提供。" style={{marginBottom:16}}/>}
   <DataTable<AccessTask> rowKey="tid" dataSource={tasks} loading={tasksQuery.isLoading||tasksQuery.isFetching} pagination={{current:page,pageSize:size,total:multi?tasks.length:tasksQuery.data?.total??0,showSizeChanger:!multi,pageSizeOptions:[10,20,50],onChange:(nextPage,nextSize)=>{setPage(nextPage);setSize(nextSize);}}} columns={[
    {title:'任务名称',width:260,render:(_,task)=><div className="task-name-cell"><span className="task-type-icon kind-接入"><Icon name="tasks" size={22}/></span><div><button className="text-link strong" onMouseEnter={warmCanvas} onFocus={warmCanvas} onClick={()=>openCanvas(task)}>{text(task.taskName)||'未命名任务'}</button><small>{task.recordSource==='pipeline'?'NiFi 流程':'接入任务'} · {task.tid}</small></div></div>},
    {title:'业务类型',width:104,render:()=> <Tag>接入</Tag>},
    {title:'数据路径',width:220,render:(_,task)=><div className="path-summary"><FlowMini/><small>{text(task.sourceDbName)||text(task.sourceTableName)||'来源待配置'} → {text(task.targetDbName)||text(task.targetTableName)||'目标待配置'}</small></div>},
    {title:'运行状态',width:118,render:(_,task)=>statusTag(task.taskStatus)},
    {title:'NiFi 关联',width:126,render:(_,task)=>task.pipelineId?<Tag color="success">已关联</Tag>:<span className="muted">待关联</span>},
    {title:'最近运行',width:138,render:(_,task)=>task.lastRunning?formatTime(task.lastRunning).slice(5,16):<span className="muted">尚未运行</span>},
    {title:'触发策略',width:114,render:(_,task)=>text(task.scheduleFrequency)||'—'},
    {title:'操作',fixed:'right',className:'ht-task-action-column',width:120,render:(_,task)=><Button type="link" onMouseEnter={warmCanvas} onFocus={warmCanvas} onClick={()=>openCanvas(task)}>NiFi 画布</Button>},
   ]}/>
  </Panel>
  <Modal title="选择已登记来源表" width={880} open={createOpen} onCancel={()=>setCreateOpen(false)} footer={<Button onClick={()=>setCreateOpen(false)}>关闭</Button>} destroyOnClose>
   <div className="ht-toolbar"><Input allowClear value={sourceQuery} onChange={event=>setSourceQuery(event.target.value)} onPressEnter={()=>{setSourcePage(1);setSourceKeyword(sourceQuery.trim());}} placeholder="搜索来源表" style={{width:280}}/><Button onClick={()=>{setSourcePage(1);setSourceKeyword(sourceQuery.trim());}}>查询</Button></div>
   {sourcesQuery.isError&&<Alert type="error" showIcon message="来源表读取失败" description={sourcesQuery.error instanceof Error?sourcesQuery.error.message:'请检查会话与接口'} style={{marginBottom:12}}/>}
   <DataTable<AccessTable> rowKey={row=>text(row.sourceTableId||row.tableId||row.tid)} dataSource={sourcesQuery.data?.list||[]} loading={sourcesQuery.isLoading} pagination={{current:sourcePage,pageSize:10,total:sourcesQuery.data?.total||0,onChange:setSourcePage}} columns={[
    {title:'来源表',width:260,render:(_,row)=><div className="entity-name"><b>{text(row.tableNameCn||row.tableName)||'未命名表'}</b><small>{text(row.tableName)}</small></div>},
    {title:'数据源',width:180,render:(_,row)=>text(row.datasourceName)||'—'},
    {title:'说明',render:(_,row)=>text(row.tableComment)||'—'},
    {title:'操作',width:130,render:(_,row)=><Button type="link" loading={creatingId===text(row.sourceTableId||row.tableId||row.tid)} onClick={()=>void create(row)}>创建并编排</Button>},
   ]}/>
  </Modal>
 </div>;
}

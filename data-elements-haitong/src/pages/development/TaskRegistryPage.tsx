import { Alert, Button, Form, Input, Select, Space, Tabs, Tag } from 'antd';
import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import PageHero from '../../components/PageHero';
import { useWorkbench, Workbench } from '../../app/Workbench';
import { useOceanTheme } from '../../design/theme';
import { DataTable, Loading, Panel, formatTime, useSafeAction } from '../../components/Common';
import { TaskDetail } from '../../components/Details';
import TaskWizard from '../../components/TaskWizard';
import { useWorkspace } from '../../hooks/useWorkspace';
import { defaultFlow, matchingMapping, validateTask } from '../../domain/rules';
import { newTask } from '../../domain/engine';
import type { Flow, Task } from '../../domain/types';
import { externalFlowRegistry, parseImportableCanvasDsl } from '../../api/externalFlowRegistry';
import { useComponentManifests } from '../../nifi/api/manifests';
import { useSessionRevision, useTokenReady } from '../../nifi/api/bridgeSession';

export default function TaskRegistryPage(){
 const [mode,setMode]=useState('nifi');
 return <div className="ht-page"><PageHero kicker="任务开发" title="任务登记" description="登记真实 NiFi 流程的外部标识；原海通 Flow JSON 保留在独立的本地演示模式。" kind="registry" tags={['真实流程登记','租户内防重复','本地演示保留']}/>
  <Tabs activeKey={mode} onChange={setMode} items={[{key:'nifi',label:'真实 NiFi 流程登记'},{key:'local',label:'原海通本地 Flow 演示'}]}/>
  {mode==='nifi'?<NativeRegistry/>:<LocalRegistryDemo/>}
 </div>;
}

function NativeRegistry(){
 const navigate=useNavigate();const {dark}=useOceanTheme();const {busy,perform}=useSafeAction();
 const tokenReady=useTokenReady();const revision=useSessionRevision();
 const manifests=useComponentManifests();
 const [raw,setRaw]=useState(''),[externalFlowId,setExternalFlowId]=useState(''),[name,setName]=useState(''),[description,setDescription]=useState('');
 const [existingPipelineId,setExistingPipelineId]=useState<string>();const [pendingPipelineId,setPendingPipelineId]=useState<string>();
 const [selectedPipelineOption,setSelectedPipelineOption]=useState<{value:string;label:string}>();
 const [selectorOpen,setSelectorOpen]=useState(false),[pipelineSearch,setPipelineSearch]=useState(''),[debouncedSearch,setDebouncedSearch]=useState(''),[pipelinePage,setPipelinePage]=useState(1);
 const [checks,setChecks]=useState<string[]>([]),[valid,setValid]=useState(false),[pageNum,setPageNum]=useState(1);
 useEffect(()=>{const timer=window.setTimeout(()=>setDebouncedSearch(pipelineSearch.trim()),300);return()=>window.clearTimeout(timer);},[pipelineSearch]);
 const pipelines=useQuery({queryKey:['registry-pipeline-options',revision,debouncedSearch,pipelinePage],queryFn:()=>externalFlowRegistry.pipelineOptions(pipelinePage,20,debouncedSearch),enabled:tokenReady&&selectorOpen});
 const pageOptions=(pipelines.data?.list||[]).map(p=>({value:p.id,label:`${p.name} / ${p.id}`}));
 const pipelineOptions=selectedPipelineOption&&!pageOptions.some(p=>p.value===selectedPipelineOption.value)?[selectedPipelineOption,...pageOptions]:pageOptions;
 const pipelinePageCount=Math.max(1,Math.ceil((pipelines.data?.total||0)/20));
 const history=useQuery({queryKey:['external-flow-registry',revision,pageNum],queryFn:()=>externalFlowRegistry.page(pageNum,20),enabled:tokenReady});
 const reset=()=>{setValid(false);setChecks([]);setPendingPipelineId(undefined);};
 const plan=async()=>{
  const externalId=externalFlowId.trim();
  if(!/^[A-Za-z0-9._:-]{1,128}$/.test(externalId))throw new Error('外部流程 ID 只能包含字母、数字、点、下划线、冒号或连字符，最长 128 位');
  const selectedId=existingPipelineId||pendingPipelineId;
  if(!selectedId){
   if(!name.trim()||name.trim().length>200)throw new Error('请填写不超过 200 字的登记任务名称');
   if(manifests.isError)throw new Error('NiFi 组件清单未能读取，请先重试');
   if(!manifests.data)throw new Error('正在加载 NiFi 组件清单，请稍后重试');
   parseImportableCanvasDsl(raw,manifests.data);
  }
  const checked=await externalFlowRegistry.validate(externalId,selectedId);
  if(checked.exists&&!checked.sameBinding)throw new Error(`此外部流程 ID 已绑定 NiFi 流程 ${checked.pipelineId}`);
  if(selectedId&&!checked.pipelineExists)throw new Error('选中的 NiFi 流程不存在或无权访问');
  if(checked.pipelineAssignedElsewhere)throw new Error('这条 NiFi 流程已登记另一个外部流程 ID');
  return {externalId,selectedId};
 };
 const register=()=>void perform(async()=>{
  const checked=await plan();let pipelineId=checked.selectedId;
  if(!pipelineId){
   const dsl=parseImportableCanvasDsl(raw,manifests.data||[]);
   const saved=await externalFlowRegistry.createDraft(name.trim(),description.trim(),dsl);
   if(!saved?.id)throw new Error('NiFi 草稿保存失败：接口未返回流程 ID');
   pipelineId=saved.id;setPendingPipelineId(pipelineId);
  }
  try{await externalFlowRegistry.save(checked.externalId,pipelineId);}catch(error){
   throw new Error(`外部标识登记失败；NiFi 草稿 ${pipelineId} 已保存。请保留页面并重试绑定。${error instanceof Error?error.message:String(error)}`);
  }
  setPendingPipelineId(undefined);setValid(false);await history.refetch();
  navigate(`/development/canvas?pipelineId=${encodeURIComponent(pipelineId)}`,{state:{returnTo:'/development/registry'}});
 },'外部流程已登记为 NiFi 草稿');
 return <>
  {!tokenReady&&<Alert showIcon type="info" message="正在同步数据中台会话；登记需要当前租户的真实 NiFi 权限。" style={{marginBottom:16}}/>}
  <div className="registry-grid"><Panel title="NiFi Canvas DSL v1" extra={<Tag color="green">真实流程定义</Tag>}>
   <CodeMirror value={raw} onChange={v=>{setRaw(v);reset();}} extensions={[json()]} theme={dark?'dark':'light'} height="420px" placeholder="粘贴从 NiFi 画布导出的 Canvas DSL v1 JSON；如绑定已有流程，此处可留空。"/>
   <Alert style={{marginTop:14}} type="info" showIcon message="原海通 Flow JSON 的 kind/read/write 结构无法直接转换为 NiFi 组件配置；请使用右侧已有流程，或导入包含 version、manifestKey、category、config 的 NiFi Canvas DSL v1。导入只保存草稿，不自动部署。"/>
  </Panel><Panel title="登记信息"><Form layout="vertical">
   <Form.Item label="外部流程 ID" required><Input value={externalFlowId} onChange={e=>{setExternalFlowId(e.target.value);reset();}} placeholder="用于防止同一外部流程重复登记"/></Form.Item>
   <Form.Item label="绑定已有 NiFi 流程（可选）"><Select allowClear showSearch filterOption={false} value={existingPipelineId} onChange={v=>{setExistingPipelineId(v);setSelectedPipelineOption(v?pipelineOptions.find(p=>p.value===v):undefined);reset();}} onSearch={v=>{setPipelineSearch(v);setPipelinePage(1);}} onOpenChange={open=>{setSelectorOpen(open);if(!open){setPipelineSearch('');setDebouncedSearch('');setPipelinePage(1);}}} loading={pipelines.isFetching} options={pipelineOptions} notFoundContent={pipelines.isLoading?'正在查找流程…':'无匹配流程'} placeholder="搜索或翻页选择已有流程" popupRender={menu=><>{menu}<div onMouseDown={event=>event.preventDefault()} style={{display:'flex',alignItems:'center',justifyContent:'space-between',padding:'8px 12px',borderTop:'1px solid var(--ant-color-border-secondary)'}}><Button size="small" disabled={pipelinePage<=1||pipelines.isFetching} onClick={()=>setPipelinePage(value=>value-1)}>上一页</Button><span>第 {pipelinePage} / {pipelinePageCount} 页 · 共 {pipelines.data?.total||0} 条</span><Button size="small" disabled={pipelinePage>=pipelinePageCount||pipelines.isFetching} onClick={()=>setPipelinePage(value=>value+1)}>下一页</Button></div></>}/></Form.Item>
   {!existingPipelineId&&<><Form.Item label="新建流程名称" required><Input value={name} onChange={e=>{setName(e.target.value);reset();}}/></Form.Item><Form.Item label="流程说明"><Input value={description} onChange={e=>{setDescription(e.target.value);reset();}}/></Form.Item></>}
  </Form>
  {pipelines.isError&&<Alert type="error" showIcon message="已有 NiFi 流程读取失败" description={pipelines.error instanceof Error?pipelines.error.message:''} style={{marginBottom:12}}/>}
  {manifests.isError&&<Alert type="error" showIcon message="NiFi 组件清单读取失败" description={manifests.error instanceof Error?manifests.error.message:''} style={{marginBottom:12}}/>}
  {pendingPipelineId&&<Alert type="warning" showIcon message={`NiFi 草稿 ${pendingPipelineId} 已保存，登记尚未完成；请重试绑定。`} style={{marginBottom:12}}/>}
  <Space><Button disabled={!tokenReady} loading={busy} onClick={()=>void perform(async()=>{try{await plan();setChecks([]);setValid(true);}catch(error){setChecks([error instanceof Error?error.message:String(error)]);setValid(false);throw error;}},'登记预检通过')}>解析与校验</Button><Button type="primary" disabled={!tokenReady||!valid} loading={busy} onClick={register}>一键登记</Button></Space>
  {checks.length>0&&<Alert style={{marginTop:12}} type="error" message={checks.join('；')}/>} {valid&&<Alert style={{marginTop:12}} type="success" message="格式与外部标识已通过预检；登记后将在 NiFi 画布继续配置。"/>}
  </Panel></div>
  <Panel title="真实登记历史">{history.isError&&<Alert type="error" showIcon message="登记历史读取失败" description={history.error instanceof Error?history.error.message:''} action={<Button size="small" onClick={()=>void history.refetch()}>重试</Button>}/>}
   <DataTable rowKey="tid" loading={history.isLoading} dataSource={history.data?.list||[]} pagination={{current:pageNum,pageSize:20,total:history.data?.total||0,onChange:setPageNum}} columns={[
    {title:'外部流程标识',dataIndex:'externalFlowId'},
    {title:'NiFi 流程',render:(_,r)=><Button type="link" onClick={()=>navigate(`/development/canvas?pipelineId=${encodeURIComponent(r.pipelineId)}`,{state:{returnTo:'/development/registry'}})}>{r.pipelineName||r.pipelineId}</Button>},
    {title:'登记时间',render:(_,r)=>formatTime(r.createdTime)},
    {title:'状态',render:(_,r)=><Tag color={r.processGroupId?'green':'blue'}>{r.processGroupId?'已部署':'已保存草稿'}</Tag>},
   ]}/>
  </Panel>
 </>;
}

function LocalRegistryDemo(){
 const local=useWorkspace();const navigate=useNavigate();const [taskId,setTaskId]=useState<string>();const [wizard,setWizard]=useState<Task>();
 if(local.isPending)return <Loading/>;
 if(local.error||!local.data)return <Alert type="error" message="本地演示工作区未能打开" description={local.error instanceof Error?local.error.message:'请检查浏览器站点存储'} action={<Button onClick={()=>void local.refetch()}>重试</Button>}/>;
 const context={state:local.data,act:local.act,editTask:(task?:Task)=>setWizard(task),showTask:(id:string)=>setTaskId(id),showRun:()=>{},openCanvas:(id:string)=>navigate(`/development/canvas?task=${encodeURIComponent(id)}`,{state:{returnTo:'/development/registry'}})};
 return <Workbench.Provider value={context}><Alert type="warning" showIcon message="本地演示模式" description="下方登记只保存到此浏览器 IndexedDB，不生成 NiFi 流程，也不会写入数据中台任务库。" style={{marginBottom:16}}/><LocalRegistryForm/>
  {taskId&&<TaskDetail id={taskId} onClose={()=>setTaskId(undefined)}/>}{wizard&&<TaskWizard initial={wizard} onClose={()=>setWizard(undefined)}/>}
 </Workbench.Provider>;
}

function LocalRegistryForm(){
 const {state,act,showTask}=useWorkbench();const {dark}=useOceanTheme();const {busy,perform}=useSafeAction();
 const [raw,setRaw]=useState(''),[flowId,setFlowId]=useState(''),[name,setName]=useState(''),[cluster,setCluster]=useState(state.clusters[0]?.id||''),[source,setSource]=useState(state.tables[0]?.id||''),[target,setTarget]=useState(state.tables[8]?.id||state.tables[1]?.id||'');
 const [checks,setChecks]=useState<string[]>([]),[valid,setValid]=useState(false);
 const parse=()=>{const input=JSON.parse(raw) as {flow?:Flow};const flow=input.flow||JSON.parse(raw) as Flow;if(!Array.isArray(flow.nodes)||!Array.isArray(flow.edges)||flow.nodes.length>200)throw new Error('请导入海通导出的本地 Flow JSON（nodes / edges，最多 200 节点）');return flow;};
 const config=()=>newTask(name,cluster,[matchingMapping(state.tables.find(t=>t.id===source)!,state.tables.find(t=>t.id===target)!)],parse());
 return <><div className="registry-grid"><Panel title="本地任务流定义" extra={<Button onClick={()=>{setRaw(JSON.stringify(defaultFlow(),null,2));setValid(false);}}>载入标准结构模板</Button>}><CodeMirror value={raw} onChange={v=>{setRaw(v);setValid(false);}} extensions={[json()]} theme={dark?'dark':'light'} height="420px" placeholder="粘贴本地 Flow JSON（nodes / edges）"/><Alert style={{marginTop:14}} type="info" message="此模式只识别原海通本地 Flow JSON；NiFi 流程不会通过这里导入或部署。"/></Panel>
  <Panel title="登记信息"><Form layout="vertical"><Form.Item label="外部流程 ID" required><Input value={flowId} onChange={e=>setFlowId(e.target.value)} placeholder="用于防止本地重复登记"/></Form.Item><Form.Item label="登记任务名称" required><Input value={name} onChange={e=>{setName(e.target.value);setValid(false);}}/></Form.Item><Form.Item label="来源表"><Select showSearch optionFilterProp="label" value={source} onChange={v=>{setSource(v);setValid(false);}} options={state.tables.map(t=>({value:t.id,label:`${t.name} / ${state.sources.find(s=>s.id===t.sourceId)?.name}`}))}/></Form.Item><Form.Item label="目标表"><Select showSearch optionFilterProp="label" value={target} onChange={v=>{setTarget(v);setValid(false);}} options={state.tables.map(t=>({value:t.id,label:`${t.name} / ${state.sources.find(s=>s.id===t.sourceId)?.name}`}))}/></Form.Item><Form.Item label="运行集群"><Select value={cluster} onChange={v=>{setCluster(v);setValid(false);}} options={state.clusters.map(c=>({value:c.id,label:c.name}))}/></Form.Item></Form>
   <Space><Button onClick={()=>{try{const errors=validateTask(config(),state).filter(e=>e.level==='error').map(e=>e.message);setChecks(errors);setValid(!errors.length);}catch(e){setChecks([String(e)]);setValid(false);}}}>解析与校验</Button><Button type="primary" disabled={!valid||!flowId.trim()} loading={busy} onClick={()=>void perform(async()=>{const task=config();await act({type:'REGISTER',task,flowId});setValid(false);showTask(task.id);},'本地任务已登记为草稿')}>一键登记</Button></Space>{checks.length>0&&<Alert type="error" message={checks.join('；')}/>} {valid&&<Alert type="success" message="配置通过本地校验，可以登记"/>}</Panel></div>
  <Panel title="本地登记历史"><DataTable rowKey="id" dataSource={state.registrations} columns={[{title:'外部流程标识',dataIndex:'flowId'},{title:'登记任务',render:(_,r)=><Button type="link" onClick={()=>showTask(r.taskId)}>{state.tasks.find(t=>t.id===r.taskId)?.name}</Button>},{title:'登记时间',render:(_,r)=>formatTime(r.at)},{title:'结果',render:()=> <Tag color="blue">仅本地台账</Tag>}]}/></Panel></>;
}

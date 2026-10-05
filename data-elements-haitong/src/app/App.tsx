import { App as AntApp, Alert, Button, Result } from 'antd';
import { lazy, Suspense, useState } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import AppLayout from '../layout/AppLayout';
import { useWorkspace, useWorkspaceRuntime } from '../hooks/useWorkspace';
import { Workbench } from './Workbench';
import { Loading } from '../components/Common';
import { TaskDetail, RunDetail } from '../components/Details';
import TaskWizard from '../components/TaskWizard';
import type { Task, Workspace } from '../domain/types';
import type { Action } from '../domain/engine';
import { useSessionRevision } from '../nifi/api/bridgeSession';
import { entryRouteDestination } from './entryRoute';
import { preloadCanvas } from './preloadCanvas';
const Overview=lazy(()=>import('../pages/overview/OverviewPage'));
const Tasks=lazy(()=>import('../pages/development/TaskListPage'));
const NifiFlows=lazy(()=>import('../pages/development/NifiFlowsPage'));
const Registry=lazy(()=>import('../pages/development/TaskRegistryPage'));
const Batch=lazy(()=>import('../pages/development/BatchCreatePage'));
const Multi=lazy(()=>import('../pages/development/MultiTableSyncPage'));
const Canvas=lazy(preloadCanvas);
const Ingress=lazy(()=>import('../pages/ops/IngressMonitorPage'));
const Distribution=lazy(()=>import('../pages/ops/DistributionMonitorPage'));
const Clusters=lazy(()=>import('../pages/ops/ClusterMonitorPage'));
const Cross=lazy(()=>import('../pages/ops/CrossNetworkPage'));
const Instant=lazy(()=>import('../pages/reconcile/InstantReconcilePage'));
const Inventory=lazy(()=>import('../pages/reconcile/InventoryReconcilePage'));
const Statements=lazy(()=>import('../pages/reconcile/InventoryStatementsPage'));
/** Keep iframe links from the original NiFi canvas usable after consolidation. */
function EntryRoute(){
 const location=useLocation();
 return <Navigate to={entryRouteDestination(location.search, window.location.search)} replace/>;
}
export default function App(){
 const location=useLocation();
 const sessionRevision=useSessionRevision();
 const livePaths=new Set(['/','/integration/overview','/overview','/development/tasks','/development/nifi-flows','/development/registry','/development/batch','/development/multi-table','/development/canvas','/ops/ingress','/ops/distribution','/ops/clusters','/ops/cross-network','/reconcile/instant','/reconcile/inventory','/reconcile/statements']);
 const localCanvas=location.pathname==='/development/canvas'&&new URLSearchParams(location.search).has('task');
 if(livePaths.has(location.pathname)&&!localCanvas)return <LoadedApp key={`platform-session-${sessionRevision}`} state={emptyWorkspace} act={localActionUnavailable} live/>;
 return <LocalWorkspaceApp/>;
}
const emptyWorkspace:Workspace={schemaVersion:2,revision:0,sources:[],tables:[],tasks:[],runs:[],bills:[],clusters:[],strategies:[],statements:[],batches:[],registrations:[],logs:[]};
const localActionUnavailable=async(_action:Action):Promise<Workspace>=>{throw new Error('该页面使用数据中台真实任务，请在真实任务接口完成操作');};
function LocalWorkspaceApp(){
 const {data,error,isPending,refetch,act}=useWorkspace();
 if(isPending)return <Loading/>;
 if(error||!data)return <Result status="error" title="本地工作区未能打开" subTitle={error instanceof Error?error.message:'请检查浏览器站点存储是否可用。'} extra={<Button type="primary" onClick={()=>void refetch()}>重试</Button>}/>;
 return <LoadedApp state={data} act={act} live={false}/>;
}
function LocalRuntime({onError}:{onError:(error:unknown)=>void}){useWorkspaceRuntime(onError);return null;}
function LoadedApp({state,act,live}:{state:Workspace;act:(a:Action)=>Promise<Workspace>;live:boolean}){
 const nav=useNavigate(),location=useLocation();const [wizard,setWizard]=useState<{task?:Task;scenario?:Task['scenario']}>(),[taskDetail,setTaskDetail]=useState<string>(),[runDetail,setRunDetail]=useState<string>(),[runtimeError,setRuntimeError]=useState('');
 const context={state,act,editTask:(task?:Task,scenario?:Task['scenario'])=>{setTaskDetail(undefined);setRunDetail(undefined);setWizard({task,scenario});},showTask:(id:string)=>{setRunDetail(undefined);setTaskDetail(id);},showRun:(id:string)=>{setTaskDetail(undefined);setRunDetail(id);},openCanvas:(id:string)=>{setTaskDetail(undefined);setRunDetail(undefined);nav(`/development/canvas?task=${encodeURIComponent(id)}`,{state:{returnTo:location.pathname+location.search}});}};
 const content=<Suspense fallback={<Loading text="正在装载任务视图" compact/>}><Routes><Route path="/" element={<EntryRoute/>}/><Route path="/integration/overview" element={<Navigate to="/overview" replace/>}/><Route path="/overview" element={<Overview/>}/><Route path="/development/tasks" element={<Tasks/>}/><Route path="/development/nifi-flows" element={<NifiFlows/>}/><Route path="/development/registry" element={<Registry/>}/><Route path="/development/batch" element={<Batch/>}/><Route path="/development/multi-table" element={<Multi/>}/><Route path="/development/canvas" element={<Canvas/>}/><Route path="/ops/ingress" element={<Ingress/>}/><Route path="/ops/distribution" element={<Distribution/>}/><Route path="/ops/clusters" element={<Clusters/>}/><Route path="/ops/cross-network" element={<Cross/>}/><Route path="/reconcile/instant" element={<Instant/>}/><Route path="/reconcile/inventory" element={<Inventory/>}/><Route path="/reconcile/statements" element={<Statements/>}/><Route path="*" element={<Result status="404" title="页面不存在" extra={<Button onClick={()=>nav('/overview')}>返回总览</Button>}/>}/></Routes></Suspense>;
 return <Workbench.Provider value={context}>{!live&&<LocalRuntime onError={e=>setRuntimeError(e instanceof Error?e.message:String(e))}/>} {runtimeError&&<Alert className="runtime-error" type="error" closable message={`本地任务未提交：${runtimeError}`} onClose={()=>setRuntimeError('')}/>} {location.pathname==='/development/canvas'?content:<AppLayout live={live}>{content}</AppLayout>}{wizard&&<TaskWizard initial={wizard.task} scenario={wizard.scenario} onClose={()=>setWizard(undefined)}/>} {taskDetail&&<TaskDetail id={taskDetail} onClose={()=>setTaskDetail(undefined)}/>} {runDetail&&<RunDetail id={runDetail} onClose={()=>setRunDetail(undefined)}/>}</Workbench.Provider>;
}

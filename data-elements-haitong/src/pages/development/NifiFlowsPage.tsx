import { Alert, App, Button, Space } from 'antd';
import { isAxiosError } from 'axios';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useServerSort } from '../../components/serverSort';
import PageHero from '../../components/PageHero';
import { DataTable, EmptyState, Panel } from '../../components/Common';
import { useDeletePipeline, usePipelinePage, type PipelineSummary } from '../../nifi/api/pipelines';
import { getBridgeToken, useSessionRevision, useTokenReady } from '../../nifi/api/bridgeSession';
import { getPlatformLoginUrl, redirectToPlatformLogin, syncNifiPlatformSession } from '../../services/platformSession';
import { preloadCanvasReadiness, preloadCanvasWhenIdle } from '../../app/preloadCanvas';

/** The platform session bridge supplies NiFi access; no credential entry is needed here. */
export function NifiSessionPrompt({ redirectOnMissing = false }: { redirectOnMissing?: boolean } = {}) {
 const [status, setStatus] = useState<'checking'|'connected'|'missing'|'error'>('checking');
 const [detail, setDetail] = useState('');
 const embedded = window.parent !== window;
 const check = () => {
  setStatus('checking');
  void syncNifiPlatformSession().then(() => {
   // Embedded clients may send INIT after this component mounts.
   const connected = Boolean(getBridgeToken());
   setStatus(connected ? 'connected' : 'missing');
   if (!connected && redirectOnMissing && !embedded) redirectToPlatformLogin();
  }).catch((error: unknown) => {
   setDetail(error instanceof Error ? error.message : '登录态同步失败');
   setStatus('error');
  });
 };
 useEffect(() => { check(); }, []);
 return <div style={{ maxWidth: 560 }}>
  <Alert type={status === 'error' ? 'error' : status === 'connected' ? 'success' : 'info'} showIcon
   message={status === 'checking' ? '正在同步数据中台登录态' : status === 'connected' ? '已使用数据中台登录态' : embedded ? '正在等待原系统传入登录态' : '请先登录数据中台'}
   description={status === 'error' ? detail : status === 'checking' ? '海通将直接使用当前平台会话。' : status === 'connected' ? 'NiFi 流程可直接使用。' : embedded ? '请从已登录的数据中台重新打开画布。' : '登录后从“数据集成中心”进入，无需填写 Token。'}
   action={status !== 'checking' && status !== 'connected' && !embedded ? <Space><Button size="small" onClick={check}>重试</Button><Button size="small" type="primary" onClick={() => window.location.assign(getPlatformLoginUrl())}>前往数据中台登录</Button></Space> : undefined}/>
 </div>;
}

export default function NifiFlowsPage() {
 const navigate = useNavigate();
 const { message, modal } = App.useApp();
 const tokenReady = useTokenReady();
 const sessionRevision = useSessionRevision();
 const queryClient = useQueryClient();
 const [page,setPage]=useState(1),[size,setSize]=useState(20);
 const order=useServerSort(()=>setPage(1));
 const list = usePipelinePage(page,size,order.sort);
 const deletePipeline = useDeletePipeline();
 const [showSession, setShowSession] = useState(false);
 const warmCanvas = () => { if (tokenReady) preloadCanvasReadiness(queryClient,sessionRevision); };
 useEffect(() => tokenReady ? preloadCanvasWhenIdle(() => preloadCanvasReadiness(queryClient,sessionRevision)) : undefined, [tokenReady,queryClient,sessionRevision]);
 const open = (pipelineId?: string) => navigate(`/development/canvas${pipelineId?`?pipelineId=${encodeURIComponent(pipelineId)}`:''}`, {state:{returnTo:'/development/nifi-flows'}});
 const confirmDelete = (item: PipelineSummary) => {
  modal.confirm({
   title: `删除流程“${item.name}”？`,
   content: item.nifiProcessGroupId
    ? '删除会移除平台流程、关联接入任务及对应的 NiFi 流程组，并清理组内队列。'
    : '删除后，该流程将从列表中移除。',
   okText: '删除',
   cancelText: '取消',
   okButtonProps: { danger: true },
   onOk: async () => {
    const wasLastOnPage = list.data?.list.length === 1;
    try {
     await deletePipeline.mutateAsync(item.id);
     if (page > 1 && wasLastOnPage) setPage(current => current === page ? current - 1 : current);
     message.success('流程已删除');
    } catch (error) {
     const detail = isAxiosError(error) ? error.response?.data?.message || error.response?.data?.error : undefined;
     message.error(typeof detail === 'string' ? detail : '删除流程失败，请刷新列表后重试。');
     throw error;
    }
   },
  });
 };
 return <div className="ht-page">
  <PageHero kicker="任务开发" title="NiFi 流程" description="在海通集成中心管理真实 NiFi 流程，使用完整画布配置、部署和监控。" kind="nifi" tags={['真实流程','完整画布','部署运行']}/>
  <Panel title="真实流程" extra={<Space><Button onClick={()=>{setShowSession(v=>!v);}}>同步会话</Button><Button disabled={!tokenReady} loading={list.isFetching} onClick={()=>void list.refetch()}>刷新</Button><Button type="primary" disabled={!tokenReady} onClick={()=>open()}>新建流程</Button></Space>}>
   {(!tokenReady||showSession)&&<NifiSessionPrompt redirectOnMissing/>}
   {tokenReady&&list.isError&&<Alert type="error" showIcon style={{marginTop:16}} message="读取 NiFi 流程失败" description={list.error instanceof Error?list.error.message:'请检查会话权限与后端连接。'} action={<Button size="small" onClick={()=>setShowSession(true)}>重新同步会话</Button>}/>}
   {tokenReady&&list.isLoading&&<p role="status">正在读取 NiFi 流程…</p>}
   {tokenReady&&!list.isLoading&&!list.isError&&<DataTable<PipelineSummary>
    rowKey="id"
    dataSource={list.data?.list??[]} serverSort={order} pagination={{current:page,pageSize:size,total:list.data?.total||0,onChange:(p,n)=>{setPage(n===size?p:1);setSize(n);}}}
    columns={[
     {title:'流程名称',dataIndex:'name',key:'name',width:220,ellipsis:{showTitle:false},render:(name:string,item:PipelineSummary)=><Button type="link" title={name} style={{maxWidth:188,minWidth:0}} onClick={()=>open(item.id)}>
      <span style={{overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{name}</span>
     </Button>},
     {title:'流程 ID',dataIndex:'id',key:'id',ellipsis:true},
     {title:'组件数',dataIndex:'nodeCount',key:'nodeCount',width:100,render:(value:number|undefined)=>value??'—'},
     {title:'NiFi Process Group',dataIndex:'nifiProcessGroupId',key:'nifiProcessGroupId',ellipsis:true,render:(value:string|null|undefined)=>value||'未部署'},
     {title:'操作',key:'action',width:190,render:(_:unknown,item:PipelineSummary)=><Space>
      <Button onMouseEnter={warmCanvas} onFocus={warmCanvas} onClick={()=>open(item.id)}>打开画布</Button>
      <Button danger disabled={deletePipeline.isPending} loading={deletePipeline.isPending && deletePipeline.variables === item.id}
       onClick={()=>confirmDelete(item)}>删除</Button>
     </Space>},
    ]}
    locale={{emptyText:<EmptyState title="暂无 NiFi 流程" text="可以新建一个流程并在画布中配置组件。" compact/>}}
   />}
  </Panel>
 </div>;
}

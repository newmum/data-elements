import { Alert, Button, Input, Modal, Pagination, Spin, Tabs, Tag } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { safeDiagnostic } from '../../api/platformError';
import { deploymentAllows } from '../../app/deployment';
import { DataTable, EmptyState, Panel, formatTime } from '../../components/Common';
import { useSessionRevision, useTokenReady } from '../../nifi/api/bridgeSession';
import { readFlowErrorHistory, readLiveFlowBulletins, usePipelinePage, type ErrorLevel, type PipelineSummary } from '../../nifi/api/pipelines';
import { getApiErrorMessage } from '../../nifi/api/response';
import { NifiSessionPrompt } from '../development/NifiFlowsPage';
import './NifiLogsPage.css';

type LogView = 'live' | 'history';
type LogRow = { key: string; time: string; level: ErrorLevel; source: string; message: string };
const FLOW_PAGE_SIZE = 10;
const displayTime = (time: string) => {
 const formatted = formatTime(time);
 return formatted === '—' ? time || '—' : formatted;
};
const levelTag = (level: ErrorLevel) => <Tag color={level==='ERROR'?'error':'warning'}>{level==='ERROR'?'错误':'警告'}</Tag>;

export default function NifiLogsPage() {
 const navigate = useNavigate();
 const tokenReady = useTokenReady();
 const revision = useSessionRevision();
 const [flowPage, setFlowPage] = useState(1);
 const [keyword, setKeyword] = useState('');
 const [selected, setSelected] = useState<PipelineSummary>();
 const flows = usePipelinePage(flowPage, FLOW_PAGE_SIZE, undefined, keyword);
 const options = flows.data?.list ?? [];
 const flow = options.find(item=>item.id===selected?.id) ?? selected ?? options.find(item=>item.nifiProcessGroupId) ?? options[0];
 const [view, setView] = useState<LogView>('live');
 const [logPage, setLogPage] = useState(1);
 const [detail, setDetail] = useState<LogRow>();
 const flowId = flow?.id;
 const deployed = Boolean(flow?.nifiProcessGroupId);
 const canRead = tokenReady && Boolean(flowId) && (view==='history' || deployed);
 const logs = useQuery<LogRow[]>({
  queryKey: ['nifi-logs', revision, flowId, view],
  enabled: canRead,
  retry: false,
  queryFn: async ({signal}) => {
   if (!flowId) return [];
   if (view==='live') {
    const rows = await readLiveFlowBulletins(flowId, signal);
    return rows.map(row=>({key:`${row.id}:${row.sourceId ?? ''}:${row.timestamp}`,time:row.timestamp,
     level:row.level,source:row.sourceName,message:row.message}));
   }
   const rows = await readFlowErrorHistory(flowId, signal);
   return rows.filter(row=>row.phase==='RUNTIME').map(row=>({key:row.id,time:row.occurredAt,
    level:row.level,source:row.nodeLabel||'',message:row.message}));
  },
 });
 const chooseFlow = (item: PipelineSummary) => {
  setSelected(item);
  if (!item.nifiProcessGroupId) setView('history');
  setLogPage(1);
  setDetail(undefined);
 };
 const rows = logs.data ?? [];
 const empty = view==='live' ? '近期没有 NiFi 错误或警告公告。' : '没有已保存的 NiFi 运行错误或警告。';
 const logTable = logs.isError && canRead ? <Alert type="warning" showIcon message="读取运行记录失败"
  description={safeDiagnostic(getApiErrorMessage(logs.error,'请检查连接后刷新。'))}/> :
  <DataTable<LogRow> rowKey="key" loading={canRead&&logs.isLoading} dataSource={canRead?rows:[]} tableLayout="fixed"
   scroll={{x:470}} pagination={rows.length>10?{current:Math.min(logPage,Math.ceil(rows.length/10)),pageSize:10,
    total:rows.length,onChange:setLogPage,showSizeChanger:false}:false}
   locale={{emptyText:canRead&&logs.isLoading?'正在读取运行记录…':!deployed&&view==='live'?'流程尚未部署，暂无实时公告。':empty}}
   columns={[
    {title:'发生时间',width:156,render:(_,row)=>displayTime(row.time)},
    {title:'级别',width:64,render:(_,row)=>levelTag(row.level)},
    {title:'来源',dataIndex:'source',width:112,ellipsis:true,render:(source:string)=>source||'—'},
    {title:'消息',dataIndex:'message',render:(_:unknown,row:LogRow)=><button type="button" className="text-link ht-nifi-log-message"
     aria-label={`查看${row.level==='ERROR'?'错误':'警告'}详情`} onClick={()=>setDetail(row)}>{safeDiagnostic(row.message)||'查看详情'}</button>},
   ]}/>;
 return <div className="ht-page ht-nifi-logs">
  <header className="ht-nifi-logs-title">
   <h1>NiFi 运行日志</h1>
   <p className="helper">按流程查看 NiFi 处理器的实时公告及已记录的运行错误和警告。</p>
  </header>
  {!tokenReady ? <NifiSessionPrompt redirectOnMissing/> : <div className="ht-nifi-logs-workspace">
   <Panel title="流程" className="ht-nifi-logs-flows">
    <Input.Search allowClear aria-label="搜索流程名称" placeholder="搜索流程名称" onSearch={value=>{
     setSelected(flow); setKeyword(value.trim()); setFlowPage(1);
    }}/>
    {flows.isError ? <Alert type="warning" showIcon message="无法读取流程列表"
     action={<Button size="small" onClick={()=>void flows.refetch()}>重试</Button>}/> :
     <Spin spinning={flows.isFetching}>
      {options.length>0 ? <ul className="ht-nifi-logs-flow-list" aria-label="流程列表">
       {options.map(item=><li key={item.id}>
        <button type="button" aria-pressed={flowId===item.id} onClick={()=>chooseFlow(item)} title={item.name}>
         <span>{item.name}</span>{!item.nifiProcessGroupId&&<small>未部署</small>}
        </button>
       </li>)}
      </ul> : !flows.isFetching && <p className="helper" role="status">{keyword?'没有符合条件的流程。':'暂无可查看的流程。'}</p>}
     </Spin>}
    {Boolean(flows.data?.total&&flows.data.total>FLOW_PAGE_SIZE)&&<Pagination simple size="small" current={flowPage}
     pageSize={FLOW_PAGE_SIZE} total={flows.data?.total} showSizeChanger={false} onChange={page=>{setSelected(flow);setFlowPage(page);}}/>}
   </Panel>
   <Panel title="运行记录" extra={flow&&deploymentAllows('/development/canvas')&&<Button size="small" type="link" onClick={()=>navigate(
    `/development/canvas?pipelineId=${encodeURIComponent(flow.id)}`, {state:{returnTo:'/management/logs'}},
   )}>查看画布</Button>}>
    {!flow ? <EmptyState compact title={flows.isLoading?'正在读取流程…':'暂无选中的流程'} text="选择一个流程后查看运行记录。"/> : <>
     <div className="ht-nifi-logs-current" title={flow.name}>{flow.name}</div>
     <Tabs size="small" activeKey={view} onChange={key=>{setView(key==='history'?'history':'live');setLogPage(1);}}
      items={[{key:'live',label:'实时公告',children:view==='live'?logTable:undefined},
       {key:'history',label:'运行历史',children:view==='history'?logTable:undefined}]}
      tabBarExtraContent={<Button size="small" disabled={!canRead} loading={logs.isFetching} onClick={()=>void logs.refetch()}>刷新</Button>}/>
    </>}
   </Panel>
  </div>}
  <Modal title="NiFi 运行日志详情" open={Boolean(detail)} onCancel={()=>setDetail(undefined)}
   footer={<Button onClick={()=>setDetail(undefined)}>关闭</Button>}>
   {detail&&<>
    <p>{levelTag(detail.level)}{displayTime(detail.time)}{detail.source&&` · ${detail.source}`}</p>
    <div className="ht-nifi-log-detail">{safeDiagnostic(detail.message)||'暂无消息'}</div>
   </>}
  </Modal>
 </div>;
}

import { useState } from 'react';
import { Alert, App, Button, Drawer, Empty, Select, Space } from 'antd';
import { ReactFlow, Background, Controls, MiniMap } from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';
import type { Row } from '../../services/api';
import { lineageTasks, lineageGraph, runtimeLineage } from '../../services/lineage';
import { useWorkspaceQuery } from '../../services/useWorkspaceQuery';
import { useStudio } from '../er/store';
import { FullscreenWorkspace } from '../../app/FullscreenWorkspace';
import { DataTable, ErrorNotice, Page, date, useAction } from './common';
export function SharedLineagePage(){
 const q=useWorkspaceQuery('shared-lineage-tasks',s=>lineageTasks(s));const [current,setCurrent]=useState<Row|null>(null),[node,setNode]=useState<string>(),[evidence,setEvidence]=useState<Row|null>(null),[error,setError]=useState('');
 const {busy,run}=useAction(),{message}=App.useApp();const theme=useStudio(s=>s.theme);
 const open=(row:Row)=>run(async()=>{const graph=await lineageGraph(row.id);setCurrent(graph);setNode(undefined);setEvidence(null);setError('');},'');
 const runtime=()=>run(async()=>{if(!current||!node)return;setError('');try{const r=await runtimeLineage(current.id,node);setEvidence(r);if(!r.nodes.length)message.info(r.message||'当前节点暂无运行血缘事件');}catch(e){setError(e instanceof Error?e.message:String(e));}},'');
 const nodes:Node[]=(current?.nodes||[]).map((n:Row,i:number)=>({id:n.id,position:{x:Number.isFinite(n.x)?n.x:100+(i%4)*300,y:Number.isFinite(n.y)?n.y:100+Math.floor(i/4)*170},data:{label:n.label}}));
 const edges:Edge[]=(current?.edges||[]).map((e:Row)=>({id:e.id,source:e.source,target:e.target,label:e.label}));
 return <Page title="数据血缘" actions={<Button type="primary" loading={q.loading} onClick={q.refresh}>刷新接入任务</Button>}><DataTable rows={q.data} loading={q.loading} error={q.error} onRefresh={q.refresh} titleKey="shared-lineage-tasks" columns={[
  {title:'链路 / 接入任务',dataIndex:'name',width:270},{title:'节点数',dataIndex:'nodeCount',align:'right',width:100},{title:'运行血缘',width:140,render:(_,r)=>r.nifiProcessGroupId?'可查询运行事件':'尚未部署'}, {title:'更新时间',dataIndex:'updatedAt',width:175,render:date},{title:'说明',dataIndex:'description',width:260},{title:'操作',width:128,render:(_,r)=><Button type="link" loading={busy&&current?.id===r.id} onClick={()=>open(r)}>全屏血缘画布</Button>}
 ]}/>{current&&<FullscreenWorkspace kind="lineage" title={current.name} busy={busy} onClose={()=>setCurrent(null)}><div className="lineage-workspace"><div className="lineage-toolbar"><Space wrap><span>共享后端已保存的接入链路</span><Select style={{width:240}} aria-label="运行血缘查询节点" placeholder="选择节点查询运行血缘" value={node} onChange={setNode} options={current.nodes.map((n:Row)=>({value:n.id,label:n.label}))}/><Button type="primary" loading={busy} disabled={!current.nifiProcessGroupId||!node} onClick={runtime}>查询运行血缘</Button><span className="muted">仅查看，不修改接入任务和加工连线</span></Space></div><ErrorNotice error={error}/><ReactFlow colorMode={theme} nodes={nodes} edges={edges} nodesDraggable={false} nodesConnectable={false} deleteKeyCode={null} fitView><Background/><Controls/><MiniMap/></ReactFlow></div></FullscreenWorkspace>}<Drawer title="运行血缘 · NiFi 事件" open={!!evidence} width="min(950px, 100vw)" onClose={()=>setEvidence(null)}>{evidence&&<><Alert type="info" showIcon title={evidence.anchorEventId?'真实运行事件血缘':'暂无运行事件'} description={evidence.message||'由 NiFi provenance 追踪返回，不是字段名匹配产生的逻辑关系。'}/>{evidence.nodes.length?<div style={{height:570,marginTop:18}}><ReactFlow colorMode={theme} nodes={evidence.nodes.map((n:Row,i:number)=>({id:String(n.id),position:{x:(i%3)*260,y:Math.floor(i/3)*170},data:{label:[n.type,n.eventType,n.id].filter(Boolean).join(' · ')}}))} edges={evidence.edges.map((e:Row,i:number)=>({id:String(e.id||i),source:String(e.sourceId||e.source),target:String(e.targetId||e.target)}))} nodesDraggable={false} nodesConnectable={false} deleteKeyCode={null} fitView><Background/><Controls/></ReactFlow></div>:<Empty description="当前节点暂无可追踪事件"/>}</>}</Drawer></Page>;
}

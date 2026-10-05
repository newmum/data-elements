import { App, Alert, Button, Drawer, Form, Input, Modal, Select, Space, Switch, Tag } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import PageHero from '../../components/PageHero';
import { InsightStrip, Panel } from '../../components/Common';
import Icon from '../../design/Icon';
import { integrationNodesApi, type IntegrationNode, type NodeDraft } from '../../api/integrationNodes';

const blank=():NodeDraft=>({nodeName:'',nodeCode:'',networkCode:'',baseUrl:'',rootProcessGroupId:'',authUsername:'',authPassword:'',insecureTls:true,remark:'',enabled:true,isDefault:false});
const bytes=(value:number|null|undefined)=>value==null?'—':`${(value/1024/1024/1024).toFixed(2)} GiB`;

/** NiFi nodes are the platform's existing nifi_node_t resources, not local CPU samples. */
export default function ClusterMonitorPage(){
 const {message,modal}=App.useApp(),client=useQueryClient();
 const query=useQuery({queryKey:['platform','nifi-nodes'],queryFn:async()=>{
  const [options,nodes]=await Promise.all([integrationNodesApi.accessOptions(),integrationNodesApi.page()]);
  return {networks:options.networks,nodes};
 }});
 const [detail,setDetail]=useState<IntegrationNode>(),[edit,setEdit]=useState<NodeDraft>(),[saving,setSaving]=useState(false),[testing,setTesting]=useState<string>();
 const diagnostics=useQuery({queryKey:['platform','nifi-node-diagnostics',detail?.tid],queryFn:()=>integrationNodesApi.diagnostics(detail!.tid),enabled:!!detail?.tid,refetchInterval:detail?30000:false,retry:0});
 const networks=query.data?.networks||[],nodes=query.data?.nodes||[];
 const refresh=()=>void client.invalidateQueries({queryKey:['platform','nifi-nodes']});
 const editNode=(node:IntegrationNode)=>setEdit({tid:node.tid,nodeName:node.nodeName,nodeCode:node.nodeCode,networkCode:node.networkCode,baseUrl:node.baseUrl,rootProcessGroupId:node.rootProcessGroupId,authUsername:node.authUsername,authPassword:'',insecureTls:node.insecureTls,remark:node.remark,enabled:node.enabled,isDefault:node.isDefault});
 const save=async()=>{
  if(!edit)return;
  if(!edit.nodeName.trim()||!edit.nodeCode.trim()||!edit.networkCode||!/^https?:\/\//i.test(edit.baseUrl.trim())){message.warning('请填写节点名称、编码、所属网络与 HTTP(S) 地址');return;}
  setSaving(true);
  try{await integrationNodesApi.save({...edit,nodeName:edit.nodeName.trim(),nodeCode:edit.nodeCode.trim(),baseUrl:edit.baseUrl.trim()});setEdit(undefined);message.success('NiFi 节点已保存');refresh();}
  catch(cause){message.error(cause instanceof Error?cause.message:'保存节点失败');}
  finally{setSaving(false);}
 };
 const health=async(node:IntegrationNode)=>{
  setTesting(node.tid);
  try{const result=await integrationNodesApi.health(node);if(result.success)message.success(`节点连通正常${result.latencyMs==null?'':`（${result.latencyMs} ms）`}`);else message.error(result.message||'节点检测失败');}
  catch(cause){message.error(cause instanceof Error?cause.message:'节点检测失败');}
  finally{setTesting(undefined);}
 };
 const remove=(node:IntegrationNode)=>{
  if(node.isDefault){message.warning('默认节点不能删除，请先改设其他节点');return;}
  modal.confirm({title:'删除 NiFi 数据接入节点',content:`确认删除“${node.nodeName}”吗？已关联任务不会自动删除。`,okText:'删除',okButtonProps:{danger:true},onOk:async()=>{await integrationNodesApi.remove(node.tid);setDetail(undefined);message.success('节点已删除');refresh();}});
 };
 return <div className="ht-page"><PageHero kicker="任务运维" title="集群监控" description="登记和维护 NiFi 接入节点，检查连通状态与系统资源诊断，为流程部署选择可用节点。" kind="clusters" tags={['节点健康','网络区域','连接检测']} primaryAction={<Button type="primary" icon={<Icon name="plus" size={17}/>} onClick={()=>setEdit({...blank(),networkCode:networks[0]?.value||''})}>登记节点</Button>}/>
  <InsightStrip items={[{label:'已登记节点',value:nodes.length,icon:'clusters',hint:'当前登录租户的数据接入节点'},{label:'已启用节点',value:nodes.filter(node=>node.enabled).length,icon:'check',hint:'可被接入流程选用',tone:'cyan'},{label:'默认节点',value:nodes.filter(node=>node.isDefault).length,icon:'tasks',hint:'各网络默认接入节点',tone:'amber'}]}/>
  {query.isError&&<Alert type="error" showIcon message="NiFi 节点读取失败" description={query.error instanceof Error?query.error.message:'请检查数据中台会话与节点接口'} action={<Button size="small" onClick={refresh}>重试</Button>} style={{marginBottom:16}}/>}
  <div className="ht-toolbar"><span className="helper">节点由数据中台统一维护；打开节点详情可读取 NiFi 实际 JVM 与系统诊断。负载平均值不是 CPU 使用率。</span><span className="toolbar-spacer"/><Button onClick={refresh} loading={query.isFetching}>刷新</Button></div>
  <div className="cluster-grid">{nodes.map(node=><Panel key={node.tid} className="cluster-card"><div className="cluster-card-top"><span className="cluster-emblem"><Icon name="clusters" size={30}/></span><div><h2>{node.nodeName}</h2><p>{node.networkName||node.networkCode}</p></div><Tag color={node.enabled?'success':'default'}>{node.enabled?'已启用':'已停用'}</Tag></div><div className="cluster-number-row"><div><b>{node.isDefault?'是':'否'}</b><span>网络默认</span></div><div><b>{node.rootProcessGroupId?'已配置':'默认'}</b><span>根流程组</span></div><div><b>{node.authUsername?'已配置':'默认'}</b><span>NiFi 认证</span></div></div><p className="helper" title={node.baseUrl}>{node.baseUrl}</p><div className="card-footer"><Button onClick={()=>setDetail(node)}>节点详情</Button><Button loading={testing===node.tid} onClick={()=>void health(node)}>健康检测</Button><Button type="link" onClick={()=>editNode(node)}>配置</Button></div></Panel>)}</div>
  {!query.isLoading&&!query.isError&&!nodes.length&&<Panel><p className="helper">当前租户尚未登记 NiFi 节点，可点击“登记节点”创建。</p></Panel>}
  <Drawer title={detail?.nodeName||'节点详情'} open={!!detail} onClose={()=>setDetail(undefined)} width={640}>{detail&&<div className="ht-detail-list"><p><b>节点编码：</b>{detail.nodeCode}</p><p><b>所属网络：</b>{detail.networkName||detail.networkCode}</p><p><b>发布地址：</b>{detail.baseUrl}</p><p><b>根流程组：</b>{detail.rootProcessGroupId||'NiFi 默认根流程组'}</p><p><b>认证用户名：</b>{detail.authUsername||'未设置'}</p><p><b>状态：</b>{detail.enabled?'已启用':'已停用'}</p>
   <h3>NiFi 系统诊断</h3>
   {diagnostics.isLoading?<p className="helper">正在读取诊断快照…</p>:diagnostics.isError?<Alert type="warning" showIcon message="诊断读取失败" description={diagnostics.error instanceof Error?diagnostics.error.message:'请检查节点权限与连接'}/>:diagnostics.data?.success?<div><p><b>JVM 堆使用率：</b>{diagnostics.data.heapUtilization||'—'}</p><p><b>JVM 堆已用 / 上限：</b>{bytes(diagnostics.data.usedHeapBytes)} / {bytes(diagnostics.data.maxHeapBytes)}</p><p><b>处理器负载均值：</b>{diagnostics.data.processorLoadAverage??'—'}（可用处理器 {diagnostics.data.availableProcessors??'—'}）</p><p><b>JVM 线程数：</b>{diagnostics.data.totalThreads??'—'}</p><p><b>快照时间：</b>{diagnostics.data.statsLastRefreshed||'—'}</p></div>:<Alert type="warning" showIcon message="NiFi 未提供系统诊断" description={diagnostics.data?.message||'请检查 NiFi 的 /system 读取权限'}/ >}
   <Space><Button onClick={()=>void health(detail)} loading={testing===detail.tid}>健康检测</Button><Button onClick={()=>void diagnostics.refetch()} loading={diagnostics.isFetching}>刷新诊断</Button><Button type="primary" onClick={()=>{editNode(detail);setDetail(undefined);}}>编辑配置</Button><Button danger onClick={()=>remove(detail)}>删除节点</Button></Space></div>}</Drawer>
  <Modal title={edit?.tid?'编辑 NiFi 节点':'登记 NiFi 节点'} open={!!edit} onCancel={()=>!saving&&setEdit(undefined)} onOk={()=>void save()} okText="保存" confirmLoading={saving} destroyOnClose>
   {edit&&<Form layout="vertical"><Form.Item label="节点名称" required><Input value={edit.nodeName} onChange={event=>setEdit({...edit,nodeName:event.target.value})}/></Form.Item><Form.Item label="节点编码" required><Input value={edit.nodeCode} onChange={event=>setEdit({...edit,nodeCode:event.target.value})}/></Form.Item><Form.Item label="所属网络" required><Select value={edit.networkCode||undefined} options={networks.map(network=>({value:network.value,label:network.label}))} onChange={value=>setEdit({...edit,networkCode:value})}/></Form.Item><Form.Item label="NiFi 发布地址" required><Input value={edit.baseUrl} placeholder="https://nifi.example.com:8443" onChange={event=>setEdit({...edit,baseUrl:event.target.value})}/></Form.Item><Form.Item label="根流程组 ID"><Input value={edit.rootProcessGroupId} onChange={event=>setEdit({...edit,rootProcessGroupId:event.target.value})}/></Form.Item><Form.Item label="认证用户名"><Input value={edit.authUsername} autoComplete="off" onChange={event=>setEdit({...edit,authUsername:event.target.value})}/></Form.Item><Form.Item label="认证密码"><Input.Password value={edit.authPassword} autoComplete="new-password" placeholder={edit.tid?'留空表示保留现有密码':'输入 NiFi 密码'} onChange={event=>setEdit({...edit,authPassword:event.target.value})}/></Form.Item><Space size="large"><span>启用 <Switch checked={edit.enabled} onChange={value=>setEdit({...edit,enabled:value})}/></span><span>设为网络默认 <Switch checked={edit.isDefault} onChange={value=>setEdit({...edit,isDefault:value})}/></span></Space></Form>}
  </Modal>
 </div>;
}

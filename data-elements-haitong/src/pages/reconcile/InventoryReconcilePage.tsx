import { Alert, Button, Form, Input, Modal, Radio, Select, Space, Switch, Tag } from 'antd';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import PageHero from '../../components/PageHero';
import { DataTable, InsightStrip, Panel, useSafeAction } from '../../components/Common';
import Icon from '../../design/Icon';
import { integrationPlatformService } from '../../api/integrationPlatformService';
import { reconciliationLive, num, str, value, type ReconcileAccessTask, type ReconcilePolicy } from '../../api/reconciliationLive';

type CompareMode = 'SUMMARY' | 'KEY' | 'CONTENT';
type TriggerMode = 'MANUAL' | 'CRON' | 'ACCESS_EVENT';
type Rule = { sourceField:string; targetField:string; sourceDataType?:string; targetDataType?:string; compareEnabled:boolean; normalization?:string; numericTolerance?:unknown; datePrecision?:string; nullEqualsEmpty?:boolean; maskRule?:string; sortNo:number };
type Editor = { id?:string; name:string; accessTaskId:string; compareMode:CompareMode; triggerMode:TriggerMode; cronExpression:string; sourceKeyFields:string; targetKeyFields:string; sourceIncrementField:string; targetIncrementField:string; rules:Rule[] };
const field = (row:Record<string,unknown>, ...keys:string[]) => str(row,...keys);
const active = (row:ReconcilePolicy) => Number(value(row,'status','enabled') ?? 0) === 1;
const compare = (row:Record<string,unknown>):CompareMode => { const mode=field(row,'compare_mode','compareMode'); return mode==='SUMMARY'||mode==='CONTENT'?mode:'KEY'; };
const trigger = (row:Record<string,unknown>):TriggerMode => { const mode=field(row,'trigger_mode','triggerMode'); return mode==='CRON'||mode==='ACCESS_EVENT'?mode:'MANUAL'; };
const compareLabels:Record<CompareMode,string>={SUMMARY:'数量比较',KEY:'主键比对',CONTENT:'内容校验'};
const triggerLabels:Record<TriggerMode,string>={MANUAL:'手动',CRON:'定时',ACCESS_EVENT:'接入完成'};
const errorText=(error:unknown)=>error instanceof Error?error.message:String(error);
const bool=(raw:unknown)=>raw===true||raw===1||raw==='1';
const asRule=(row:Record<string,unknown>,index:number):Rule=>{
 const enabled=value(row,'compare_enabled','compareEnabled'), empty=value(row,'null_equals_empty','nullEqualsEmpty');
 return {sourceField:field(row,'source_field','sourceField'),targetField:field(row,'target_field','targetField'),sourceDataType:field(row,'source_data_type','sourceDataType')||undefined,targetDataType:field(row,'target_data_type','targetDataType')||undefined,compareEnabled:enabled===undefined||bool(enabled),normalization:field(row,'normalization')||undefined,numericTolerance:value(row,'numeric_tolerance','numericTolerance'),datePrecision:field(row,'date_precision','datePrecision')||undefined,nullEqualsEmpty:empty==null?undefined:bool(empty),maskRule:field(row,'mask_rule','maskRule')||undefined,sortNo:num(row,'sort_no','sortNo')||index};
};

export default function InventoryReconcilePage(){
 const nav=useNavigate(), {busy,perform}=useSafeAction();
 const [policies,setPolicies]=useState<ReconcilePolicy[]>([]),[tasks,setTasks]=useState<ReconcileAccessTask[]>([]);
 const [policyTotal,setPolicyTotal]=useState(0),[enabledTotal,setEnabledTotal]=useState<number|string>('—'),[runTotal,setRunTotal]=useState<number|string>('—');
 const [page,setPage]=useState(1),[loading,setLoading]=useState(true),[loadError,setLoadError]=useState(''),[edit,setEdit]=useState<Editor>();
 const load=useCallback(async(currentPage:number)=>{
  setLoading(true);setLoadError('');
  try{
   const [policiesResponse,accessTasks,summary,runsResponse]=await Promise.allSettled([reconciliationLive.policies(currentPage,20),reconciliationLive.accessTasks(),integrationPlatformService.reconciliationSummary(),reconciliationLive.runs(1,1)]);
   if(policiesResponse.status==='rejected'||accessTasks.status==='rejected'){
    const error=policiesResponse.status==='rejected'?policiesResponse.reason:accessTasks.status==='rejected'?accessTasks.reason:new Error('对账数据不可用');
    setPolicies([]);setTasks([]);setPolicyTotal(0);setEnabledTotal('—');setRunTotal('—');throw error;
   }
   setPolicies(policiesResponse.value.list);setTasks(accessTasks.value);setPolicyTotal(policiesResponse.value.total);
   setEnabledTotal(summary.status==='fulfilled'?num(summary.value,'policyEnabled','policy_enabled'):'—');
   setRunTotal(runsResponse.status==='fulfilled'?runsResponse.value.total:'—');
   const errors=[summary,runsResponse].filter(result=>result.status==='rejected').map(result=>errorText((result as PromiseRejectedResult).reason));
   if(errors.length)setLoadError(`概览统计暂不可用：${errors.join('；')}`);
  }catch(error){setLoadError(errorText(error));throw error;}finally{setLoading(false);}
 },[]);
 useEffect(()=>{void load(page).catch(()=>undefined);},[load,page]);
 const taskOptions=useMemo(()=>tasks.map(task=>({value:String(task.tid),label:`${field(task,'task_name','taskName')||task.tid} / ${field(task,'source_table_name','sourceTableName')} → ${field(task,'target_table_name','targetTableName')}`})),[tasks]);
 const selectedTask=edit?tasks.find(task=>String(task.tid)===edit.accessTaskId):undefined;
 const create=()=>{const task=tasks[0];if(!task)return;setEdit({name:'',accessTaskId:String(task.tid),compareMode:'KEY',triggerMode:'MANUAL',cronExpression:'',sourceKeyFields:field(task,'source_table_primary_key','sourceTablePrimaryKey'),targetKeyFields:field(task,'target_table_primary_key','targetTablePrimaryKey'),sourceIncrementField:field(task,'source_table_increment_key','sourceTableIncrementKey'),targetIncrementField:'',rules:[]});};
 const configure=(policy:ReconcilePolicy)=>void perform(async()=>{
  const detail=await reconciliationLive.policyDetail(policy.tid), raw=value(detail,'rules');
  setEdit({id:policy.tid,name:field(detail,'policy_name','policyName'),accessTaskId:field(detail,'access_task_id','accessTaskId'),compareMode:compare(detail),triggerMode:trigger(detail),cronExpression:field(detail,'cron_expression','cronExpression'),sourceKeyFields:field(detail,'source_key_fields','sourceKeyFields'),targetKeyFields:field(detail,'target_key_fields','targetKeyFields'),sourceIncrementField:field(detail,'source_increment_field','sourceIncrementField'),targetIncrementField:field(detail,'target_increment_field','targetIncrementField'),rules:Array.isArray(raw)?raw.map((rule,index)=>asRule(rule as Record<string,unknown>,index)):[]});
 });
 const save=()=>void perform(async()=>{
  if(!edit)return;
  if(!edit.name.trim())throw new Error('请填写策略名称');
  if(!edit.accessTaskId)throw new Error('请选择接入任务');
  if(!edit.sourceKeyFields.trim()||!edit.targetKeyFields.trim())throw new Error('来源表和目标表必须配置稳定的对账主键');
  if(edit.triggerMode==='CRON'&&!edit.cronExpression.trim())throw new Error('定时策略必须配置 Cron 表达式');
  await reconciliationLive.savePolicy({...edit,...(edit.id?{tid:edit.id,rules:edit.rules}:{rules:[]}),policyName:edit.name.trim(),cronExpression:edit.triggerMode==='CRON'?edit.cronExpression.trim():'',sourceKeyFields:edit.sourceKeyFields.trim(),targetKeyFields:edit.targetKeyFields.trim(),sourceIncrementField:edit.sourceIncrementField.trim(),targetIncrementField:edit.targetIncrementField.trim()});
  setEdit(undefined);await load(page);
 },'策略已保存，请预检后启用');
 const changeEnabled=(policy:ReconcilePolicy,enabled:boolean)=>void perform(async()=>{
  if(enabled){const result=await reconciliationLive.precheck(policy.tid),passed=value(result,'success','passed');if(passed===false||passed===0||passed==='false')throw new Error(field(result,'message')||'策略预检未通过');}
  await reconciliationLive.enable(policy.tid,enabled);await load(page);
 },enabled?'预检通过，策略已启用':'策略已停用');
 const run=(policy:ReconcilePolicy)=>void perform(async()=>{await reconciliationLive.manualRun(policy.tid);await load(page);nav('/reconcile/statements');},'已提交盘点任务');
 return <div className="ht-page">
  <PageHero kicker="数据对账" title="盘点对账" description="按约定时间窗口、主键和比较规则执行盘点，核查数据是否送全、送准，并为差异分析提供可回放证据。" kind="inventory" tags={['数量核对','主键比对','内容校验']} primaryAction={<Button type="primary" icon={<Icon name="plus" size={17}/>} disabled={!tasks.length||loading} onClick={create}>新建对账策略</Button>}/>
  <InsightStrip items={[{label:'盘点策略',value:policyTotal,icon:'inventory',hint:'保留策略修订与启停状态'},{label:'启用中的策略',value:enabledTotal,icon:'check',hint:'支持直接执行当前策略',tone:'cyan'},{label:'盘点执行记录',value:runTotal,icon:'statements',hint:'可查看执行结果与差异明细'}]}/>
  <Panel title="盘点策略">
   {loadError&&<Alert type="error" showIcon message="对账数据接口异常" description={loadError} action={<Button size="small" onClick={()=>void load(page).catch(()=>undefined)}>重试</Button>} style={{marginBottom:16}}/>}
   {!loading&&!loadError&&!tasks.length&&<Alert type="info" showIcon message="暂无可配置的接入任务" description="请先在数据接入中完成来源表、目标表与主键配置。" style={{marginBottom:16}}/>}
   <DataTable<ReconcilePolicy> rowKey="tid" loading={loading} dataSource={policies} pagination={{current:page,pageSize:20,total:policyTotal,showSizeChanger:false,showTotal:total=>`共 ${total} 条记录`}} onChange={pagination=>setPage(pagination.current||1)} columns={[
    {title:'策略',width:236,render:(_,policy)=><div className="entity-name"><b>{field(policy,'policy_name','policyName')}</b><small>修订 v{num(policy,'version')||1} · {field(policy,'source_key_fields','sourceKeyFields')||'未配置主键'}</small></div>},
    {title:'来源 / 目标',width:255,render:(_,policy)=><div className="entity-name"><span>{field(policy,'source_table_name','sourceTableName')||'—'}</span><small>→ {field(policy,'target_table_name','targetTableName')||'—'}</small></div>},
    {title:'比较方式',render:(_,policy)=><Tag color="blue">{compareLabels[compare(policy)]}</Tag>},
    {title:'频次约定',render:(_,policy)=>triggerLabels[trigger(policy)]},
    {title:'启用',render:(_,policy)=><Switch size="small" checked={active(policy)} loading={busy} onChange={enabled=>changeEnabled(policy,enabled)}/>},
    {title:'操作',fixed:'right',width:170,render:(_,policy)=><Space size={0}><Button type="link" disabled={busy} onClick={()=>configure(policy)}>配置</Button><Button type="link" disabled={!active(policy)||busy} onClick={()=>run(policy)}>立即执行</Button></Space>}
   ]}/>
   <p className="helper">策略关联已有接入任务；启用前需通过源端、目标端及字段预检。定时和接入完成触发由数据中台调度。</p>
  </Panel>
  {edit&&<Modal title="盘点策略配置" open width={860} maskClosable={false} onCancel={()=>setEdit(undefined)} okText="保存策略" confirmLoading={busy} onOk={save}>
   <Form layout="vertical"><div className="form-grid">
    <Form.Item label="策略名称" required><Input value={edit.name} maxLength={80} onChange={event=>setEdit({...edit,name:event.target.value})}/></Form.Item>
    <Form.Item label="频次约定"><Select value={edit.triggerMode} onChange={(mode:TriggerMode)=>setEdit({...edit,triggerMode:mode})} options={[{value:'MANUAL',label:'手动'},{value:'CRON',label:'定时（Cron）'},{value:'ACCESS_EVENT',label:'接入完成'}]}/></Form.Item>
    <Form.Item label="接入任务 / 来源表" required><Select showSearch optionFilterProp="label" value={edit.accessTaskId} options={taskOptions} onChange={id=>{const task=tasks.find(item=>String(item.tid)===id);if(task)setEdit({...edit,accessTaskId:id,sourceKeyFields:field(task,'source_table_primary_key','sourceTablePrimaryKey'),targetKeyFields:field(task,'target_table_primary_key','targetTablePrimaryKey'),sourceIncrementField:field(task,'source_table_increment_key','sourceTableIncrementKey'),targetIncrementField:'',rules:[]});}}/></Form.Item>
    <Form.Item label="目标表"><Input value={selectedTask?field(selectedTask,'target_table_name','targetTableName'):''} readOnly/></Form.Item>
    <Form.Item label="来源表主键（逗号分隔）" required><Input value={edit.sourceKeyFields} onChange={event=>setEdit({...edit,sourceKeyFields:event.target.value})}/></Form.Item>
    <Form.Item label="目标表主键（逗号分隔）" required><Input value={edit.targetKeyFields} onChange={event=>setEdit({...edit,targetKeyFields:event.target.value})}/></Form.Item>
    <Form.Item label="来源增量时间字段"><Input value={edit.sourceIncrementField} onChange={event=>setEdit({...edit,sourceIncrementField:event.target.value})} placeholder="不填则比较全部记录"/></Form.Item>
    <Form.Item label="目标增量时间字段"><Input value={edit.targetIncrementField} onChange={event=>setEdit({...edit,targetIncrementField:event.target.value})} placeholder="与来源窗口对应"/></Form.Item>
   </div>
   {edit.triggerMode==='CRON'&&<Form.Item label="Cron 表达式" required><Input value={edit.cronExpression} onChange={event=>setEdit({...edit,cronExpression:event.target.value})} placeholder="例如 0 0 2 * * ?"/></Form.Item>}
   <Form.Item label="对账方式"><Radio.Group value={edit.compareMode} onChange={event=>setEdit({...edit,compareMode:event.target.value as CompareMode})} options={[{value:'SUMMARY',label:'数量比较'},{value:'KEY',label:'主键比对'},{value:'CONTENT',label:'内容校验'}]}/></Form.Item>
   <Form.Item label="内容比较字段"><Select mode="multiple" value={edit.rules.filter(rule=>rule.compareEnabled).map(rule=>rule.sourceField)} disabled={edit.compareMode!=='CONTENT'||!edit.rules.length} placeholder={edit.rules.length?'选择参与内容比对的字段':'保存后自动带入接入任务的字段映射'} options={edit.rules.map(rule=>({value:rule.sourceField,label:`${rule.sourceField} → ${rule.targetField}`}))} onChange={(names:string[])=>setEdit({...edit,rules:edit.rules.map(rule=>({...rule,compareEnabled:names.includes(rule.sourceField)}))})}/></Form.Item>
   <Alert type="info" showIcon message="策略使用当前接入任务的来源表、目标表及字段映射。" description="新策略保存时自动带入字段规则；修改已有策略会重新要求预检。内容校验使用后端规范化规则。"/>
   </Form>
  </Modal>}
 </div>;
}

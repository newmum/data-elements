import { useState } from 'react';
import { Alert, App, Button, Drawer, Form, Input, Modal, Select, Skeleton, Space, Tabs, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { Row } from '../../services/api';
import { qualityApi, qualityDimensions, qualityParameterObject, qualitySqlDefinition, dimensionLabel, canMaintainQuality } from '../../services/quality';
import { useWorkspaceQuery } from '../../services/useWorkspaceQuery';
import { useServerSort } from '../../shared/serverSort';
import { DataTable, ErrorNotice, Page, date, required, useAction } from './common';
import '../../styles/shared-quality.css';

const examples=[
 {label:'完整性 · 空值',dimension:'COMPLETENESS',sqlTemplate:'${column} IS NULL',values:{}},
 {label:'一致性 · 两列不一致',dimension:'CONSISTENCY',sqlTemplate:'${column} <> ${relatedColumn}',values:{}},
 {label:'准确性 · 业务数值范围',dimension:'ACCURACY',sqlTemplate:'${column} < #{min} OR ${column} > #{max}',values:{min:0,max:100}},
 {label:'规范性 · 文本含首尾空格',dimension:'VALIDITY',sqlTemplate:'${column} <> TRIM(${column})',values:{}},
];
const dimensionOptions=qualityDimensions.map(value=>({value,label:dimensionLabel(value)}));

function TemplateTable({onSelect,maintain=false}:{onSelect?:(row:Row)=>void;maintain?:boolean}) {
 const [query,setQuery]=useState({page:1,size:20,keyword:'',dimension:''}),[editing,setEditing]=useState<Row>(),[form]=Form.useForm();
 const sorting=useServerSort(()=>setQuery(v=>({...v,page:1}))),request={...query,...sorting.sort};
 const q=useWorkspaceQuery('quality-sql-templates:'+JSON.stringify(request),signal=>qualityApi.sqlTemplatePage(request,signal));
 const {message,modal}=App.useApp(),{busy,run}=useAction();
 const open=(row?:Row)=>{
  try {const definition=row?qualitySqlDefinition(row):examples[0];const draft={id:row?.id,ruleName:row?.rule_name||'',ruleCode:row?.rule_code||'',dimension:row?.check_dimension||'COMPLETENESS',sqlTemplate:definition.sqlTemplate,valuesText:JSON.stringify(definition.values,null,2),description:row?.rule_description||''};form.resetFields();form.setFieldsValue(draft);setEditing(draft);}
  catch(error){message.error((error as Error).message);}
 };
 const save=async()=>{
  let values:Row;try {values=await form.validateFields();values.values=qualityParameterObject(values.valuesText);}catch(error){if(error instanceof Error)message.error(error.message);return;}
  await run(async()=>{await qualityApi.saveSqlTemplate({...editing,...values});setEditing(undefined);message.success('SQL模板已保存，可在质检任务中选择');},'');
 };
 const remove=(row:Row)=>modal.confirm({title:'删除SQL模板？',content:'后续不能再选择此模板。已有任务和报告继续保留保存时的快照；重新配置任务时需选择现用模板。',okText:'删除模板',okButtonProps:{danger:true},onOk:async()=>{await qualityApi.deleteSqlTemplate(row.id);message.success('模板已删除，历史任务与报告保留');}});
 return <>
  <DataTable titleKey="quality-sql-templates" rows={q.data?.list||[]} loading={q.loading} error={q.error} onRefresh={q.refresh}
   serverPagination={{page:query.page,size:query.size,total:q.data?.total||0,onChange:(page:number,size:number)=>setQuery(v=>({...v,page,size}))}}
   serverSort={sorting} onSearch={keyword=>setQuery(v=>({...v,page:1,keyword}))} showExport={false}
   searchPlaceholder="搜索SQL模板名称或编码；Enter 查询"
   actions={<Space><Select aria-label="模板质量维度" style={{width:135}} value={query.dimension} options={[{value:'',label:'全部维度'},...dimensionOptions]} onChange={dimension=>setQuery(v=>({...v,page:1,dimension}))}/>{maintain&&<Button type="primary" icon={<PlusOutlined/>} disabled={!canMaintainQuality()} onClick={()=>open()}>新建SQL模板</Button>}</Space>}
   columns={[
    {title:'模板名称',dataIndex:'rule_name',width:210,render:(_,r)=><div className="wx-cell-stack"><strong>{r.rule_name}</strong><small>{r.rule_code}</small></div>},
    {title:'质量维度',dataIndex:'check_dimension',width:110,render:dimensionLabel},
    {title:'模板说明',dataIndex:'rule_description',width:240,ellipsis:true},
    {title:'更新时间',dataIndex:'updated_time',width:160,render:date},
    {title:'操作',width:maintain?180:90,render:(_,r)=><Space>{onSelect&&<Button type="link" onClick={()=>onSelect(r)}>选择</Button>}{maintain&&<><Button type="link" disabled={busy||!canMaintainQuality()} onClick={()=>open(r)}>编辑</Button><Button danger type="link" disabled={busy||!canMaintainQuality()} onClick={()=>remove(r)}>删除</Button></>}</Space>},
   ]}/>
  <Drawer rootClassName="wx-editor-drawer wx-quality-editor" open={!!editing} size={740} title={editing?.id?'编辑SQL质量模板':'新建SQL质量模板'} onClose={()=>{if(!busy)setEditing(undefined);}} destroyOnHidden mask={{closable:false}}
   footer={<div className="editor-footer"><span>保存模板后，在质检任务中选择并预检</span><Space><Button disabled={busy} onClick={()=>setEditing(undefined)}>取消</Button><Button type="primary" loading={busy} onClick={()=>void save()}>保存模板</Button></Space></div>}>
   <Alert className="mb16" showIcon type="info" title="填写触发异常的SQL条件" description="条件为真时计为异常；NULL比较结果不为真，必填字段请同时配置完整性规则。单列或两列条件自动作用于任务已选择的表。联合唯一、跨表引用请使用对应内置模板。"/>
   <Form form={form} layout="vertical" className="wx-editor-form">
    <Form.Item name="ruleName" label="模板名称" rules={required}><Input maxLength={255}/></Form.Item>
    <Form.Item name="ruleCode" label="模板编码" rules={required}><Input maxLength={255}/></Form.Item>
    <Form.Item name="dimension" label="质量维度" rules={required}><Select options={dimensionOptions}/></Form.Item>
    <Form.Item label="常见SQL示例"><Select placeholder="选择示例后可继续修改" options={examples.map((x,index)=>({value:index,label:x.label}))} onChange={index=>{const example=examples[index];form.setFieldsValue({dimension:example.dimension,sqlTemplate:example.sqlTemplate,valuesText:JSON.stringify(example.values,null,2)});}}/></Form.Item>
    <Form.Item name="sqlTemplate" label="SQL异常条件" rules={required} extra={'字段：${column}、${relatedColumn}；值参数：#{名称}。支持比较、算术、AND/OR、IN、BETWEEN、CASE，以及COALESCE / NULLIF / TRIM / UPPER / LOWER / ABS / ROUND。'}><Input.TextArea className="wx-quality-sql-input" maxLength={4000} rows={6}/></Form.Item>
    <Form.Item name="valuesText" label="默认参数（JSON对象）" rules={[{validator:async(_rule,value)=>{qualityParameterObject(value);}}]}><Input.TextArea rows={4} maxLength={4000}/></Form.Item>
    <Form.Item name="description" label="适用场景与说明"><Input.TextArea rows={3} maxLength={1000}/></Form.Item>
   </Form>
  </Drawer>
 </>;
}

/** 仅在用户点击选择时挂载，配置多条规则不会各自补查模板列表。 */
export function QualityTemplatePicker({onSelect,onClose}:{onSelect:(row:Row)=>void;onClose:()=>void}) {
 return <Modal open width={1000} title="选择SQL质量模板" footer={null} onCancel={onClose} destroyOnHidden><TemplateTable onSelect={onSelect}/></Modal>;
}

export function QualityTemplatesPage() {
 const nav=useNavigate(),q=useWorkspaceQuery('quality-builtin-templates',qualityApi.templates);
 return <Page title="数据质量规则" description="常见质量模板与可配置SQL模板，复用于真实质检任务。" actions={<Button onClick={()=>nav('/governance/quality/plans?create=1')}>创建质检任务</Button>}>
  <Tabs items={[
   {key:'builtin',label:'内置模板',children:<><ErrorNotice error={q.error} retry={q.refresh}/><Skeleton active loading={q.loading}><div className="wx-quality-template-grid">{(q.data||[]).filter(r=>r.ruleType!=='SQL_TEMPLATE').map(r=><article className="wx-quality-rule-card" key={r.ruleType}><header><b>{r.ruleName}</b><Tag>{dimensionLabel(r.dimension)}</Tag></header><p>{r.description}</p><Button onClick={()=>nav('/governance/quality/plans?create=1')}>配置质检任务</Button></article>)}</div></Skeleton></>},
   {key:'sql',label:'SQL模板',children:<TemplateTable maintain/>},
  ]}/>
 </Page>;
}

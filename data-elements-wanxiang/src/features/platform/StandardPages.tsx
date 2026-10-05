import { useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Descriptions, Drawer, Form, Input, InputNumber, Modal, Segmented, Select, Space, Table, Tooltip } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import type { Row } from '../../services/api';
import { useWorkspaceQuery } from '../../services/useWorkspaceQuery';
import { dataTypeLabel } from '../../shared/businessLabels';
import { Panel } from '../../design/Visuals';
import { canMaintainStandards, deleteCode, deleteElement, elementForm, fieldTypes, listCodes, listElements, loadLanding, readCode, readElement, saveBinding, saveCode, saveElement, saveEncoding, segmentsFromPattern, setCodePublished, setElementPublished, standardChoices, testEncoding, type StandardChoices } from '../../services/standards';
import { DataTable, EditorModal, ErrorNotice, JsonDetail, Page, Status, opts, required, statusColumn, text, timeColumn, useAction } from './common';

const emptyChoices:StandardChoices={classes:[],codes:[],types:[],rules:[]};
const published=(row:Row)=>row.status==='PUBLISHED';
const unavailable=()=> <Tooltip title="原共享接口未提供此字段"><span className="muted">未提供</span></Tooltip>;

function StandardFields({choices,initial}:{choices:StandardChoices;initial?:Row|null}) {
 const form=Form.useFormInstance(),type=Form.useWatch('fieldType',form);
 const types=[...choices.types];
 for(const value of [initial?.dataTypeId,'varchar'])if(value&&!types.some(t=>t.value===value))types.push({value,label:value});
 return <>
  <Form.Item name="name" label="名称" rules={required}><Input maxLength={50}/></Form.Item>
  <Form.Item name="code" label="英文代码" rules={required}><Input maxLength={50}/></Form.Item>
  <Form.Item name="standardEncode" label="标准编码" rules={required}><Input maxLength={50}/></Form.Item>
  <Form.Item name="definition" label="业务定义"><Input.TextArea rows={3} maxLength={255} showCount/></Form.Item>
  <div className="wx-standard-form-grid">
   <Form.Item name="dataTypeId" label="数据类型" rules={required}><Select showSearch options={types}/></Form.Item>
   <Form.Item name="fieldType" label="规范字段类型" rules={required}><Select options={[...new Set([...fieldTypes,initial?.fieldType].filter(Boolean))].map(value=>({value,label:value.toUpperCase()}))}/></Form.Item>
   <Form.Item name="fieldLength" label="规范长度" rules={required}><InputNumber min={1} max={65535} precision={0}/></Form.Item>
   <Form.Item name="isNullable" label="可为空值" rules={required}><Select options={[{value:1,label:'是'},{value:0,label:'否'}]}/></Form.Item>
   {type==='decimal'&&<><Form.Item name="numericPrecision" label="数值精度"><InputNumber min={1} max={65} precision={0}/></Form.Item><Form.Item name="numericScale" label="小数位"><InputNumber min={0} max={30} precision={0}/></Form.Item></>}
   <Form.Item name="dataLevel" label="数据分级" rules={required}><Select options={[{value:'1',label:'完全公开（L1）'},{value:'2',label:'对内公开（L2）'},{value:'3',label:'私密（L3）'},{value:'4',label:'机密（L4）'}]}/></Form.Item>
   <Form.Item name="dataCategoryId" label="标准分类"><Select showSearch allowClear optionFilterProp="label" options={opts(choices.classes)}/></Form.Item>
  </div>
  <Form.Item name="valueDomainId" label="值域（标准代码集）"><Select allowClear showSearch optionFilterProp="label" options={opts(choices.codes)}/></Form.Item>
  <Form.Item name="standardCodeSet" label="关联标准代码集编码"><Input maxLength={100}/></Form.Item>
  <Form.Item name="formatPattern" label="格式规则"><Input maxLength={500} placeholder="可填写正则表达式；编码标准与此字段共用"/></Form.Item>
  <Form.Item name="exampleValue" label="示例值"><Input maxLength={500}/></Form.Item>
  <Form.Item name="qualityRule" label="关联质检规则"><Select mode="multiple" allowClear showSearch optionFilterProp="label" options={choices.rules}/></Form.Item>
  <Form.Item name="customRule" label="自定义规则说明"><Input.TextArea rows={3} maxLength={500} placeholder="沿用原接口规则内容，不在浏览器中执行脚本"/></Form.Item>
 </>;
}
function ElementInformation({row}:{row:Row}) {
 return <Descriptions bordered size="small" column={1} items={[
  {key:'name',label:'名称',children:row.name},{key:'code',label:'英文代码',children:row.code},
  {key:'standard',label:'标准编码',children:text(row.standardEncode)},{key:'definition',label:'业务定义',children:text(row.definition)},
  {key:'type',label:'类型 / 长度',children:`${row.dataTypeId||row.type||'—'} · ${row.fieldType||'—'} (${row.fieldLength??'—'})`},
  {key:'decimal',label:'精度 / 小数位',children:`${text(row.numericPrecision)} / ${text(row.numericScale)}`},
  {key:'class',label:'标准分类',children:text(row.dataCategoryName)},{key:'nullable',label:'可为空值',children:row.isNullable==null?'—':Number(row.isNullable)===1?'是':'否'},
  {key:'level',label:'数据分级',children:row.dataLevel?`L${row.dataLevel}`:'—'},{key:'domain',label:'值域标识',children:text(row.valueDomainId)},
  {key:'codes',label:'关联代码集',children:text(row.standardCodeSet)},{key:'pattern',label:'格式规则',children:text(row.formatPattern)},
  {key:'example',label:'示例值',children:text(row.exampleValue)},{key:'quality',label:'关联质检规则',children:text(row.qualityRule)},
  {key:'custom',label:'自定义规则说明',children:text(row.customRule)},{key:'status',label:'发布状态',children:<Status value={row.status}/>},
 ]}/>;
}

export function ElementsPage() {
 const q=useWorkspaceQuery('shared-elements',listElements);
 const choices=useWorkspaceQuery('shared-element-options',standardChoices);
 const [edit,setEdit]=useState<Row|null>(),[detail,setDetail]=useState<Row|null>(null),[category,setCategory]=useState<string>(),[state,setState]=useState<string>();
 const {busy,run}=useAction(),{modal,message}=App.useApp(),navigate=useNavigate();
 const confirm=(row:Row,remove=false)=>modal.confirm({title:remove?'删除未发布数据元？':'下线数据元？',content:remove?`删除“${row.name}”的标准定义，不删除源数据库字段。`:`“${row.name}”下线后可修改，保存后需重新审批发布；已有字段引用会标记为未发布。`,okText:remove?'确认删除':'确认下线',okButtonProps:{danger:remove},onOk:async()=>{try{remove?await deleteElement(row):await setElementPublished(row,false);message.success(remove?'数据元已删除':'已下线，可修改后重新审批');}catch(e){message.error(e instanceof Error?e.message:String(e));throw e;}}});
 return <Page title="数据元" description="保存后进入待审批列表，审批发布后生效；已发布内容先下线再修改。" actions={canMaintainStandards()&&<Button type="primary" icon={<PlusOutlined/>} onClick={()=>setEdit(null)}>新建数据元</Button>}>
  <DataTable rows={(q.data||[]).filter(r=>(!category||r.dataCategoryId===category)&&(!state||r.status===state))} loading={q.loading} error={q.error} onRefresh={q.refresh} actions={<Space wrap><Select aria-label="标准分类筛选" allowClear placeholder="全部分类" style={{width:150}} options={opts(choices.data?.classes||[])} value={category} onChange={setCategory}/><Select aria-label="发布状态筛选" allowClear placeholder="全部状态" style={{width:120}} options={[{value:'PENDING',label:'待审批'},{value:'PUBLISHED',label:'已发布'}]} value={state} onChange={setState}/></Space>} columns={[
   {title:'名称',dataIndex:'name',width:180,render:(v,r)=><Button type="link" onClick={()=>run(async()=>setDetail(await readElement(r.id)),'')}>{v}</Button>},
   {title:'英文代码',dataIndex:'code',width:170},{title:'标准编码',dataIndex:'standardEncode',width:140,render:text},
   {title:'定义',dataIndex:'definition',width:240,ellipsis:true},{title:'数据类型',dataIndex:'type',width:145,render:v=><div className="wx-cell-stack"><span>{dataTypeLabel(v)}</span><small>{v}</small></div>},
   {title:'长度',dataIndex:'length',width:90,render:text},{title:'分类',dataIndex:'dataCategoryName',width:160,render:text},
   {title:'当前修订',dataIndex:'versionNo',width:120,render:v=><Tooltip title="按原记录版本号显示；原保存接口不维护独立修订，审批不使用此字段"><span>{v?text(v):'未提供'}</span></Tooltip>},statusColumn,timeColumn,
   {title:'操作',width:148,fixed:'right',render:(_,r)=><Space>{published(r)?<Button type="link" disabled={!canMaintainStandards()||busy} onClick={()=>confirm(r)}>下线</Button>:<><Button type="link" disabled={!canMaintainStandards()||busy} onClick={()=>run(async()=>setEdit(await readElement(r.id)),'')}>编辑</Button><Button type="link" onClick={()=>navigate('/governance/standards/review')}>待审批</Button><Button danger type="link" disabled={!canMaintainStandards()||busy} onClick={()=>confirm(r,true)}>删除</Button></>}</Space>},
  ]}/>
  <EditorModal open={edit!==undefined} title={edit?'编辑数据元':'新建数据元'} presentation="drawer" initial={elementForm(edit)} onCancel={()=>setEdit(undefined)} saveMessage="数据元已保存，自动进入待审批列表" storageHint="保存到当前租户的共享标准库" onSave={v=>{if(!choices.data||choices.error)throw new Error('请先成功读取表单选项');return saveElement(v,choices.data.classes,edit);}}>
   <ErrorNotice error={choices.error} retry={choices.refresh}/><StandardFields choices={choices.data||emptyChoices} initial={edit}/>
  </EditorModal>
  <Drawer open={!!detail} title="数据元详情" width={620} onClose={()=>setDetail(null)}>{detail&&<ElementInformation row={detail}/>}</Drawer>
 </Page>;
}
export function ReviewPage() {
 const q=useWorkspaceQuery('shared-standard-review',listElements),[item,setItem]=useState<Row|null>(null);const {busy,run}=useAction();
 return <Page title="数据元审核" description="已保存但未发布的数据元自动进入本列表；核对内容后审批并发布。">
  <DataTable rows={(q.data||[]).filter(r=>!published(r))} loading={q.loading} error={q.error} onRefresh={q.refresh} columns={[
   {title:'数据元 / 标准编码',width:240,render:(_,r)=><div className="wx-cell-stack"><strong>{r.name}</strong><small>{r.standardEncode||'未填写标准编码'}</small></div>},
   {title:'英文代码',dataIndex:'code',width:170},{title:'业务定义',dataIndex:'definition',width:260,ellipsis:true},{title:'标准分类',dataIndex:'dataCategoryName',width:160,render:text},
   {title:'数据类型',width:145,render:(_,r)=><div className="wx-cell-stack"><span>{dataTypeLabel(r.type)}</span><small>{r.type} ({r.fieldLength??'—'})</small></div>},
   {title:'提交修订',key:'submittedRevision',width:120,render:unavailable},{title:'提交时间',key:'submittedAt',width:160,render:unavailable},statusColumn,timeColumn,
   {title:'操作',width:116,render:(_,r)=><Button type="link" disabled={busy} onClick={()=>run(async()=>setItem(await readElement(r.id)),'')}>审批并发布</Button>},
  ]}/>
  <Modal width={660} open={!!item} title={`审批 · ${item?.name||''}`} onCancel={()=>!busy&&setItem(null)} okText="审批并发布" confirmLoading={busy} okButtonProps={{disabled:!canMaintainStandards()||!item||published(item)}} onOk={()=>run(async()=>{await setElementPublished(item!,true);setItem(null);},'审批完成，数据元已发布')}>
   {item&&<ElementInformation row={item}/>}<Alert className="mt16" showIcon type="info" title="确认内容后发布" description="只更新原有“已发布”状态。发布后必须先下线才能修改，修改保存后再次进入待审批列表。"/>
  </Modal>
 </Page>;
}

function MappingFields({entities,elements,edit,rules,bindings,landing}:{entities:Row[];elements:Row[];edit?:Row|null;rules:Row[];bindings:Row[];landing:boolean}) {
 const form=Form.useFormInstance(),entityId=Form.useWatch('entityId',form),fieldId=Form.useWatch('fieldId',form),mode=Form.useWatch('mode',form)||'STANDARD';
 const entity=entities.find(e=>e.id===entityId),field=entity?.fields.find((f:Row)=>f.id===fieldId);
 const current=elements.find(e=>e.id===field?.standardId);
 const targets=elements.filter(published);if(current&&!published(current))targets.push(current);
 return <>
  <Form.Item name="entityId" label="元数据实体" rules={required}><Select showSearch optionFilterProp="label" disabled={!!edit} options={entities.map(e=>({value:e.id,label:`${e.displayName||e.name} · ${e.sourceName||''}`}))} onChange={()=>form.setFieldsValue({fieldId:undefined,targetId:undefined,expectedStandardId:null})}/></Form.Item>
  <Form.Item name="fieldId" label="字段" rules={required}><Select showSearch optionFilterProp="label" disabled={!!edit} options={(entity?.fields||[]).map((f:Row)=>({value:f.id,label:`${f.displayName||f.name} · ${f.name}`}))} onChange={id=>{const selected=entity?.fields.find((f:Row)=>f.id===id),existing=bindings.find(b=>b.field_id===id&&b.mapping_mode===mode);form.setFieldsValue({targetId:mode==='STANDARD'?selected?.standardId||undefined:existing?.target_id,expectedStandardId:selected?.standardId||null,expectedTargetId:existing?.target_id||null});}}/></Form.Item>
  {!landing&&<Form.Item name="mode" label="对标类型"><Select disabled={!!edit?.target_id} onChange={m=>{const current=bindings.find(b=>b.field_id===fieldId&&b.mapping_mode===m);form.setFieldsValue({targetId:m==='STANDARD'?field?.standardId:current?.target_id,expectedTargetId:current?.target_id||null});}} options={[{value:'STANDARD',label:'已发布的数据元标准'},{value:'QUALITY',label:'已配置的质量规则'},{value:'REFERENCE',label:'其他技术字段参照'}]}/></Form.Item>}
  <Form.Item name="targetId" label={mode==='STANDARD'?'目标标准':mode==='QUALITY'?'质检任务规则':'参照字段'} rules={required}><Select showSearch optionFilterProp="label" options={mode==='STANDARD'?targets.map(e=>({value:e.id,label:`${e.name} · ${e.code}`,disabled:!published(e)})):mode==='QUALITY'?rules.filter(r=>r.tableId===entityId&&r.columnName===field?.name&&Number(r.enabled)===1).map(r=>({value:r.id,label:`${r.taskName} · ${r.ruleName}`})):entities.flatMap(e=>e.fields.filter((f:Row)=>f.id!==fieldId).map((f:Row)=>({value:f.id,label:`${e.displayName||e.name}.${f.displayName||f.name} · ${f.name}`})))}/></Form.Item>
  <Form.Item name="expectedStandardId" hidden><Input/></Form.Item>
  <Form.Item name="expectedTargetId" hidden><Input/></Form.Item>
 </>;
}
export function MappingPage({landing=false}:{landing?:boolean}) {
 const q=useWorkspaceQuery('shared-standard-landing',async signal=>{const [data,elements]=await Promise.all([loadLanding(signal),listElements(signal)]);return {...data,items:data.items.map(r=>({...r,id:r.field_id+':STANDARD',mapping_mode:'STANDARD'})),elements};});
 const [edit,setEdit]=useState<Row|null>(),[scope,setScope]=useState('ALL');const {modal,message}=App.useApp();
 const data=q.data,rows:Row[]=data?.items||[],entities=data?.entities||[],elements=data?.elements||[],coverage=data?.coverage||{};
 const fieldIndex=useMemo(()=>new Map<string,{entity:Row;field:Row}>(entities.flatMap(entity=>(entity.fields||[]).map((field:Row)=>[String(field.id),{entity,field}] as const))),[entities]);
 const standardIndex=useMemo(()=>new Map<string,Row>(elements.map(element=>[String(element.id),element])),[elements]);
 const info=(id:string)=>fieldIndex.get(id)||null;
 const standardFor=(id:string)=>standardIndex.get(id);
 const fieldCell=(id:string)=>{const pair=info(id);if(!pair)return <span>字段不存在或无权访问</span>;const name=`${pair.entity.displayName||pair.entity.name} · ${pair.field.displayName||pair.field.name}`,technical=`${pair.entity.name}.${pair.field.name}`;return <div className="wx-cell-stack wx-mapping-field"><Tooltip title={name}><strong>{name}</strong></Tooltip><Tooltip title={technical}><small>{technical}</small></Tooltip></div>;};
 const typeCell=(type:unknown,length:unknown,standard=false)=>{const kind=String(type??'').trim(),size=length==null||length===''?'':String(length);if(!kind)return <span className="muted">未提供</span>;const label=`${kind.toUpperCase()}${size?`(${size})`:''}`;return <Tooltip title={label}><span className={`wx-mapping-type${standard?' wx-mapping-type-standard':''}`}>{label}</span></Tooltip>;};
 const targetCell=(id:string)=>{const e=standardFor(id);if(e)return <div className="wx-cell-stack wx-mapping-standard"><Tooltip title={e.name}><strong>{e.name}</strong></Tooltip><Tooltip title={e.code}><small>{e.code}</small></Tooltip></div>;if(!id)return <span className="muted">未关联标准</span>;const legacy=/^\d+$/.test(id);return <Tooltip title={legacy?`当前租户未查到标准 ID：${id}`:`字段保存的历史标准标识为 ${id}；当前标准库按记录 ID 查询，尚未完成对应关系核对。`}><span className="wx-mapping-unresolved">{legacy?'当前租户未找到该标准':`历史引用 · ${id}`}</span></Tooltip>;};
 return <Page title={landing?'标准落地':'元数据对标'} description="查看业务字段的数据标准关联情况，并维护已发布的数据元标准。" actions={canMaintainStandards()&&<Button type="primary" onClick={()=>setEdit(null)}>关联数据标准</Button>}>
  {landing&&<div className="mb16"><Panel title="标准落地进度" description="按真实已采集字段和有效已发布标准统计；覆盖率不代表通过质检。"><div className="wx-coverage-body"><div className="wx-coverage-number"><strong>{data?Number(coverage.coverage||0).toFixed(1):'—'}<small>%</small></strong><span>标准覆盖率</span></div><div className="wx-coverage-track"><span style={{width:`${Math.min(100,Math.max(0,Number(coverage.coverage||0)))}%`}}/></div><div className="wx-coverage-split"><div><b>{data?coverage.mappedFields:'—'}</b><span>有效标准引用</span></div><div><b>{data?Number(coverage.totalFields)-Number(coverage.boundFields):'—'}</b><span>尚未绑定字段</span></div><div><b>{data?Number(coverage.boundFields)-Number(coverage.mappedFields):'—'}</b><span>引用未发布 / 不可用</span></div></div></div></Panel></div>}
  <DataTable rows={rows.filter(r=>scope==='ALL'||(scope==='BOUND'?!!r.target_id:!r.target_id))} loading={q.loading} error={q.error} onRefresh={q.refresh} actions={<Segmented aria-label="数据标准关联筛选" value={scope} onChange={value=>setScope(String(value))} options={[{value:'ALL',label:'全部'},{value:'BOUND',label:'已绑定'},{value:'UNBOUND',label:'未绑定'}]}/>} columns={[
   {title:'字段名',dataIndex:'field_id',width:220,sorter:(a,b)=>String(info(a.field_id)?.field.displayName||info(a.field_id)?.field.name||'').localeCompare(String(info(b.field_id)?.field.displayName||info(b.field_id)?.field.name||''),'zh-CN',{numeric:true}),render:fieldCell},
   {title:'原字段类型 / 长度',key:'source_type',render:(_,r)=>{const field=info(r.field_id)?.field;return typeCell(field?.type,field?.length);}},
   {title:'数据标准',dataIndex:'target_id',width:190,className:'wx-mapping-standard-column',sorter:(a,b)=>String(standardFor(a.target_id)?.name||a.target_id||'').localeCompare(String(standardFor(b.target_id)?.name||b.target_id||''),'zh-CN',{numeric:true}),render:targetCell},
   {title:'标准类型 / 长度',key:'standard_type',render:(_,r)=>{const standard=standardFor(r.target_id);return standard?typeCell(standard.fieldType??standard.type,standard.fieldLength??standard.length,true):<span className="muted">—</span>;}},...(landing?[statusColumn]:[]),
   {title:'操作',width:132,render:(_,r)=><Space className="wx-mapping-action-buttons"><Button type="link" disabled={!canMaintainStandards()} onClick={()=>setEdit(r)}>{r.target_id?'替换标准':'关联标准'}</Button>{r.target_id&&<Button danger type="link" disabled={!canMaintainStandards()} onClick={()=>modal.confirm({title:'解除数据标准关联？',content:'只解除当前字段与数据标准的关联，不删除数据标准。',onOk:async()=>{try{await saveBinding({entityId:r.entity_id,fieldId:r.field_id,mode:'STANDARD',targetId:null,expectedStandardId:r.target_id});message.success('数据标准关联已解除');}catch(e){message.error(e instanceof Error?e.message:String(e));throw e;}}})}>解除</Button>}</Space>},
  ]}/>
  <EditorModal open={edit!==undefined} title={edit?'维护数据标准关联':'关联数据标准'} initial={edit?{id:edit.id,entityId:edit.entity_id,fieldId:edit.field_id,mode:'STANDARD',targetId:standardFor(edit.target_id)?.id,expectedStandardId:edit.target_id||null}:{mode:'STANDARD'}} onCancel={()=>setEdit(undefined)} saveMessage="数据标准关联已保存" onSave={saveBinding}>
   <MappingFields entities={entities} elements={elements} rules={[]} bindings={rows} landing edit={edit}/>
  </EditorModal>
 </Page>;
}

export function CodesPage() {
 const q=useWorkspaceQuery('shared-code-sets',async signal=>{const [codes,elements]=await Promise.all([listCodes(signal),listElements(signal)]);return {codes,elements};});
 const choices=useWorkspaceQuery('shared-code-options',standardChoices);
 const [edit,setEdit]=useState<Row|null>(),[set,setSet]=useState<Row|null>(null),[items,setItems]=useState<Row[]>([]);const dirty=useRef(false);
 const {busy,run}=useAction(),{modal,message}=App.useApp();
 const references=(id:string)=>(q.data?.elements||[]).filter(e=>e.valueDomainId===id).length;
 const open=async(row:Row)=>{const current=await readCode(row.id);setItems(current.items);dirty.current=false;setSet(current);};
 const close=()=>{if(dirty.current)modal.confirm({title:'放弃未保存的代码条目？',okText:'放弃修改',cancelText:'继续编辑',onOk:()=>setSet(null)});else setSet(null);};
 const update=(next:Row[])=>{dirty.current=true;setItems(next);};
 const transition=(row:Row,remove=false)=>modal.confirm({title:remove?'删除代码集？':published(row)?'下线代码集？':'发布代码集？',content:remove?'删除前会检查数据元引用；仍被引用的代码集不能删除。':published(row)?`有 ${references(row.id)} 个数据元引用此值域。下线后可编辑，修改完成后再发布。`:'发布后可供数据元引用；修改前需先下线。',okText:remove?'确认删除':published(row)?'确认下线':'确认发布',okButtonProps:{danger:remove},onOk:async()=>{try{if(!choices.data||choices.error)throw new Error('请先成功读取分类选项');remove?await deleteCode(row):await setCodePublished(row,!published(row),choices.data.classes);message.success(remove?'代码集已删除':published(row)?'代码集已下线':'代码集已发布');}catch(e){message.error(e instanceof Error?e.message:String(e));throw e;}}});
 return <Page title="标准代码" description="维护共享值域的代码和名称；已发布代码集先下线再修改。" actions={canMaintainStandards()&&<Button type="primary" icon={<PlusOutlined/>} onClick={()=>setEdit(null)}>新建代码集</Button>}>
  <DataTable rows={q.data?.codes||[]} loading={q.loading} error={q.error} onRefresh={q.refresh} columns={[
   {title:'名称',dataIndex:'name',width:200},{title:'代码',dataIndex:'code',width:150},statusColumn,{title:'代码条目数',width:120,render:(_,r)=>r.items.length},{title:'引用数据元',width:120,render:(_,r)=>references(r.id)},
   {title:'标准分类',dataIndex:'dataCategoryName',width:160,render:text},timeColumn,
   {title:'操作',width:208,render:(_,r)=><Space><Button type="link" disabled={busy} onClick={()=>run(()=>open(r),'')}>代码条目</Button>{canMaintainStandards()&&<><Button type="link" disabled={published(r)||busy} onClick={()=>run(async()=>setEdit(await readCode(r.id)),'')}>编辑</Button><Button type="link" disabled={busy} onClick={()=>transition(r)}>{published(r)?'下线':'发布'}</Button>{!published(r)&&<Button danger type="link" disabled={busy} onClick={()=>transition(r,true)}>删除</Button>}</>}</Space>},
  ]}/>
  <EditorModal open={edit!==undefined} title={edit?'代码集信息':'新建代码集'} initial={edit||{status:'DRAFT',items:[]}} onCancel={()=>setEdit(undefined)} saveMessage="代码集已保存到共享标准库" onSave={v=>{if(!choices.data||choices.error)throw new Error('请先读取表单选项');return saveCode(v,choices.data.classes,edit);}}>
   <ErrorNotice error={choices.error} retry={choices.refresh}/><Form.Item name="name" label="名称" rules={required}><Input maxLength={255}/></Form.Item><Form.Item name="code" label="代码" rules={required}><Input maxLength={100}/></Form.Item><Form.Item name="dataCategoryId" label="标准分类"><Select allowClear showSearch optionFilterProp="label" options={opts(choices.data?.classes||[])}/></Form.Item><Form.Item name="description" label="说明"><Input.TextArea maxLength={1000}/></Form.Item>
  </EditorModal>
  <Drawer width={800} open={!!set} title={`${set?.name||''} · 代码条目`} onClose={()=>!busy&&close()} maskClosable={false} extra={canMaintainStandards()&&!published(set||{})&&<Space><Button disabled={busy} onClick={()=>update([...items,{value:'',label:'',description:''}])}>增加条目</Button><Button type="primary" loading={busy} onClick={()=>run(async()=>{if(!choices.data||choices.error)throw new Error('请先读取分类选项');const saved=await saveCode({...set,items},choices.data.classes,set);setSet(saved);setItems(saved.items);dirty.current=false;},'代码条目已保存到共享值域')}>保存条目</Button></Space>}>
   <Alert className="mb16" showIcon type="info" title={published(set||{})?'当前代码集已发布，只读；下线后可修改。':'修改后点击保存；同一代码集中的代码值不能重复。'}/>
   <Table size="small" scroll={{x:760}} rowKey={(_,i)=>String(i)} pagination={false} dataSource={items} columns={[
    ...['value','label','description'].map((key,i)=>({title:['代码值','显示名称','说明'][i],dataIndex:key,width:[160,220,280][i],render:(v:string,_r:Row,index:number)=><Input aria-label={`${['代码值','显示名称','条目说明'][i]} ${index+1}`} disabled={published(set||{})||!canMaintainStandards()||busy} maxLength={[128,255,500][i]} value={v} onChange={e=>update(items.map((r,n)=>n===index?{...r,[key]:e.target.value}:r))}/>})),
    {title:'操作',className:'wx-action-column',fixed:'right',align:'center',width:76,render:(_v,_r,i)=><Button danger type="text" aria-label={`删除条目 ${i+1}`} disabled={published(set||{})||!canMaintainStandards()||busy} icon={<DeleteOutlined/>} onClick={()=>update(items.filter((_r,n)=>n!==i))}/>},
   ]}/>
  </Drawer>
 </Page>;
}
function EncodingFields({legacy=false,choices}:{legacy?:boolean;choices:StandardChoices}) {
 return <>
  <Form.Item name="name" label="规范名称" rules={required}><Input maxLength={50}/></Form.Item><Form.Item name="code" label="英文代码" rules={required}><Input maxLength={50}/></Form.Item><Form.Item name="standardEncode" label="标准编码" rules={required}><Input maxLength={50}/></Form.Item>
  {legacy?<Form.Item name="formatPattern" label="原格式规则" rules={required} extra="该规则不能无损拆成新版的四种编码段；保留原表达式进行维护。"><Input maxLength={500}/></Form.Item>:<Form.List name={['definition','segments']}>{(fields,{add,remove})=><><p>按顺序定义每一段，不接受脚本或发号表达式。</p>{fields.map(({key,name,...rest})=><Space key={key} align="start" className="encoding-segment"><Form.Item {...rest} name={[name,'type']} rules={required}><Select aria-label={`编码段类型 ${name+1}`} style={{width:135}} options={[{value:'LITERAL',label:'固定文本'},{value:'DIGITS',label:'数字'},{value:'LETTERS',label:'字母'},{value:'ALPHANUMERIC',label:'字母数字'}]}/></Form.Item><Form.Item {...rest} name={[name,'value']}><Input aria-label={`固定文本 ${name+1}`} placeholder="固定文本"/></Form.Item><Form.Item {...rest} name={[name,'length']}><InputNumber aria-label={`编码段长度 ${name+1}`} placeholder="长度" min={1} max={128} precision={0}/></Form.Item><Button aria-label={`删除编码段 ${name+1}`} icon={<DeleteOutlined/>} onClick={()=>remove(name)}/></Space>)}<Button block type="dashed" onClick={()=>add({type:'DIGITS',length:4})}>增加编码段</Button></>}</Form.List>}
  <Form.Item name="dataCategoryId" label="标准分类"><Select allowClear showSearch optionFilterProp="label" options={opts(choices.classes)}/></Form.Item><Form.Item name="description" label="说明"><Input.TextArea maxLength={255} rows={2}/></Form.Item>
 </>;
}
export function EncodingPage() {
 const q=useWorkspaceQuery('shared-encoding-standards',listElements),choices=useWorkspaceQuery('shared-encoding-options',standardChoices);
 const [edit,setEdit]=useState<Row|null>(),[test,setTest]=useState<Row|null>(null),[value,setValue]=useState(''),[result,setResult]=useState<Row|null>(null);
 const {busy,run}=useAction(),{modal,message}=App.useApp(),navigate=useNavigate();
 const initial=edit?{...edit,description:edit.definition,definition:{segments:segmentsFromPattern(edit.formatPattern)||undefined}}:{definition:{segments:[{type:'LITERAL',value:'WX-'},{type:'DIGITS',length:6}]},dataLevel:'2',isNullable:0};
 return <Page title="编码标准" description="编码规则与共享数据元的格式规则一致；格式校验不发号，不承担号码全局唯一性。" actions={canMaintainStandards()&&<Button type="primary" icon={<PlusOutlined/>} onClick={()=>setEdit(null)}>新建编码规范</Button>}>
  <DataTable rows={(q.data||[]).filter(r=>r.formatPattern)} error={q.error} loading={q.loading} onRefresh={q.refresh} columns={[
   {title:'名称',dataIndex:'name',width:180},{title:'英文代码',dataIndex:'code',width:160},{title:'标准编码',dataIndex:'standardEncode',width:150},statusColumn,
   {title:'格式规则',dataIndex:'formatPattern',width:250,ellipsis:true},{title:'说明',dataIndex:'definition',width:220,ellipsis:true},timeColumn,
   {title:'操作',width:176,render:(_,r)=><Space>{published(r)?<Button type="link" disabled={busy} onClick={()=>modal.confirm({title:'下线编码规范？',content:'与数据元共享发布状态。下线后修改，再到数据元审核菜单审批发布。',onOk:async()=>{try{await setElementPublished(r,false);message.success('编码规范已下线');}catch(e){message.error(e instanceof Error?e.message:String(e));throw e;}}})}>下线</Button>:<><Button type="link" disabled={!canMaintainStandards()||busy} onClick={()=>run(async()=>setEdit(await readElement(r.id)),'')}>编辑</Button><Button type="link" onClick={()=>navigate('/governance/standards/review')}>待审批</Button></>}<Button type="link" disabled={busy} onClick={()=>{setTest(r);setResult(null);setValue('');}}>格式校验</Button></Space>},
  ]}/>
  <EditorModal open={edit!==undefined} title={edit?'编辑编码规范':'新建编码规范'} initial={initial} onCancel={()=>setEdit(undefined)} saveMessage="编码规范已保存到共享数据元，自动进入待审批列表" onSave={v=>{if(!choices.data||choices.error)throw new Error('请先读取表单选项');return saveEncoding(v,choices.data.classes,edit);}}>
   <ErrorNotice error={choices.error} retry={choices.refresh}/><EncodingFields legacy={!!edit&&!segmentsFromPattern(edit.formatPattern)} choices={choices.data||emptyChoices}/>
  </EditorModal>
  <Modal open={!!test} title="校验编码格式" onCancel={()=>{setTest(null);setResult(null);}} confirmLoading={busy} okText="执行校验" onOk={()=>run(async()=>setResult(await testEncoding(test!.formatPattern,value)),'')}>
   <p className="muted">按已保存的格式规则校验；不预留号码、不执行脚本、不检查号码唯一性。</p><Input aria-label="待校验编码" maxLength={1024} value={value} onChange={e=>{setValue(e.target.value);setResult(null);}} placeholder="输入实际编码"/>{result&&<><Alert className="mt16" showIcon type={result.valid?'success':'warning'} title={result.valid?'格式符合':'格式不符合'} description={result.message}/><JsonDetail value={result}/></>}
  </Modal>
 </Page>;
}

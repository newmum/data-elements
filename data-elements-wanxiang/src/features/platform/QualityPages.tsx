import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Col, Descriptions, Drawer, Empty, Form, Input, InputNumber, Progress, Row as GridRow, Select, Space, Table, Tabs, Tooltip } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import type { Row } from '../../services/api';
import { qualityApi, qualityBody, qualityDraft, emptyQualityDraft, qualityExport, qualityRunning, qualityStatus, qualityScore, canMaintainQuality, dimensionLabel, severityLabel, qualityFormatPresets, runStates, orderStates, type QualityDraft, type QualityQuery } from '../../services/quality';
import { useWorkspaceQuery } from '../../services/useWorkspaceQuery';
import { ModuleIcon } from '../../design/ModuleIcon';
import { DataTable, ErrorNotice, Page, date, download, required, text, useAction } from './common';
import '../../styles/shared-quality.css';

const severityOptions=['LOW','MEDIUM','HIGH','CRITICAL'].map(value=>({value,label:severityLabel(value)}));
function QualityState({value}:{value:unknown}) { const s=String(value??'');const tone=s==='FAILED'?'danger':s==='COMPLETED_WITH_ISSUES'||s==='OPEN'||s==='PENDING_RECHECK'?'warning':qualityRunning(s)?'info':s==='COMPLETED'||s==='CLOSED'?'success':'neutral';return <span className={`wx-status tone-${tone}`}><i/>{qualityStatus(value)}</span>; }
const score=qualityScore;
function QualityObjectCell({row,historical=false}:{row:Row;historical?:boolean}) {
 return <div className="wx-cell-stack"><strong>{row.table_name_cn||row.table_name||(historical?'历史关联已失效':'关联失效')}</strong><small>{row.datasource_name||(historical?'原数据源不可用':'请重新关联数据源')}</small></div>;
}
function useQualityList(kind:'tasks'|'runs'|'orders',key:string,taskKind?:'PROFILE'|'QUALITY') {
 const [query,setQuery]=useState<QualityQuery>({page:1,size:15,keyword:'',status:'',...(taskKind?{kind:taskKind}:{})}),[poll,setPoll]=useState(false);
 const loader=kind==='tasks'?qualityApi.taskPage:kind==='runs'?qualityApi.runPage:qualityApi.workOrderPage;
 const q=useWorkspaceQuery(`${key}:${JSON.stringify(query)}`,signal=>loader(query,signal),kind==='orders'?10000:poll?3000:false);
 useEffect(()=>{if(q.data)setPoll(q.data.list.some(r=>qualityRunning(r.status)||qualityRunning(r.latest_run_status)));},[q.data]);
 const total=q.data?.total??0;
 useEffect(()=>{if(q.data&&!q.loading&&query.page>1&&query.page>Math.max(1,Math.ceil(total/query.size)))setQuery(v=>({...v,page:Math.max(1,Math.ceil(total/v.size))}));},[total,q.data,q.loading,query.page,query.size]);
 return {...q,query,setQuery,rows:q.data?.list??[],search:(keyword:string)=>setQuery(v=>({...v,page:1,keyword})),pagination:{page:query.page,size:query.size,total,onChange:(page:number,size:number)=>setQuery(v=>({...v,page,size}))}};
}
function QualitySummary() {
 const q=useWorkspaceQuery('shared-quality-summary',qualityApi.summary,5000),s=q.data;
 return <><ErrorNotice error={q.error} retry={q.refresh}/><div className="wx-source-kpis wx-quality-kpis">{[
  ['共享治理任务',s?.task_total,'plans','含质检与探查 · 已启用 '+(s?.task_enabled??'—')+' 项'],['近 30 日执行',s?.run_total,'reports','含质检与探查 · 执行中 '+(s?.running_total??'—')+' 项'],['异常检查项',s?.violation_total,'workorders','同一记录可能触发多条规则'],['平均质量评分',s?.run_total?score(s.average_score):'未评价','rules','字段探查与零检查不计评分'],
 ].map(([label,value,icon,note])=><article className="wx-source-kpi" key={String(label)}><span className="source-kpi-icon"><ModuleIcon name={String(icon)}/></span><div><span>{label}</span><strong>{value??'—'}</strong><small>{note}</small></div></article>)}</div></>;
}
const csvColumns={
 tasks:[['任务名称','task_name'],['任务编码','task_code'],['数据源','datasource_name'],['数据表','table_name'],['规则数','rule_count'],['预检','precheck_status'],['启用','enabled'],['触发方式','trigger_mode'],['最近执行','latest_run_status'],['最近评分','latest_score'],['更新时间','updated_time']],
 runs:[['实例标识','id'],['任务','task_name'],['数据表','table_name'],['状态','status'],['评分','score'],['异常检查项','violation_total'],['开始时间','started_time'],['完成时间','finished_time']],
 orders:[['工单标识','id'],['标题','title'],['问题说明','detail'],['责任组织','assigneeOrgName'],['状态','status'],['整改说明','resolution'],['更新时间','updatedTime']],
};
async function exportQuality(kind:keyof typeof csvColumns,query:Partial<QualityQuery>) {
 const rows=await qualityExport(kind,query),columns=csvColumns[kind];
 const safe=(value:unknown)=>{let s=value==null?'':String(value);if(/^[=+@\-\t\r]/.test(s))s="'"+s;return '"'+s.replaceAll('"','""')+'"';};
 download('\uFEFF'+[columns.map(([label])=>safe(label)).join(','),...rows.map(r=>columns.map(([,key])=>safe(kind==='runs'&&key==='score'?score(r.score,r.checked_count):r[key])).join(','))].join('\r\n'),`${kind==='tasks'?'质检任务':kind==='runs'?'质量执行记录':'质量整改工单'}.csv`,'text/csv;charset=utf-8');
}

function RuleFields({index,columns,templates,tables}:{index:number;columns:Row[];templates:Row[];tables:Row[]}) {
 const form=Form.useFormInstance(),type=Form.useWatch(['rules',index,'ruleType'],form),pattern=Form.useWatch(['rules',index,'parameters','pattern'],form);
 const referenceTable=Form.useWatch(['rules',index,'parameters','targetTableId'],form);
 const reference=useWorkspaceQuery('quality-reference-columns:'+referenceTable,signal=>referenceTable?qualityApi.columns(referenceTable,signal):Promise.resolve([]));
 const columnOptions=columns.map(c=>({value:c.column_name,label:`${c.column_comment||c.column_name} · ${c.column_name}`}));
 return <><GridRow gutter={16}>
  <Col xs={24} sm={12}><Form.Item name={[index,'ruleName']} label="规则名称" rules={required}><Input maxLength={128}/></Form.Item></Col>
  <Col xs={24} sm={12}><Form.Item name={[index,'ruleType']} label="规则模板" rules={required}><Select options={templates.map(t=>({value:t.ruleType,label:`${t.ruleName} · ${dimensionLabel(t.dimension)}`}))} onChange={value=>{const t=templates.find(r=>r.ruleType===value);form.setFieldValue(['rules',index,'dimension'],t?.dimension||'VALIDITY');form.setFieldValue(['rules',index,'parameters'],value==='TIMELINESS'?{maxAgeMinutes:1440,futureToleranceMinutes:0}:{});}}/></Form.Item></Col>
  <Col xs={24} sm={12}><Form.Item name={[index,'columnName']} label="校验字段" rules={required}><Select showSearch optionFilterProp="label" options={columns.map(c=>({value:c.column_name,label:`${c.column_comment||c.column_name} · ${c.column_name}`}))}/></Form.Item></Col>
  <Col xs={24} sm={6}><Form.Item name={[index,'severity']} label="严重程度" rules={required}><Select options={severityOptions}/></Form.Item></Col>
  <Col xs={24} sm={6}><Form.Item name={[index,'weight']} label="评分权重" rules={required}><InputNumber min={1} max={100} precision={0}/></Form.Item></Col>
  <Form.Item name={[index,'dimension']} hidden><Input/></Form.Item><Form.Item name={[index,'relatedColumn']} hidden><Input/></Form.Item>
  {type==='UNIQUE'&&<Col span={24}><Form.Item name={[index,'parameters','columns']} label="联合唯一字段（留空时仅检查主要字段）"><Select mode="multiple" options={columnOptions} placeholder="选择多个字段，按完整组合检查重复"/></Form.Item></Col>}
  {type==='TIMELINESS'&&<><Col span={12}><Form.Item name={[index,'parameters','maxAgeMinutes']} label="最大允许过去时差（分钟）" rules={required}><InputNumber min={1} max={5256000}/></Form.Item></Col><Col span={12}><Form.Item name={[index,'parameters','futureToleranceMinutes']} label="未来时间容差（分钟）"><InputNumber min={0} max={5256000}/></Form.Item></Col></>}
  {type==='REFERENCE'&&<><Col span={24}><Form.Item name={[index,'parameters','targetTableId']} label="引用目标表（同一登记数据源）" rules={required}><Select showSearch optionFilterProp="label" options={tables.map(t=>({value:t.id,label:t.table_name_cn||t.table_name}))} onChange={()=>form.setFieldValue(['rules',index,'parameters','targetColumns'],[])}/></Form.Item></Col><Col span={12}><Form.Item name={[index,'parameters','sourceColumns']} label="来源联合字段（按选择顺序）" rules={required}><Select mode="multiple" options={columnOptions}/></Form.Item></Col><Col span={12}><Form.Item name={[index,'parameters','targetColumns']} label="目标联合字段（顺序一一对应）" rules={required}><Select loading={reference.loading} disabled={!!reference.error} mode="multiple" options={(reference.data||[]).map(c=>({value:c.column_name,label:c.column_comment||c.column_name}))}/></Form.Item></Col><Col span={24}><ErrorNotice error={reference.error} retry={reference.refresh}/><Form.Item name={[index,'parameters','allowNull']} label="空引用策略"><Select options={[{value:false,label:'空值算作异常'},{value:true,label:'允许空值，仅检查非空引用'}]}/></Form.Item></Col></>}
  {type==='RANGE'&&<><Col span={12}><Form.Item name={[index,'parameters','min']} label="最小值（含）"><Input placeholder="至少填写一个边界，支持小数和负数"/></Form.Item></Col><Col span={12}><Form.Item name={[index,'parameters','max']} label="最大值（含）"><Input/></Form.Item></Col></>}
  {type==='LENGTH'&&<><Col span={12}><Form.Item name={[index,'parameters','minLength']} label="最小长度"><InputNumber min={0} max={65535} precision={0} placeholder="至少填写一个长度边界"/></Form.Item></Col><Col span={12}><Form.Item name={[index,'parameters','maxLength']} label="最大长度"><InputNumber min={0} max={65535} precision={0}/></Form.Item></Col></>}
  {type==='ENUM'&&<Col span={24}><Form.Item name={[index,'parameters','values']} label="允许值" rules={required}><Select mode="tags" tokenSeparators={[',','，']} placeholder="输入值后按 Enter；保留前导零"/></Form.Item></Col>}
  {type==='REGEX'&&<><Col xs={24} sm={8}><Form.Item label="预设格式"><Select aria-label="预设格式" value={qualityFormatPresets.some(p=>p.pattern===pattern)?pattern:''} options={[{value:'',label:'自定义正则'},...qualityFormatPresets.map(p=>({value:p.pattern,label:p.label}))]} onChange={value=>form.setFieldValue(['rules',index,'parameters','pattern'],value)}/></Form.Item></Col><Col xs={24} sm={16}><Form.Item name={[index,'parameters','pattern']} label="正则表达式" rules={required}><Input maxLength={1000} placeholder="例如：^[A-Z0-9]+$"/></Form.Item></Col></>}
 </GridRow></>;
}

function TaskWorkspace({mode}:{mode:'plans'|'rules'|'profiling'}) {
 const state=useQualityList('tasks',`shared-quality-${mode}`,mode==='profiling'?'PROFILE':'QUALITY'),{busy,run}=useAction(),{message,modal}=App.useApp(),[params]=useSearchParams();
 const [editor,setEditor]=useState<QualityDraft>(),[sources,setSources]=useState<Row[]>([]),[tables,setTables]=useState<Row[]>([]),[columns,setColumns]=useState<Row[]>([]),[templates,setTemplates]=useState<Row[]>([]),[optionsLoading,setOptionsLoading]=useState(false),[optionsError,setOptionsError]=useState(''),[runId,setRunId]=useState<string>();
 const [form]=Form.useForm<QualityDraft & {profileColumns?:string[];profileMode?:string;profileLimit?:number}>(),optionsSequence=useRef(0),dirty=useRef(false),opening=useRef(false);
 const datasourceId=Form.useWatch('datasourceId',form),tableId=Form.useWatch('tableId',form),trigger=Form.useWatch('triggerMode',form);
 const open=async(task?:Row)=>{const n=++optionsSequence.current;setOptionsLoading(true);setOptionsError('');try {
  const [sourceRows,templateRows,current]=await Promise.all([qualityApi.datasources(),qualityApi.templates(),task?qualityApi.taskDetail(task.id):Promise.resolve(null)]);
  const draft=current?qualityDraft(current):emptyQualityDraft();
  const [tableRows,columnRows]=draft.tableId?await Promise.all([qualityApi.tables(draft.datasourceId),qualityApi.columns(draft.tableId)]):[[],[]];
  if(n===optionsSequence.current){setSources(sourceRows);setTemplates(templateRows);setTables(tableRows);setColumns(columnRows);form.resetFields();form.setFieldsValue(draft);if(mode==='profiling')form.setFieldsValue({profileColumns:draft.rules.map(r=>r.columnName),profileMode:draft.rules[0]?.parameters.scanMode||'SAMPLE',profileLimit:draft.rules[0]?.parameters.scanLimit||2000,shardCount:1,sampleLimit:0});dirty.current=false;setEditor(draft);}
 }catch(cause){if(n===optionsSequence.current){setOptionsError(cause instanceof Error?cause.message:String(cause));message.error(cause instanceof Error?cause.message:String(cause));}}finally{if(n===optionsSequence.current)setOptionsLoading(false);}};
 useEffect(()=>{if(params.get('create')==='1'&&!opening.current){opening.current=true;void open();}return()=>{optionsSequence.current++;};},[params]);
 const chooseSource=async(value:string)=>{const n=++optionsSequence.current;form.setFieldsValue({datasourceId:value,tableId:undefined,shardKey:'',rules:[],profileColumns:[]});setTables([]);setColumns([]);setOptionsError('');setOptionsLoading(true);try{const rows=await qualityApi.tables(value);if(n===optionsSequence.current)setTables(rows);}catch(cause){if(n===optionsSequence.current)setOptionsError(String((cause as Error).message));}finally{if(n===optionsSequence.current)setOptionsLoading(false);}};
 const chooseTable=async(value:string)=>{const n=++optionsSequence.current;form.setFieldsValue({tableId:value,shardKey:'',rules:[],profileColumns:[]});setColumns([]);setOptionsError('');setOptionsLoading(true);try{const rows=await qualityApi.columns(value);if(n===optionsSequence.current)setColumns(rows);}catch(cause){if(n===optionsSequence.current)setOptionsError(String((cause as Error).message));}finally{if(n===optionsSequence.current)setOptionsLoading(false);}};
 const close=()=>{if(busy)return;const finish=()=>{optionsSequence.current++;setEditor(undefined);setOptionsLoading(false);};if(dirty.current)modal.confirm({title:'放弃未保存的质检配置？',okText:'放弃修改',cancelText:'继续编辑',onOk:finish});else finish();};
 const precheck=async(taskId:string)=>{const result=await qualityApi.precheck(taskId);if(result.success)message.success('预检通过');else modal.error({title:'预检未通过',content:<><p>{result.message}</p>{result.detail&&<pre className="wx-quality-error">{text(result.detail)}</pre>}</>});return result.success;};
 const save=async(withPrecheck:boolean)=>{
  if(optionsLoading||optionsError||!editor)return;
  let draft:QualityDraft;try{const values=await form.validateFields();const rules=mode==='profiling'?(values.profileColumns||[]).map(columnName=>({ruleName:columnName+' · 字段探查',ruleType:'PROFILE',dimension:'PROFILE',columnName,severity:'LOW',weight:1,parameters:{scanMode:values.profileMode||'SAMPLE',scanLimit:values.profileLimit||2000}})):values.rules;draft=qualityBody({...editor,...values,rules});if(!sources.some(s=>s.id===draft.datasourceId)||!tables.some(t=>t.id===draft.tableId)||draft.rules.some(r=>!columns.some(c=>c.column_name===r.columnName)))throw new Error('源、表或字段已失效，请重新选择真实元数据');}catch(cause){if(cause instanceof Error)message.error(cause.message);return;}
  await run(async()=>{const saved=await qualityApi.saveTask(draft);dirty.current=false;setEditor(undefined);message.success('已保存到共享后端，任务已停用并需要重新预检');if(withPrecheck)await precheck(saved.id);},'');
 };
 const start=(row:Row)=>run(async()=>{if(row.precheck_status!=='PASSED'&&!await precheck(row.id))return;const result=await qualityApi.manualRun(row.id);setRunId(result.id);message.success('真实异步质检已提交');},'');
 const toggle=(row:Row)=>run(async()=>{const enable=!row.enabled;if(enable&&!await precheck(row.id))return;await qualityApi.enable(row.id,enable);message.success(enable?'任务已启用':'任务已停用');},'');
 const remove=(row:Row)=>modal.confirm({title:'删除质检任务？',content:'仅删除任务配置，历史报告、问题样例和整改工单保留；执行中的任务不能删除。',okText:'删除任务',okButtonProps:{danger:true},onOk:async()=>{await qualityApi.deleteTask(row.id);message.success('任务配置已删除，历史记录保留');}});
 const title=mode==='plans'?'质检方案与任务':mode==='rules'?'数据质量规则':'数据质量探查';
 const numeric=columns.filter(c=>/int|number|decimal|numeric|long|float|double/i.test(c.data_type||c.column_type||''));
 return <Page title={title} description="使用共享任务和真实源数据库执行质量检查。" actions={<Button type="primary" icon={<PlusOutlined/>} loading={optionsLoading&&!editor} disabled={!canMaintainQuality()||busy} onClick={()=>void open()}>新建{mode==='rules'?'规则与任务':mode==='profiling'?'探查任务':'质检方案'}</Button>}>
  <QualitySummary/><ErrorNotice error={optionsError&&!editor?optionsError:null}/>
  <DataTable titleKey={`shared-quality-${mode}`} rows={state.rows} loading={state.loading} error={state.error} onRefresh={state.refresh} serverPagination={state.pagination} onSearch={state.search} searchPlaceholder="搜索任务、数据源或表；Enter 查询" onExport={()=>exportQuality('tasks',state.query)} actions={<Select aria-label="质检任务状态" style={{width:130}} value={state.query.status} onChange={status=>state.setQuery(v=>({...v,status,page:1}))} options={[{value:'',label:'全部任务'},{value:'1',label:'已启用'},{value:'2',label:'已停用'}]}/>} columns={[
   {title:mode==='rules'?'规则所属任务':'任务名称',dataIndex:'task_name',width:220,render:(_,r)=><div className="wx-cell-stack"><strong>{r.task_name}</strong><small>{r.task_code}</small></div>},
   {title:'作用数据表',width:230,render:(_,r)=><QualityObjectCell row={r}/>},
   {title:mode==='profiling'?'探查字段数':'规则数',dataIndex:'rule_count',width:105,align:'right'},
   {title:'触发策略',width:135,render:(_,r)=><Tooltip title={r.cron_expression||'按操作手动执行'}><span>{r.trigger_mode==='CRON'?'定时执行':'手动执行'}</span></Tooltip>},
   {title:'预检',width:110,render:(_,r)=><span className={`wx-status tone-${r.precheck_status==='PASSED'?'success':r.precheck_status==='FAILED'?'danger':'neutral'}`}><i/>{r.precheck_status==='PASSED'?'已通过':r.precheck_status==='FAILED'?'未通过':'待预检'}</span>},
   {title:'状态',width:105,render:(_,r)=><span className={`wx-status tone-${r.enabled?'success':'neutral'}`}><i/>{r.enabled?'已启用':'已停用'}</span>},
   {title:'最近执行',width:160,render:(_,r)=><div className="wx-cell-stack"><QualityState value={r.latest_run_status}/>{mode!=='profiling'&&<small>{score(r.latest_score)}</small>}</div>},
   {title:'更新时间',dataIndex:'updated_time',width:160,render:date},
   {title:'操作',width:240,render:(_,r)=><Space size={4}><Button type="link" disabled={busy||optionsLoading||qualityRunning(r.latest_run_status)||!canMaintainQuality()} onClick={()=>void open(r)}>配置规则</Button><Button type="link" disabled={busy||!canMaintainQuality()} onClick={()=>run(()=>precheck(r.id),'')}>预检</Button><Button type="link" disabled={busy||(!r.enabled&&!!r.repairRequired)||!canMaintainQuality()} onClick={()=>toggle(r)}>{r.enabled?'停用':'启用'}</Button><Button type="link" disabled={busy||!!r.repairRequired||qualityRunning(r.latest_run_status)||!canMaintainQuality()} onClick={()=>start(r)}>执行</Button><Button danger type="link" disabled={busy||qualityRunning(r.latest_run_status)||!canMaintainQuality()} onClick={()=>remove(r)}>删除</Button></Space>},
  ]}/>
  <Drawer rootClassName="wx-editor-drawer wx-quality-editor" open={!!editor} size={880} title={mode==='profiling'?(editor?.tid?'配置字段探查任务':'新建字段探查任务'):(editor?.tid?'配置质检任务与规则':'新建质检任务与规则')} onClose={close} mask={{closable:false}} destroyOnHidden footer={<div className="editor-footer"><span>保存到原数据中台共享后端</span><Space><Button disabled={busy} onClick={close}>取消</Button><Button loading={busy} disabled={optionsLoading||!!optionsError} onClick={()=>void save(false)}>保存</Button><Button type="primary" loading={busy} disabled={optionsLoading||!!optionsError} onClick={()=>void save(true)}>保存并预检</Button></Space></div>}>
   <ErrorNotice error={optionsError} retry={()=>void run(async()=>{const n=++optionsSequence.current;const [t,c]=await Promise.all([datasourceId?qualityApi.tables(datasourceId):Promise.resolve([]),tableId?qualityApi.columns(tableId):Promise.resolve([])]);if(n===optionsSequence.current){setTables(t);setColumns(c);setOptionsError('');}},'')}/>
   <Form form={form} initialValues={editor} layout="vertical" className="wx-editor-form" onValuesChange={()=>{dirty.current=true;}}>
    <div className="wx-form-section-title"><span>01</span> 任务与检查对象</div><GridRow gutter={16}>
     <Col xs={24} sm={12}><Form.Item name="taskName" label="任务名称" rules={required}><Input maxLength={128}/></Form.Item></Col><Col xs={24} sm={12}><Form.Item name="taskCode" label="任务编码" rules={required}><Input maxLength={64}/></Form.Item></Col>
     <Col xs={24} sm={12}><Form.Item name="datasourceId" label="数据源" rules={required}><Select showSearch optionFilterProp="label" loading={optionsLoading} options={sources.map(s=>({value:s.id,label:s.db_name||s.id}))} onChange={value=>void chooseSource(value)}/></Form.Item></Col>
     <Col xs={24} sm={12}><Form.Item name="tableId" label="数据表" rules={required}><Select showSearch optionFilterProp="label" disabled={!datasourceId} loading={optionsLoading} options={tables.map(t=>({value:t.id,label:`${t.table_name_cn||t.table_comment||t.table_name} · ${t.table_name}`}))} onChange={value=>void chooseTable(value)}/></Form.Item></Col>
    </GridRow>{tableId&&!columns.length&&!optionsLoading&&<Alert className="mb16" showIcon type="warning" title="该表没有已采集字段，请先完成元数据采集，不能凭空配置检查字段。"/>}
    <div className="wx-form-section-title"><span>02</span> {mode==='profiling'?'字段探查':'质量规则'}</div>
    {mode==='profiling'?<><Form.Item name="profileColumns" label="探查字段（最多 64 个）" rules={required}><Select mode="multiple" maxCount={64} options={columns.map(c=>({value:c.column_name,label:c.column_comment||c.column_name}))}/></Form.Item><GridRow gutter={16}><Col span={12}><Form.Item name="profileMode" label="扫描范围" rules={required}><Select options={[{value:'SAMPLE',label:'有界抽样（不代表全量）'},{value:'FULL',label:'全量聚合（受超时限制）'}]}/></Form.Item></Col><Col span={12}><Form.Item name="profileLimit" label="抽样最大扫描行数"><InputNumber min={1} max={10000} precision={0}/></Form.Item></Col></GridRow><Alert className="mb16" showIcon type="info" title="真实字段统计" description="读取实际表的聚合统计：空值率、文本空白率、非空去重值数。报告标明全量或有界抽样；不上传业务行，不把字段统计当成质量评分。"/></>:<Form.List name="rules">{(fields,{add,remove})=><>{fields.map(field=><section className="wx-quality-rule-card" key={field.key}><header><b>规则 {field.name+1}</b><Button type="text" danger aria-label={`删除规则 ${field.name+1}`} icon={<DeleteOutlined/>} onClick={()=>remove(field.name)}/></header><RuleFields index={field.name} columns={columns} templates={templates} tables={tables}/></section>)}<Button block type="dashed" disabled={!columns.length||optionsLoading} icon={<PlusOutlined/>} onClick={()=>add({ruleName:'非空校验',ruleType:'NOT_NULL',columnName:'',dimension:'COMPLETENESS',severity:'MEDIUM',weight:10,parameters:{}})}>添加质量规则</Button></>}</Form.List>}
    <div className="wx-form-section-title"><span>03</span> 执行策略</div><GridRow gutter={16}>
     <Col xs={24} sm={12}><Form.Item name="triggerMode" label="触发方式" rules={required}><Select options={[{value:'MANUAL',label:'手动执行'},{value:'CRON',label:'定时执行'}]}/></Form.Item></Col>
     {trigger==='CRON'&&<Col xs={24} sm={12}><Form.Item name="cronExpression" label="Cron 表达式（六段）" rules={required}><Input maxLength={128} placeholder="0 0 2 * * *"/></Form.Item></Col>}
     <Col xs={24} sm={12}><Form.Item name="shardKey" hidden={mode==='profiling'} label="数值分片字段"><Select allowClear showSearch optionFilterProp="label" options={numeric.map(c=>({value:c.column_name,label:c.column_name}))}/></Form.Item></Col>
     <Col xs={24} sm={8}><Form.Item name="shardCount" hidden={mode==='profiling'} label="分片数" rules={required}><InputNumber min={1} max={128} precision={0}/></Form.Item></Col>
     <Col xs={24} sm={8}><Form.Item name="sampleLimit" hidden={mode==='profiling'} label="异常样例上限" rules={required} extra="仅限制保存的异常样例，不是扫描行数预算"><InputNumber min={0} max={2000} precision={0}/></Form.Item></Col>
     <Col xs={24} sm={8}><Form.Item name="timeoutMinutes" label="超时（分钟）" rules={required}><InputNumber min={1} max={1440} precision={0}/></Form.Item></Col>
    </GridRow><Form.Item name="resourceGroup" hidden><Input/></Form.Item><Form.Item name="description" label="任务说明"><Input.TextArea maxLength={1000} rows={3}/></Form.Item>
    <Alert showIcon type="info" title="规则和执行策略保存到共享后端" description="保存会停用任务并清除旧预检；预检成功后才能启用或执行。执行读取实际源数据库，问题整改需在源系统完成，不在此编辑业务记录。"/>
   </Form>
  </Drawer><QualityRunDrawer runId={runId} onClose={()=>setRunId(undefined)}/>
 </Page>;
}
export function RulesPage(){return <TaskWorkspace mode="rules"/>;}
export function PlansPage(){return <TaskWorkspace mode="plans"/>;}
export function ProfilingPage(){return <TaskWorkspace mode="profiling"/>;}

export function ReportsPage({profile=false}:{profile?:boolean}) {
 const state=useQualityList('runs',`shared-quality-reports-${profile}`,profile?'PROFILE':'QUALITY'),[runId,setRunId]=useState<string>(),{busy,run}=useAction();
 return <Page title={profile?'探查报告':'质量报告'} description="真实共享质检实例的执行结果、规则指标、分片和有限异常样例。"><QualitySummary/>
  <DataTable titleKey={`shared-quality-reports-${profile}`} rows={state.rows} loading={state.loading} error={state.error} onRefresh={state.refresh} serverPagination={state.pagination} onSearch={state.search} searchPlaceholder="搜索任务或数据表；Enter 查询" onExport={()=>exportQuality('runs',state.query)} actions={<Select aria-label="质检执行状态" style={{width:140}} value={state.query.status} onChange={status=>state.setQuery(v=>({...v,status,page:1}))} options={[{value:'',label:'全部状态'},...runStates.map(value=>({value,label:qualityStatus(value)}))]}/>} columns={[
   {title:'任务 / 实例',width:230,render:(_,r)=><div className="wx-cell-stack"><strong>{r.task_name}</strong><small>{r.id}</small></div>},
   {title:'数据表',width:230,render:(_,r)=><QualityObjectCell row={r} historical/>},{title:'状态',dataIndex:'status',width:145,render:value=><QualityState value={value}/>},
   ...(profile?[{title:'实际探查行数',dataIndex:'row_count',width:130,align:'right' as const,render:text}]:[{title:'检查项数',dataIndex:'checked_count',width:100,align:'right' as const,render:text},{title:'异常检查项',dataIndex:'violation_total',width:110,align:'right' as const,render:text},{title:'质量评分',dataIndex:'score',width:110,align:'right' as const,render:(_:unknown,r:Row)=>score(r.score,r.checked_count)}]),
   {title:'开始时间',dataIndex:'started_time',width:170,render:date},{title:'完成时间',dataIndex:'finished_time',width:170,render:date},
   {title:'操作',width:152,render:(_,r)=><Space size={4}><Button type="link" onClick={()=>setRunId(r.id)}>查看指标</Button>{qualityRunning(r.status)&&<Button type="link" danger disabled={busy||!canMaintainQuality()} onClick={()=>run(()=>qualityApi.cancelRun(r.id),'已提交取消请求')}>取消执行</Button>}</Space>},
  ]}/><QualityRunDrawer runId={runId} onClose={()=>setRunId(undefined)}/>
 </Page>;
}

/** The quality-page bell reads the same backend runs, never the browser demo job store. */
export function QualityTaskCenter({onClose}:{onClose:()=>void}) {
 const state=useQualityList('runs','shared-quality-task-center'),[runId,setRunId]=useState<string>();
 return <><Drawer open size={980} title="质检执行中心" onClose={onClose} destroyOnHidden>
  <DataTable titleKey="shared-quality-task-center" rows={state.rows} loading={state.loading} error={state.error} onRefresh={state.refresh} serverPagination={state.pagination} onSearch={state.search} onExport={()=>exportQuality('runs',state.query)} searchPlaceholder="搜索真实质检任务或数据表" columns={[
   {title:'任务名称',dataIndex:'task_name',width:240},{title:'数据表',width:230,render:(_,r)=><QualityObjectCell row={r} historical/>},{title:'状态',dataIndex:'status',width:145,render:value=><QualityState value={value}/>},{title:'开始时间',dataIndex:'started_time',width:175,render:date},{title:'操作',width:132,render:(_,r)=><Button type="link" onClick={()=>setRunId(r.id)}>结果与执行依据</Button>},
  ]}/>
 </Drawer><QualityRunDrawer runId={runId} onClose={()=>setRunId(undefined)}/></>;
}

export function QualityRunDrawer({runId,onClose}:{runId?:string;onClose:()=>void}) {
 const [page,setPage]=useState(1),[severity,setSeverity]=useState(''),[tab,setTab]=useState('metrics'),[create,setCreate]=useState<Row|null>(null),[owner,setOwner]=useState(''),[remark,setRemark]=useState('');
 const detail=useWorkspaceQuery(`shared-quality-run:${runId}`,signal=>runId?qualityApi.runDetail(runId,signal):Promise.resolve(null),runId?3000:false);
 const issues=useWorkspaceQuery(`shared-quality-issues:${runId}:${page}:${severity}`,signal=>runId?qualityApi.runIssues(runId,page,20,severity,'',signal):Promise.resolve(null),runId?3000:false);
 const orgs=useWorkspaceQuery(`shared-quality-create-organizations:${!!create}`,signal=>create?qualityApi.organizations(signal):Promise.resolve([]));
 const {busy,run}=useAction(),{modal}=App.useApp(),data=detail.data;
 useEffect(()=>{setPage(1);setSeverity('');setTab('metrics');setCreate(null);},[runId]);
 const refresh=()=>{detail.refresh();issues.refresh();};
 const progress=data?.shard_total?Math.min(100,Math.round((data.shards||[]).filter((s:Row)=>s.status==='COMPLETED').length*100/data.shard_total)):null;
 const metrics:Row[]=data?.metrics||[],shards=data?.shards||[],profile=data?.task_kind==='PROFILE';
 const cancel=()=>modal.confirm({title:'取消本次质检执行？',content:'已完成的历史记录会保留；取消后不再接收迟到的分片结果。',onOk:async()=>{await qualityApi.cancelRun(runId!);refresh();}});
 return <><Drawer rootClassName="wx-quality-detail" open={!!runId} size={980} title="结果与执行依据" onClose={onClose} destroyOnHidden extra={<Space><Button loading={detail.loading} onClick={refresh}>刷新详情</Button>{qualityRunning(data?.status)&&<Button danger disabled={busy||!canMaintainQuality()} onClick={cancel}>取消执行</Button>}</Space>}>
  <ErrorNotice error={detail.error||issues.error} retry={refresh}/>{!data&&detail.loading?<Empty description="正在读取真实质检报告…"/>:data&&<>
   <div className="wx-quality-detail-heading"><h3>{data.task_name}</h3><QualityState value={data.status}/></div>
   {progress!=null&&<Progress percent={progress} status={data.status==='FAILED'?'exception':undefined}/>}
   <Descriptions column={2} items={[['实例 ID',data.id],['数据表',data.table_name],['扫描行数',data.row_count],['检查项数',data.checked_count],['质量评分',score(data.score,data.checked_count)],['异常检查项',data.violation_total],['开始时间',date(data.started_time)],['完成时间',date(data.finished_time)]].map(([label,value])=>({key:String(label),label,children:text(value)}))}/>
   {data.error_message&&<Alert className="mb16" showIcon type="error" title={data.error_message} description={data.error_detail}/>}
   <Tabs activeKey={tab} onChange={setTab} items={[
    {key:'metrics',label:`${profile?'字段统计':'规则指标'}（${metrics.length}）`,children:profile?<><Alert className="mb16" type="info" showIcon title="统计范围以每个字段的实际扫描行数为准" description="抽样不是全量；去重值数不包含 NULL。空表比率未评价，不支持的字段类型显示未提供，不制造 0 或满分。"/><Button className="mb16" onClick={()=>download('\uFEFF'+[['字段','范围','扫描行数','空值数','空值率%','空白数','空白率%','非空去重值数'],...metrics.map((r:Row)=>[r.column_name,r.profile?.scanMode,r.profile?.scannedRows,r.profile?.nullCount,r.profile?.nullRate,r.profile?.blankCount,r.profile?.blankRate,r.profile?.distinctCount])].map(c=>c.map(v=>'"'+String(v??'未提供').replaceAll('"','""')+'"').join(',')).join('\r\n'),'字段探查统计.csv','text/csv;charset=utf-8')}>导出字段统计</Button><Table<Row> rowKey="id" scroll={{x:1000}} pagination={false} dataSource={metrics} columns={[{title:'字段',dataIndex:'column_name',width:180,sorter:(a,b)=>String(a.column_name||'').localeCompare(String(b.column_name||''),'zh-CN',{numeric:true})},{title:'统计范围',width:120,render:(_,r)=>r.profile?.scanMode==='FULL'?'全量':'有界抽样'},{title:'扫描行数',align:'right',render:(_,r)=>text(r.profile?.scannedRows)},{title:'空值数',align:'right',render:(_,r)=>text(r.profile?.nullCount)},{title:'空值率（%）',align:'right',render:(_,r)=>score(r.profile?.nullRate,r.profile?.scannedRows)},{title:'空白数',align:'right',render:(_,r)=>r.profile?.textual?text(r.profile?.blankCount):'不适用'},{title:'空白率（%）',align:'right',render:(_,r)=>r.profile?.textual?score(r.profile?.blankRate,r.profile?.scannedRows):'不适用'},{title:'非空去重值数',align:'right',render:(_,r)=>r.profile?.distinctSupported?text(r.profile?.distinctCount):'类型不支持'}]}/></>:<Table<Row> rowKey="id" scroll={{x:850}} pagination={false} dataSource={metrics} columns={[{title:'规则',dataIndex:'rule_name',width:180,sorter:(a,b)=>String(a.rule_name||'').localeCompare(String(b.rule_name||''),'zh-CN',{numeric:true})},{title:'字段',dataIndex:'column_name',width:150},{title:'维度',dataIndex:'quality_dimension',width:110,render:dimensionLabel},{title:'检查数',dataIndex:'checked_count',align:'right',width:100},{title:'异常数',dataIndex:'violation_count',align:'right',width:100},{title:'通过率（%）',dataIndex:'pass_rate',align:'right',width:120,render:(_,r)=>score(r.pass_rate,r.checked_count)}]}/>},
    {key:'shards',label:`执行分片（${shards.length}）`,children:<Table<Row> rowKey="id" pagination={false} scroll={{x:800}} dataSource={shards} columns={[{title:'分片',dataIndex:'shard_no',width:80},{title:'状态',dataIndex:'status',width:140,render:value=><QualityState value={value}/>},{title:'扫描行数',dataIndex:'row_count',width:120,align:'right'},{title:'异常数',dataIndex:'violation_count',width:100,align:'right'},{title:'执行节点',dataIndex:'worker_id',width:220,render:text},{title:'错误',dataIndex:'error_message',width:220,ellipsis:true,render:text}]}/>},
    {key:'issues',label:`问题样例（${issues.data?.total??0}）`,children:<><Alert className="mb16" type="info" showIcon title="样例是有限定位线索，不代表全部异常记录。异常总数以规则指标为准；同一记录可能触发多条规则。"/><Select className="mb16" aria-label="问题严重程度" style={{width:150}} value={severity} onChange={value=>{setSeverity(value);setPage(1);}} options={[{value:'',label:'全部严重程度'},...severityOptions]}/><Table<Row> rowKey="id" loading={issues.loading&&!issues.data} scroll={{x:1000}} dataSource={issues.data?.list||[]} pagination={{current:page,pageSize:20,total:issues.data?.total||0,showSizeChanger:false,onChange:setPage}} columns={[{title:'规则',dataIndex:'rule_name',width:160,sorter:(a,b)=>String(a.rule_name||'').localeCompare(String(b.rule_name||''),'zh-CN',{numeric:true}),showSorterTooltip:{title:'仅对当前页排序'}},{title:'数据键',dataIndex:'row_key',width:140,render:text},{title:'字段',dataIndex:'column_name',width:130,sorter:(a,b)=>String(a.column_name||'').localeCompare(String(b.column_name||''),'zh-CN',{numeric:true}),showSorterTooltip:{title:'仅对当前页排序'}},{title:'异常值',dataIndex:'issue_value',width:160,ellipsis:true,render:text},{title:'严重程度',dataIndex:'severity',width:100,render:severityLabel},{title:'状态',dataIndex:'issue_status',width:130,render:value=><QualityState value={value}/>},{title:'操作',className:'wx-action-column',align:'center',width:118,fixed:'right',render:(_,r)=><Button type="link" disabled={busy||r.issue_status==='CLOSED'||!canMaintainQuality()} onClick={()=>{setOwner('');setRemark('');setCreate(r);}}>转整改工单</Button>}]}/></>},
   ]}/>
  </>}</Drawer>
  <Drawer open={!!create} title="登记质量整改工单" size={560} onClose={()=>{if(!busy)setCreate(null);}} destroyOnHidden footer={<Space><Button disabled={busy} onClick={()=>setCreate(null)}>取消</Button><Button type="primary" loading={busy} disabled={orgs.loading||!!orgs.error} onClick={()=>run(async()=>{await qualityApi.createWorkOrder(create!.id,owner||undefined,remark);setCreate(null);refresh();},'工单已创建或关联，重复操作不会重复创建')}>登记工单</Button></Space>}><ErrorNotice error={orgs.error} retry={orgs.refresh}/><Form layout="vertical"><Form.Item label="问题"><p>{create?.rule_name} · {create?.column_name}</p></Form.Item><Form.Item label="责任组织（可稍后分派）"><Select allowClear showSearch optionFilterProp="label" value={owner||undefined} onChange={setOwner} options={(orgs.data||[]).map(o=>({value:o.id,label:o.name||o.orgName||o.id}))}/></Form.Item><Form.Item label="问题说明"><Input.TextArea rows={4} maxLength={1000} value={remark} onChange={e=>setRemark(e.target.value)}/></Form.Item></Form></Drawer>
 </>;
}

export function OrdersPage() {
 const state=useQualityList('orders','shared-quality-orders'),[selected,setSelected]=useState<Row|null>(null),[owner,setOwner]=useState(''),[opinion,setOpinion]=useState(''),{busy,run}=useAction(),nav=useNavigate();
 const orgs=useWorkspaceQuery(`shared-quality-order-organizations:${!!selected}`,signal=>selected?qualityApi.organizations(signal):Promise.resolve([]));
 const fresh=state.rows.find(r=>r.id===selected?.id)||selected;
 const open=(row:Row)=>{setSelected(row);setOwner(row.assigneeOrgId||'');setOpinion('');};
 const transition=(action:string)=>run(async()=>{await qualityApi.transitionWorkOrder(fresh!.id,action,opinion.trim(),owner||undefined);setSelected(null);},'工单和问题状态已同步');
 return <Page title="质检工单" description="真实异常样例生成整改工单，责任组织整改后必须通过真实复检才能关闭。">
  <DataTable titleKey="shared-quality-orders" showSearch={false} rows={state.rows} loading={state.loading} error={state.error} onRefresh={state.refresh} serverPagination={state.pagination} onExport={()=>exportQuality('orders',state.query)} actions={<Select aria-label="质检工单状态" style={{width:145}} value={state.query.status} onChange={status=>state.setQuery(v=>({...v,status,page:1}))} options={[{value:'',label:'全部工单'},...orderStates.map(value=>({value,label:qualityStatus(value)}))]}/>} columns={[
   {title:'工单',dataIndex:'title',width:240},{title:'问题说明',dataIndex:'detail',width:240,ellipsis:true},{title:'责任组织',width:180,render:(_,r)=>r.assigneeOrgName||r.assigneeOrgId||'待分派'},
   {title:'状态',dataIndex:'status',width:125,render:value=><QualityState value={value}/>},{title:'整改说明',dataIndex:'resolution',width:220,ellipsis:true,render:text},{title:'更新时间',dataIndex:'updatedTime',width:175,render:date},
   {title:'操作',width:88,render:(_,r)=><Button type="link" onClick={()=>open(r)}>{r.status==='CLOSED'?'查看':'处理'}</Button>},
  ]}/>
  <Drawer rootClassName="wx-quality-detail" title="质量整改工单" open={!!selected} size={650} onClose={()=>{if(!busy)setSelected(null);}} destroyOnHidden>
   <ErrorNotice error={orgs.error} retry={orgs.refresh}/>{fresh&&<><Descriptions column={1} items={[['工单',fresh.title],['问题标识',fresh.assetId],['当前状态',qualityStatus(fresh.status)],['责任组织',fresh.assigneeOrgName||fresh.assigneeOrgId||'待分派'],['最近说明',fresh.resolution||'—']].map(([label,value])=>({key:String(label),label,children:text(value)}))}/>
    {fresh.status!=='CLOSED'&&<Form layout="vertical"><Form.Item label="责任组织" required={fresh.status==='OPEN'}><Select disabled={fresh.status!=='OPEN'||orgs.loading||!!orgs.error} showSearch optionFilterProp="label" value={owner||undefined} onChange={setOwner} options={(orgs.data||[]).map(o=>({value:o.id,label:o.name||o.orgName||o.id}))}/></Form.Item><Form.Item label="整改 / 复检说明" required={fresh.status!=='OPEN'}><Input.TextArea value={opinion} onChange={e=>setOpinion(e.target.value)} maxLength={1000} rows={4}/></Form.Item>
     <Space wrap>{fresh.status==='OPEN'&&<Button type="primary" loading={busy} disabled={!canMaintainQuality()||orgs.loading||!!orgs.error} onClick={()=>transition('START')}>分派并开始整改</Button>}{fresh.status==='IN_PROGRESS'&&<Button type="primary" loading={busy} disabled={!canMaintainQuality()} onClick={()=>transition('SUBMIT_RECHECK')}>提交复检</Button>}{fresh.status==='PENDING_RECHECK'&&<><Alert className="mb16" type="info" showIcon title="先修复源数据，再执行原任务。后端会核对新的已完成实例及原规则结果；空表、无关规则或仍有异常时不能关闭。"/><Button disabled={busy} onClick={()=>{setSelected(null);nav('/governance/quality/plans');}}>前往执行复检</Button><Button type="primary" loading={busy} disabled={!canMaintainQuality()} onClick={()=>transition('CLOSE')}>复检通过并关闭</Button></>}</Space>
    </Form>}{fresh.status==='CLOSED'&&<Alert showIcon type="success" title="工单已关闭，当前只读。"/>}
   </>}
  </Drawer>
 </Page>;
}

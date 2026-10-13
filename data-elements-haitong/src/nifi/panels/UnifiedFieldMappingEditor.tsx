import { useEffect, useState } from 'react';
import { Alert, App, Button, Checkbox, Drawer, Input, InputNumber, Select, Space, Tag, Typography } from 'antd';
import { nanoid } from 'nanoid';
import { compileFieldMapping, fieldMappingCapabilities, mappingVersions, previewFieldMapping, recommendFieldMapping, validateFieldMapping, type FieldMeta, type FunctionParameter, type MappingTableRef, type PreviewResponse, type UnifiedMappingDraft, type UnifiedMappingRule } from '../api/fieldMapping';
import { DraftTable } from '../../components/DraftTable';
import { getNifiOverlayContainer } from './overlayContainer';
import MappingAssetsPanel from './MappingAssetsPanel';

const TYPES = ['ANY', 'STRING', 'INT', 'LONG', 'DECIMAL', 'DOUBLE', 'BOOLEAN', 'DATE', 'DATETIME', 'INSTANT', 'ARRAY'];
const OPERATORS = [
  ['DIRECT', '原值'], ['CAST', '按目标类型转换'], ['FUNCTION', '函数'], ['LOOKUP', '单值码表'], ['LOOKUP_MULTI', '拆分码表'],
  ['STATIC_MAP', '枚举 / 强制对标'], ['DATE_PARSE', '日期解析'], ['DATE_FORMAT', '日期格式化'], ['ID_CARD_15_TO_18', '身份证15位转18位'],
  ['ID_CARD_VALIDATE', '身份证校验'], ['CONCAT', '多字段合并'], ['EXPRESSION', '安全表达式'], ['VALIDATE', '输出校验'], ['SYSTEM_VALUE', '系统字段'],
];
function defaults(operator: string): Record<string, unknown> {
  switch (operator) {
    case 'FUNCTION': return { function: 'trim', parameters: [] };
    case 'CAST': return { logicalType: 'STRING', precision: 38, scale: 10, roundingMode: 'UNNECESSARY' };
    case 'DATE_PARSE': return { pattern: 'uuuu-MM-dd', logicalType: 'DATE' };
    case 'DATE_FORMAT': return { pattern: 'uuuuMMdd' };
    case 'ID_CARD_15_TO_18': return { centuryPolicy: 'PREFIX_19', already18Policy: 'VALIDATE_AND_KEEP' };
    case 'LOOKUP': case 'LOOKUP_MULTI': return { definition: { datasourceId: '', tableId: '', table: '', keyColumns: [''], valueColumn: '', filter: null }, missPolicy: 'NULL', allMissingPolicy: 'NULL', partialMissingPolicy: 'KEEP_TOKEN', separator: ',', nullLabelPolicy: 'PRESERVE', emptyLabelPolicy: 'PRESERVE' };
    case 'STATIC_MAP': return { values: {}, fallback: null };
    case 'CONCAT': return { inputs: [], separator: ',', nullElementPolicy: 'EMPTY' };
    case 'EXPRESSION': return { expression: '${value}' };
    case 'VALIDATE': return { required: false, maxLength: 0 };
    case 'SYSTEM_VALUE': return { generator: 'UUID' };
    default: return {};
  }
}
export default function UnifiedFieldMappingEditor({ value, onChange, sourceSchema = [], targetSchema = [], assetContext }: { value: UnifiedMappingDraft; onChange: (value: string) => void; sourceSchema?: FieldMeta[]; targetSchema?: FieldMeta[]; assetContext?: {pipelineId?:string|null;nodeId?:string;sourceRef?:MappingTableRef;targetRef?:MappingTableRef} }) {
  const { message, modal } = App.useApp();
  const [draft, setDraft] = useState(value);
  const [selected, setSelected] = useState<React.Key[]>([]);
  const [editing, setEditing] = useState<UnifiedMappingRule | null>(null);
  const [functions, setFunctions] = useState<string[]>([]);
  const [functionSchemas,setFunctionSchemas]=useState<Record<string,FunctionParameter[]>>({});
  const [assetsOpen,setAssetsOpen]=useState(false);
  const [sampleLimit,setSampleLimit]=useState(20);
  const [capabilityError, setCapabilityError] = useState('');
  const [sampleText, setSampleText] = useState('[\n  {}\n]');
  const [preview, setPreview] = useState<PreviewResponse | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  useEffect(() => setDraft(value), [value]);
  useEffect(() => { let active = true; fieldMappingCapabilities().then(result => { if (active) {setFunctions(result.functions.filter(f => f.previewSupported && f.runtimeSupported && !['uuid', 'codeTableLookup', 'expression'].includes(f.name)).map(f => f.name));setFunctionSchemas(Object.fromEntries(result.functions.map(f=>[f.name,f.parameterSchema??[]])));} }).catch(() => { if (active) setCapabilityError('函数能力目录暂不可用，已有草稿仍可编辑。'); }); return () => { active = false; }; }, []);
  function update(next: UnifiedMappingDraft) { setDraft(next); onChange(JSON.stringify({ ...next, executionEngine: 'JAVA_RECORD' }, null, 2)); }
  async function action(kind: 'validate' | 'compile' | 'preview') {
    setBusy(true);
    try {
      if (kind === 'validate') { const result = await validateFieldMapping(draft, sourceSchema, targetSchema); if (!result.valid) modal.error({ title: '规则校验未通过', content: result.errors.map(e => <div key={e.path + e.code}>{e.code}：{e.message}</div>) }); else if(result.warnings.length)modal.warning({title:'规则有效，Schema检查有提示',content:result.warnings.map(e=><div key={e.path+e.code}>{e.message}</div>)});else message.success('规则及Schema校验通过'); }
      if (kind === 'compile') { const result = await compileFieldMapping(draft, sourceSchema, targetSchema); modal.info({ title: '统一执行计划', width: 800, getContainer: getNifiOverlayContainer, content: <pre style={{ maxHeight: 520, overflow: 'auto' }}>{JSON.stringify(result.plan, null, 2)}</pre> }); }
      if (kind === 'preview') { const samples: unknown = JSON.parse(sampleText); if (!Array.isArray(samples) || samples.length > 100 || samples.some(row => !row || typeof row !== 'object' || Array.isArray(row))) throw new Error('样本须为最多100个对象的JSON数组'); const result = await previewFieldMapping(draft, samples); setPreview(result); }
    } catch (error: any) { message.error(error?.response?.data?.error ?? error?.message ?? '操作失败'); }
    finally { setBusy(false); }
  }
  function add() { const rule: UnifiedMappingRule = { ruleId: nanoid(), enabled: true, input: { kind: 'FIELD', name: '' }, target: { name: '', logicalType: 'STRING', nullable: true }, steps: [], onError: 'ERROR' }; setEditing(rule); }
  async function automatic() { setBusy(true);try { const recommendation=await recommendFieldMapping(sourceSchema,targetSchema);const compiled=await compileFieldMapping(recommendation.spec,sourceSchema,targetSchema);if(!compiled.plan)throw new Error('统一规则编译服务未返回计划');const targets=new Set(draft.rules.map(r=>r.target.name));update({...draft,rules:[...draft.rules,...compiled.plan.executionPlan.rules.filter(r=>!targets.has(r.target.name)).map(r=>({...r,ruleId:nanoid()}))]});message.success('已补充未配置目标字段，包含符合目标定义的系统字段'); }catch(error:any){message.error(error?.response?.data?.error??error.message);}finally{setBusy(false);} }
  function saveRule() { if (!editing?.target.name) { message.warning('请填写目标字段'); return; } if (draft.rules.some(r => r.ruleId !== editing.ruleId && r.target.name === editing.target.name)) { message.warning('目标字段重复'); return; } const index = draft.rules.findIndex(r => r.ruleId === editing.ruleId); const rules = [...draft.rules]; if (index < 0) rules.push(editing); else rules[index] = editing; update({ ...draft, rules }); setEditing(null); }
  async function lockSchema(){setBusy(true);try{const contract=await mappingVersions.schema(assetContext?.sourceRef,assetContext?.targetRef);update({...draft,schemaContract:contract});message.success('已锁定物理Schema，后续编译与运行将检查漂移');}catch(error:any){message.error(error?.response?.data?.error??error.message);}finally{setBusy(false);}}
  async function sourcePreview(){if(!assetContext?.sourceRef)return;setBusy(true);try{setPreview(await mappingVersions.sample(draft,assetContext.sourceRef,sampleLimit));message.success('已在服务端对原始样本执行规则，回传内容已脱敏');}catch(error:any){message.error(error?.response?.data?.error??error.message);}finally{setBusy(false);}}
  function upgradeRuntime(){modal.confirm({title:'升级当前运行图中的字段节点',content:'请先保存画布草稿。此操作只替换当前字段节点，保留来源与目标组件ID、增量水位和原运行状态；其他画布变更需单独发布。非空队列和无法核验的旧图会阻止升级。',onOk:async()=>{setBusy(true);try{const plan=await compileFieldMapping(draft,sourceSchema,targetSchema);if(!plan.plan||!assetContext?.pipelineId||!assetContext.nodeId)throw new Error('请保存并发布画布后使用运行升级');await mappingVersions.upgrade(assetContext.pipelineId,assetContext.nodeId,plan.plan.planHash);message.success('字段运行节点已升级，来源组件与水位保留');}catch(error:any){message.error(error?.response?.data?.error??error.message);throw error;}finally{setBusy(false);}}});}
  return <div className="field-mapping-editor">
    <Space wrap style={{ marginBottom: 12 }}>
      <Tag color="blue">Java Record · 统一规则</Tag><Button onClick={add}>新增字段规则</Button>
      <Button loading={busy} disabled={!sourceSchema.length||!targetSchema.length} onClick={automatic}>自动补齐映射</Button>
      <Button disabled={!selected.length} onClick={() => update({ ...draft, rules: draft.rules.map(r => selected.includes(r.ruleId) ? { ...r, enabled: true } : r) })}>批量启用</Button>
      <Button disabled={!selected.length} onClick={() => update({ ...draft, rules: draft.rules.map(r => selected.includes(r.ruleId) ? { ...r, enabled: false } : r) })}>批量停用</Button>
      <Button loading={busy} onClick={() => action('validate')}>校验</Button><Button loading={busy} onClick={() => action('compile')}>编译计划</Button><Button onClick={() => setPreviewOpen(true)}>样本预览</Button>
      <Button onClick={()=>setAssetsOpen(true)}>模板 / 计划历史</Button><Button loading={busy} disabled={!assetContext?.sourceRef&&!assetContext?.targetRef} onClick={lockSchema}>锁定物理Schema</Button>
      <Button loading={busy} disabled={!assetContext?.pipelineId||!assetContext?.nodeId} onClick={upgradeRuntime}>保留来源ID升级运行节点</Button>
      <Button loading={busy} disabled={!assetContext?.pipelineId} onClick={()=>modal.confirm({title:'启动已发布运行版本',content:'此操作启动当前NiFi运行图，保留来源水位。画布中其他尚未发布的修改继续保留为草稿；一次性来源只执行一次。',onOk:async()=>{setBusy(true);try{await mappingVersions.startPublished(assetContext!.pipelineId!);message.success('已启动当前发布版本，未发布草稿保留');}catch(error:any){message.error(error?.response?.data?.error??error.message);throw error;}finally{setBusy(false);}}})}>启动已发布运行版本</Button>
    </Space>
    {capabilityError && <Alert message={capabilityError} type="info" showIcon />}
    {!!draft.schemaContract&&<Alert type="info" message="当前草稿已锁定Schema；缺列、类型与精度变化会阻止发布或保留原输入。" style={{marginBottom:12}} />}
    <DraftTable<UnifiedMappingRule> size="small" rowKey="ruleId" scroll={{ y: 430, x: 850 }} dataSource={draft.rules} fields={{ target: {kind:'text',value:r=>r.target.name}, source: {kind:'text',value:r=>r.input?.name??r.input?.kind??'条件'} }}
      rowSelection={{ selectedRowKeys: selected, onChange: setSelected }} columns={[
        { title: '启用', width: 65, render: (_, rule) => <Checkbox checked={rule.enabled !== false} onChange={e => update({ ...draft, rules: draft.rules.map(r => r.ruleId === rule.ruleId ? { ...r, enabled: e.target.checked } : r) })} /> },
        { title: '源字段 / 输入', key:'source', render: (_, rule) => rule.when ? '条件分支' : rule.input?.name ?? rule.input?.expression ?? JSON.stringify(rule.input?.value) ?? rule.input?.kind },
        { title: '目标字段', key:'target', dataIndex: ['target', 'name'] }, { title: '目标类型', dataIndex: ['target', 'logicalType'] },
        { title: '转换步骤', render: (_, rule) => rule.steps?.length ? rule.steps.map(s => OPERATORS.find(o => o[0] === s.operator)?.[1] ?? s.operator).join(' → ') : '原值' },
        { title: '错误策略', dataIndex: 'onError' }, { title: '操作', width: 140, render: (_, rule) => <Space><Button size="small" onClick={() => setEditing({ ...structuredClone(rule), input: rule.input ?? {kind: 'LITERAL',value:null}, steps: rule.steps ?? [] })}>配置</Button><Button size="small" danger onClick={() => update({ ...draft, rules: draft.rules.filter(r => r.ruleId !== rule.ruleId) })}>移除</Button></Space> },
      ]} />
    <Space wrap style={{ marginTop: 12 }}><Typography.Text>记录错误处理</Typography.Text>
      <Select value={String(draft.policies.recordErrorMode ?? 'FAIL_FLOWFILE')} options={[{ value: 'FAIL_FLOWFILE', label: '整批失败并保留原输入' }, { value: 'ROUTE_INVALID', label: '成功记录继续，错误记录隔离' }]} onChange={recordErrorMode => update({ ...draft, policies: { ...draft.policies, recordErrorMode } })} style={{ width: 250 }} />
      <Checkbox checked={!!draft.policies.allowUnmappedPassthrough} onChange={e => update({ ...draft, policies: { ...draft.policies, allowUnmappedPassthrough: e.target.checked } })}>未映射字段直通</Checkbox>
    </Space>
    <Drawer title="字段规则详细配置" width={720} open={!!editing} onClose={() => setEditing(null)} getContainer={getNifiOverlayContainer} extra={<Button type="primary" onClick={saveRule}>应用到草稿</Button>}>
      {editing && <Space direction="vertical" style={{ width: '100%' }} size="middle">
        {editing.when!=null&&<Alert type="info" message="当前字段使用条件分支，请在完整规则JSON中修改when、then和else；分支输入优先于下面的普通输入。" />}
        <Space wrap><Typography.Text>输入</Typography.Text><Select value={editing.input.kind} options={['FIELD', 'LITERAL', 'EXPRESSION', 'SYSTEM'].map(v => ({ value: v, label: v }))} onChange={kind => setEditing({ ...editing, input: { kind } })} style={{ width: 150 }} />
          {editing.input.kind === 'FIELD' && (sourceSchema.length ? <Select showSearch value={editing.input.name} placeholder="源字段名称" style={{width:240}} options={sourceSchema.map(f=>({value:f.name,label:f.name}))} onChange={name => setEditing({ ...editing, input: { ...editing.input, name } })} /> : <Input placeholder="源字段名称" value={editing.input.name} onChange={e=>setEditing({...editing,input:{...editing.input,name:e.target.value}})} />)}
          {editing.input.kind === 'LITERAL' && <JsonInput value={editing.input.value ?? null} onChange={v => setEditing({ ...editing, input: { ...editing.input, value: v } })} />}
          {editing.input.kind === 'EXPRESSION' && <Input placeholder="${field:字段名}" value={editing.input.expression} onChange={e => setEditing({ ...editing, input: { ...editing.input, expression: e.target.value } })} />}
          {editing.input.kind === 'SYSTEM' && <Select value={editing.input.generator ?? 'UUID'} options={['UUID', 'NOW'].map(v => ({ value: v, label: v }))} onChange={generator => setEditing({ ...editing, input: { kind: 'SYSTEM', generator } })} />}
        </Space>
        <Space wrap><Typography.Text>目标</Typography.Text><Input value={editing.target.name} placeholder="目标字段名称" onChange={e => setEditing({ ...editing, target: { ...editing.target, name: e.target.value } })} /><Select value={editing.target.logicalType ?? 'ANY'} options={TYPES.map(v => ({ value: v, label: v }))} onChange={logicalType => setEditing({ ...editing, target: { ...editing.target, logicalType } })} style={{ width: 140 }} /><Checkbox checked={editing.target.nullable !== false} onChange={e => setEditing({ ...editing, target: { ...editing.target, nullable: e.target.checked } })}>允许NULL</Checkbox></Space>
        <Space wrap><Typography.Text>最大长度</Typography.Text><InputNumber min={0} max={262144} value={editing.target.length??0} onChange={length=>setEditing({...editing,target:{...editing.target,length:length??0}})} />{editing.target.logicalType==='DECIMAL'&&<><Typography.Text>精度 / 小数位</Typography.Text><InputNumber min={1} max={1000} value={editing.target.precision??38} onChange={precision=>setEditing({...editing,target:{...editing.target,precision:precision??38}})} /><InputNumber min={0} max={editing.target.precision??38} value={editing.target.scale??10} onChange={scale=>setEditing({...editing,target:{...editing.target,scale:scale??0}})} /></>}</Space>
        <Alert type="info" message="步骤依次执行。码表关联保留真实数据源、登记表、键列、取值列和过滤条件；身份证转换须明确世纪策略。新增目标字段需与物理目标表一致。" />
        {editing.steps.map((step, index) => <div key={step.stepId ?? index} style={{ border: '1px solid #d9d9d9', padding: 12, borderRadius: 6 }}>
          <Space wrap><Tag>{index + 1}</Tag><Select value={step.operator} options={OPERATORS.map(([value, label]) => ({ value, label }))} style={{ width: 220 }} onChange={operator => setEditing({ ...editing, steps: editing.steps.map((s, i) => i === index ? { ...s, operator, args: defaults(operator) } : s) })} />
            <Button disabled={index === 0} onClick={() => { const steps = [...editing.steps]; [steps[index - 1], steps[index]] = [steps[index], steps[index - 1]]; setEditing({ ...editing, steps }); }}>上移</Button><Button danger onClick={() => setEditing({ ...editing, steps: editing.steps.filter((_, i) => i !== index) })}>移除</Button></Space>
          {step.operator === 'FUNCTION' && <><Space style={{ marginTop: 8 }}><Typography.Text>函数</Typography.Text><Select showSearch value={String(step.args?.function ?? '')} options={functions.map(value => ({ value, label: value }))} style={{ width: 240 }} onChange={fn => setEditing({ ...editing, steps: editing.steps.map((s, i) => i === index ? { ...s, args: { function: fn, parameters: (functionSchemas[fn]??[]).filter(parameter=>parameter.required).map(parameter=>parameter.defaultValue) } } : s) })} /></Space><FunctionParameters schema={functionSchemas[String(step.args?.function??'')]??[]} values={Array.isArray(step.args?.parameters)?step.args!.parameters:[]} onChange={parameters=>setEditing({...editing,steps:editing.steps.map((s,i)=>i===index?{...s,args:{...s.args,parameters}}:s)})} /></>}
          {['LOOKUP','LOOKUP_MULTI'].includes(step.operator)&&<DictionaryFreshness args={{...step.args,definition:step.args?.definition??draft.dictionaryBindings?.[String(step.args?.dictionaryId??'')]}} onChange={args=>setEditing({...editing,steps:editing.steps.map((s,i)=>i===index?{...s,args}:s)})} />}
          <StepArguments operator={step.operator} args={step.args??{}} onChange={args=>setEditing({...editing,steps:editing.steps.map((s,i)=>i===index?{...s,args}:s)})} />
          <Typography.Paragraph type="secondary" style={{ marginTop: 8, marginBottom: 4 }}>类型化参数（JSON对象；字符串、数字、布尔和NULL分别保存）</Typography.Paragraph>
          <JsonInput value={step.args ?? {}} objectOnly onChange={args => setEditing({ ...editing, steps: editing.steps.map((s, i) => i === index ? { ...s, args: args as Record<string, unknown> } : s) })} />
        </div>)}
        <Button onClick={() => setEditing({ ...editing, steps: [...editing.steps, { stepId: nanoid(), operator: 'DIRECT', args: {} }] })}>增加转换步骤</Button>
        <Space><Typography.Text>字段错误</Typography.Text><Select value={editing.onError ?? 'ERROR'} options={[['ERROR', '交给记录错误策略'], ['NULL', '置NULL'], ['KEEP_SOURCE', '保留原输入']].map(([value, label]) => ({ value, label }))} style={{ width: 200 }} onChange={onError => setEditing({ ...editing, onError })} /></Space>
        <Input.TextArea placeholder="规则说明" value={editing.comment} onChange={e => setEditing({ ...editing, comment: e.target.value })} />
        <details><summary>完整规则JSON（条件分支、输出精度及高级配置）</summary><JsonInput value={editing} objectOnly onChange={raw => { const rule=raw as UnifiedMappingRule;if(!rule.ruleId||!rule.input?.kind||!rule.target?.name||!Array.isArray(rule.steps)){message.error('规则须包含ruleId、input、target及steps');return;}setEditing(rule);}} /></details>
      </Space>}
    </Drawer>
    <Drawer title="统一内核样本预览" width={900} open={previewOpen} onClose={() => setPreviewOpen(false)} getContainer={getNifiOverlayContainer}>
      <Alert type="info" message="手工样本，最多100行。样本值按当前规则执行；字典使用本次授权读取。" style={{ marginBottom: 12 }} />
      {assetContext?.sourceRef&&<Space style={{marginBottom:12}}><Typography.Text>来源取样行数</Typography.Text><InputNumber min={1} max={100} value={sampleLimit} onChange={limit=>setSampleLimit(limit??20)} /><Button loading={busy} onClick={sourcePreview}>从登记来源取样并预览（脱敏）</Button></Space>}
      <Input.TextArea rows={8} value={sampleText} onChange={e => setSampleText(e.target.value)} /><Button type="primary" loading={busy} onClick={() => action('preview')} style={{ margin: '12px 0' }}>执行预览</Button>
      {preview && <><Alert type={preview.rowResults.some(r => !r.success) ? 'warning' : 'success'} message={`成功 ${preview.resultRows.length} 行，错误 ${preview.rowResults.filter(r => !r.success).length} 行，字典SQL ${preview.stats.dictionarySqlCount ?? 0} 次`} />
        {preview.rowResults.filter(r => !r.success).map(r => <Alert key={r.rowIndex} type="error" message={`第${r.rowIndex + 1}行：${r.errors.join('；')}`} />)}<pre style={{ overflow: 'auto' }}>{JSON.stringify(preview.resultRows, null, 2)}</pre></>}
    </Drawer>
    <MappingAssetsPanel open={assetsOpen} onClose={()=>setAssetsOpen(false)} draft={draft} onChange={update} pipelineId={assetContext?.pipelineId} nodeId={assetContext?.nodeId} />
  </div>;
}
function FunctionParameters({schema,values,onChange}:{schema:FunctionParameter[];values:unknown[];onChange:(values:unknown[])=>void}){
  function patch(index:number,value:unknown){const next=[...values];for(let i=0;i<=index;i++)if(next[i]===undefined)next[i]=schema[i]?.defaultValue??null;next[index]=value;onChange(next);}
  return <Space direction="vertical" style={{width:'100%',marginTop:8}}>{schema.map(parameter=><div key={parameter.index}><Space wrap><Typography.Text>{parameter.name}{parameter.required?'（必填）':''}</Typography.Text>{!parameter.required&&<Checkbox checked={parameter.index<values.length} onChange={event=>event.target.checked?patch(parameter.index,parameter.defaultValue):onChange(values.slice(0,parameter.index))}>使用此参数</Checkbox>}</Space>{(parameter.required||parameter.index<values.length)&&(parameter.choices?.length?<Select style={{width:'100%'}} value={String(values[parameter.index]??parameter.defaultValue??'')} options={parameter.choices.map(value=>({value,label:value}))} onChange={value=>patch(parameter.index,value)} />:parameter.type==='INTEGER'?<InputNumber style={{width:'100%'}} precision={0} value={values[parameter.index] as number} onChange={value=>patch(parameter.index,value)} />:parameter.type==='NUMBER'?<InputNumber style={{width:'100%'}} stringMode value={values[parameter.index] as string} onChange={value=>patch(parameter.index,value)} />:parameter.type==='STRING'?<Input value={String(values[parameter.index]??'')} onChange={event=>patch(parameter.index,event.target.value)} />:<JsonInput value={values[parameter.index]??null} objectOnly={parameter.type==='OBJECT'} onChange={value=>patch(parameter.index,value)} />)}</div>)}</Space>;
}
function DictionaryFreshness({args,onChange}:{args:Record<string,unknown>;onChange:(args:Record<string,unknown>)=>void}){
  const {message}=App.useApp();const definition=(args.definition??{}) as Record<string,unknown>;const [name,setName]=useState('');const [busy,setBusy]=useState(false);const mode=String(definition.freshnessMode??'LIVE_BATCH');
  const patch=(value:Record<string,unknown>)=>onChange({...args,definition:{...definition,...value}});
  async function build(){if(!name.trim()){message.warning('请输入快照版本族名称');return;}setBusy(true);try{const result=await mappingVersions.snapshot(name.trim(),definition);patch({freshnessMode:'VERSIONED_SNAPSHOT',snapshotId:result.id,snapshotHash:result.hash});message.success(`完整快照版本${result.version}已保存`);}catch(error:any){message.error(error?.response?.data?.error??error.message);}finally{setBusy(false);}}
  return <Space direction="vertical" style={{width:'100%',marginTop:10}}><Space><Typography.Text>字典一致性</Typography.Text><Select style={{width:240}} value={mode} options={[{value:'LIVE_BATCH',label:'实时批量读取'},{value:'TTL_BATCH_CACHE',label:'允许有界TTL缓存'},{value:'VERSIONED_SNAPSHOT',label:'固定不可变版本快照'}]} onChange={freshnessMode=>patch({freshnessMode,ttlSeconds:definition.ttlSeconds??60})} /></Space>{mode==='TTL_BATCH_CACHE'&&<Space><Typography.Text>允许陈旧秒数</Typography.Text><InputNumber min={1} max={600} value={Number(definition.ttlSeconds??60)} onChange={ttlSeconds=>patch({ttlSeconds:ttlSeconds??60})} /></Space>}{mode==='VERSIONED_SNAPSHOT'&&<><Input addonBefore="固定快照ID" value={String(definition.snapshotId??'')} onChange={event=>patch({snapshotId:event.target.value})} /><Space><Input value={name} maxLength={128} placeholder="快照版本族名称" onChange={event=>setName(event.target.value)} /><Button loading={busy} onClick={build}>按当前字典定义构建完整快照</Button></Space><Typography.Text type="secondary">完整快照最多5000行、1MiB，超限或失败不会保存截断版本。</Typography.Text></>}</Space>;
}
function StepArguments({operator,args,onChange}:{operator:string;args:Record<string,unknown>;onChange:(args:Record<string,unknown>)=>void}) {
  const patch=(key:string,value:unknown)=>onChange({...args,[key]:value});
  const text=(key:string,label:string)=><Input key={key} addonBefore={label} value={String(args[key]??'')} onChange={e=>patch(key,e.target.value)} />;
  const choice=(key:string,label:string,values:string[])=><Space key={key}><Typography.Text>{label}</Typography.Text><Select style={{width:190}} value={String(args[key]??values[0])} options={values.map(value=>({value,label:value}))} onChange={value=>patch(key,value)} /></Space>;
  return <Space direction="vertical" style={{width:'100%',marginTop:10}}>
    {operator==='CAST'&&<>{choice('logicalType','转换类型',TYPES.filter(t=>t!=='ANY'))}{choice('roundingMode','舍入',['UNNECESSARY','HALF_UP','HALF_EVEN','DOWN','UP'])}</>}
    {['DATE_PARSE','DATE_FORMAT'].includes(operator)&&<>{text('pattern','日期格式')}{operator==='DATE_PARSE'&&choice('logicalType','时间语义',['DATE','DATETIME','INSTANT'])}</>}
    {operator==='ID_CARD_15_TO_18'&&<>{choice('centuryPolicy','15位世纪规则',['PREFIX_19'])}{choice('already18Policy','已有18位',['VALIDATE_AND_KEEP','ERROR'])}</>}
    {operator==='EXPRESSION'&&text('expression','表达式')}
    {operator==='SYSTEM_VALUE'&&choice('generator','生成方式',['UUID','NOW'])}
    {['LOOKUP','LOOKUP_MULTI'].includes(operator)&&<>{choice(operator==='LOOKUP'?'missPolicy':'allMissingPolicy','未命中',['NULL','KEEP_SOURCE','KEEP_INPUT','EMPTY_STRING','DEFAULT_VALUE','ERROR'])}{operator==='LOOKUP_MULTI'&&<>{text('separator','输入分隔符')}{text('outputSeparator','输出分隔符')}{choice('partialMissingPolicy','部分未命中',['KEEP_TOKEN','DROP_TOKEN','EMPTY_SLOT','ERROR'])}{choice('outputMode','输出形式',['STRING','ARRAY'])}</>}{choice('nullLabelPolicy','已命中NULL',['PRESERVE','AS_MISSING'])}{choice('emptyLabelPolicy','已命中空串',['PRESERVE','AS_MISSING'])}</>}
  </Space>;
}
function JsonInput({ value, onChange, objectOnly = false }: { value: unknown; onChange: (value: unknown) => void; objectOnly?: boolean }) {
  const [text, setText] = useState(JSON.stringify(value, null, 2)); const [error, setError] = useState('');
  useEffect(() => setText(JSON.stringify(value, null, 2)), [value]);
  return <><Input.TextArea autoSize={{ minRows: 2, maxRows: 12 }} value={text} onChange={e => setText(e.target.value)} onBlur={() => { try { const parsed = JSON.parse(text); if (objectOnly && (!parsed || typeof parsed !== 'object' || Array.isArray(parsed))) throw new Error('须填写JSON对象'); setError(''); onChange(parsed); } catch (error: any) { setError(error.message); } }} />{error && <Typography.Text type="danger">{error}</Typography.Text>}</>;
}

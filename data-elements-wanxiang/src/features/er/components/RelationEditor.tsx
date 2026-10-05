import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Form, Input, Modal, Select, Space } from 'antd';
import type { ConditionTerm, FieldMapping, RelationCreate } from '../types/domain';
import { useStudio } from '../store';
import { Icon } from './Icon';
import { compatibleTypes } from '../core/relations';
import { errorText } from '../core/model';
interface Values { name:string;sourceEntityId:string;targetEntityId:string;semanticType:'reference'|'join';description:string;sourceMax:'unknown'|'1'|'many';targetMax:'unknown'|'1'|'many';sourceMin:'unknown'|'0'|'1';targetMin:'unknown'|'0'|'1' }
export function RelationEditor(){
  const {message,modal}=App.useApp();const dirty=useRef(false);const editor=useStudio(s=>s.editor);const snapshot=useStudio(s=>s.data?.snapshot);const relationships=useStudio(s=>s.data?.relationships);
  const [busy,setBusy]=useState(false);const [form]=Form.useForm<Values>();const[mappings,setMappings]=useState<FieldMapping[]>([]);const[error,setError]=useState<string|null>(null);
  const[conditions,setConditions]=useState<ConditionTerm[]>([]);
  const sourceId=Form.useWatch('sourceEntityId',form);const targetId=Form.useWatch('targetEntityId',form);const semantic=Form.useWatch('semanticType',form);
  useEffect(()=>{
    if(!editor||!snapshot)return;
    const old=relationships?.find(r=>r.id===editor.editingId);
    const a=old?.sourceEntityId??editor.sourceEntityId;const b=old?.targetEntityId??editor.targetEntityId;
    const name=(id?:string)=>snapshot.entities.find(e=>e.id===id)?.displayName??'';
    form.resetFields();form.setFieldsValue({name:old?.name??(a&&b?`${name(a)}引用${name(b)}`:''),sourceEntityId:a,targetEntityId:b,semanticType:old?.semanticType==='join'?'join':'reference',description:old?.description??'',sourceMax:old?.sourcesPerTarget?.max??'unknown',targetMax:old?.targetsPerSource?.max??'unknown',sourceMin:old?.sourcesPerTarget?.min??'unknown',targetMin:old?.targetsPerSource?.min??'unknown'});
    setConditions(old?.conditions?.map(c=>({...c}))??[]);
    setMappings(old?.mappings?.map(m=>({...m}))??[{sourceFieldId:editor.sourceFieldId??'',targetFieldId:editor.targetFieldId??'',comparisonRule:'exact'}]);setError(null);dirty.current=false;
  // Initializing only on opening avoids overwriting in-progress edits when another panel updates.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  },[editor,form]);
  if(!snapshot)return null;
  const source=snapshot.entities.find(e=>e.id===sourceId);const target=snapshot.entities.find(e=>e.id===targetId);
  const options=snapshot.entities.map(e=>({value:e.id,label:`${e.displayName??e.name} · ${e.name}`}));
  const requestClose=()=>{if(!dirty.current){useStudio.getState().openEditor(null);return;}modal.confirm({title:'放弃尚未保存的修改？',icon:null,content:'此关系的字段映射与说明尚未保存。',okText:'放弃修改',cancelText:'继续编辑',okButtonProps:{danger:true},onOk:()=>useStudio.getState().openEditor(null)});};
  const changeMapping=(index:number,key:'sourceFieldId'|'targetFieldId',value:string)=>{dirty.current=true;setMappings(ms=>ms.map((m,i)=>i===index?{...m,[key]:value}:m));};
  function supplementKey(){
    if(!source||!target)return;
    const selected=new Set(mappings.map(m=>m.targetFieldId));
    const key=target.keys?.find(k=>k.fieldIds.some(id=>selected.has(id)));
    if(!key){message.info('目标没有可补齐的键约束，请手动添加映射。');return;}
    const copy=[...mappings];
    for(const targetFieldId of key.fieldIds){if(copy.some(m=>m.targetFieldId===targetFieldId))continue;const tf=target.fields.find(f=>f.id===targetFieldId);const sf=source.fields.find(f=>f.name===tf?.name&&tf&&compatibleTypes(f,tf));if(sf)copy.unshift({sourceFieldId:sf.id,targetFieldId,comparisonRule:'exact'});}
    setMappings(copy);dirty.current=true;message.info(copy.length>mappings.length?'已补齐同名、类型兼容的联合键字段。':'没有可自动补齐的字段，请检查作用域映射。');
  }
  async function save(){
    try{const values=await form.validateFields();if(conditions.some(c=>!c.fieldId||c.operator==='in'&&(!Array.isArray(c.value)||!c.value.length)))throw new Error('请补齐条件字段与 IN 取值');const payload:RelationCreate={snapshotId:snapshot!.id,name:values.name,sourceEntityId:values.sourceEntityId,targetEntityId:values.targetEntityId,semanticType:values.semanticType,description:values.description,mappings,conditions,sourcesPerTarget:{min:values.sourceMin,max:values.sourceMax,basis:'business'},targetsPerSource:{min:values.targetMin,max:values.targetMax,basis:'business'}};setBusy(true);await useStudio.getState().saveRelationship(payload,editor?.editingId);message.success('完整逻辑关系已保存，源数据库未被修改。');}catch(e){if(e&&typeof e==='object'&&'errorFields'in e)return;setError(errorText(e));}finally{setBusy(false);}
  }
  return <Modal open={!!editor} onCancel={requestClose} maskClosable={false} title={<span className="modal-title"><span className="modal-icon"><Icon name="link"/></span>{editor?.editingId?'编辑逻辑关系':'新建逻辑关系'}</span>} width={760} centered destroyOnHidden footer={<><Button onClick={requestClose}>取消</Button><Button type="primary" loading={busy} onClick={save}>保存逻辑关系</Button></>}>
    <div className="modal-intro">在字段之间建立可解释的业务关联。此操作不会创建数据库外键。</div>
    {error&&<Alert type="error" showIcon title="请检查关系配置" description={<div style={{whiteSpace:'pre-line'}}>{error}</div>} closable onClose={()=>setError(null)} style={{marginBottom:16}}/>}
    <Form form={form} onValuesChange={()=>{dirty.current=true;setError(null);}} layout="vertical" requiredMark={false} initialValues={{semanticType:'reference',sourceMax:'unknown',targetMax:'unknown',sourceMin:'unknown',targetMin:'unknown'}}>
      <Form.Item name="name" label="关系名称" rules={[{required:true,message:'请输入关系名称。'},{max:128,message:'最多输入 128 个字符。'}]}><Input placeholder="例如：节目排期引用节目" maxLength={128}/></Form.Item>
      <div className="entity-picker-grid"><Form.Item name="sourceEntityId" label="引用方" rules={[{required:true,message:'请选择引用方。'}]}><Select showSearch optionFilterProp="label" options={options} placeholder="选择数据表" onChange={()=>{setMappings([{sourceFieldId:'',targetFieldId:'',comparisonRule:'exact'}]);setConditions([]);}}/></Form.Item><Button className="swap-button" type="text" title="互换引用方向" aria-label="互换引用方向" icon={<Icon name="refresh" size={17}/>} onClick={()=>{dirty.current=true;form.setFieldsValue({sourceEntityId:targetId,targetEntityId:sourceId});setMappings(ms=>ms.map(m=>({sourceFieldId:m.targetFieldId,targetFieldId:m.sourceFieldId,comparisonRule:m.comparisonRule})));setConditions(cs=>cs.map(c=>({...c,side:c.side==='source'?'target':'source'})));const v=form.getFieldsValue();form.setFieldsValue({sourceMin:v.targetMin,sourceMax:v.targetMax,targetMin:v.sourceMin,targetMax:v.sourceMax});}}/><Form.Item name="targetEntityId" label="被引用方" rules={[{required:true,message:'请选择被引用方。'}]}><Select showSearch optionFilterProp="label" options={options} placeholder="选择目标表" onChange={()=>{setMappings([{sourceFieldId:'',targetFieldId:'',comparisonRule:'exact'}]);setConditions([]);}}/></Form.Item></div>
      <div className="mapping-section"><div className="section-heading"><h3>字段映射 <span>{mappings.length} 组</span></h3><Button type="link" size="small" onClick={supplementKey}>补齐联合键</Button></div>
        {mappings.map((m,index)=>{
          const sf=source?.fields.find(f=>f.id===m.sourceFieldId),tf=target?.fields.find(f=>f.id===m.targetFieldId);const bad=!!sf&&!!tf&&!compatibleTypes(sf,tf);
          return <div key={index} className="mapping-editor-row"><Select status={bad?'warning':undefined} showSearch optionFilterProp="label" value={m.sourceFieldId||undefined} options={source?.fields.map(f=>({value:f.id,label:`${f.path} · ${f.nativeType}`}))} placeholder="引用字段" onChange={v=>changeMapping(index,'sourceFieldId',v)}/><Icon name="arrow" size={15}/><Select status={bad?'warning':undefined} showSearch optionFilterProp="label" value={m.targetFieldId||undefined} options={target?.fields.map(f=>({value:f.id,label:`${f.path} · ${f.nativeType}`}))} placeholder="目标字段" onChange={v=>changeMapping(index,'targetFieldId',v)}/><Button type="text" aria-label={`移除第${index+1}组映射`} danger disabled={mappings.length===1} icon={<Icon name="close" size={14}/>} onClick={()=>{dirty.current=true;setMappings(ms=>ms.filter((_,i)=>i!==index));}}/></div>;
        })}
        <Button type="dashed" block disabled={mappings.length>=64} icon={<Icon name="plus" size={14}/>} onClick={()=>{dirty.current=true;setMappings(ms=>[...ms,{sourceFieldId:'',targetFieldId:'',comparisonRule:'exact'}]);}}>添加字段映射</Button>
        <p className="subtle-note">所有映射一起构成一条联合键关系；基数为业务约束，验证结果不会自动替代你的定义。</p>
      </div>
      <div className="form-two-columns"><Form.Item name="semanticType" label="关系语义"><Select options={[{value:'reference',label:'引用关系（类型兼容）'},{value:'join',label:'一般 JOIN（需业务说明）'}]}/></Form.Item><div className="cardinality-fields"><div className="field-label">最大基数 <span>未知时保持“?”</span></div><Space.Compact block><Form.Item name="sourceMax" noStyle><Select aria-label="每个目标对应的引用记录数" options={[{value:'unknown',label:'源：?'},{value:'many',label:'源：N'},{value:'1',label:'源：1'}]}/></Form.Item><Form.Item name="targetMax" noStyle><Select aria-label="每个引用记录对应的目标数" options={[{value:'unknown',label:'目标：?'},{value:'1',label:'目标：1'},{value:'many',label:'目标：N'}]}/></Form.Item></Space.Compact></div></div>
      <div className="form-two-columns"><Form.Item name="sourceMin" label="每个目标至少被引用"><Select options={[{value:'unknown',label:'未知'},{value:'0',label:'0（允许无引用）'},{value:'1',label:'1（必须被引用）'}]}/></Form.Item><Form.Item name="targetMin" label="每条来源至少匹配目标"><Select options={[{value:'unknown',label:'未知'},{value:'0',label:'0（允许无匹配）'},{value:'1',label:'1（必须有匹配）'}]}/></Form.Item></div>
      <div className="mapping-section"><div className="section-heading"><h3>关系条件 <span>AND</span></h3></div>{conditions.map((term,index)=>{const fields=term.side==='source'?source?.fields:target?.fields;const update=(patch:Partial<ConditionTerm>)=>{dirty.current=true;setConditions(items=>items.map((c,i)=>i===index?{...c,...patch}:c));};return <div key={index} className="relation-condition-row"><Select aria-label={`条件${index+1}范围`} value={term.side} options={[{value:'source',label:'引用方'},{value:'target',label:'目标方'}]} onChange={side=>update({side,fieldId:''})}/><Select aria-label={`条件${index+1}字段`} showSearch optionFilterProp="label" value={term.fieldId||undefined} placeholder="条件字段" options={fields?.map(f=>({value:f.id,label:f.name}))} onChange={fieldId=>update({fieldId})}/><Select aria-label={`条件${index+1}操作`} value={term.operator} options={[{value:'eq',label:'等于'},{value:'in',label:'属于 IN'},{value:'is_not_null',label:'非空'}]} onChange={operator=>update({operator,value:operator==='in'?[]:null})}/>{term.operator==='in'?<Select mode="tags" aria-label={`条件${index+1}值`} tokenSeparators={[',','，']} value={Array.isArray(term.value)?term.value:[]} placeholder="输入多个值" onChange={value=>update({value})}/>:<Input aria-label={`条件${index+1}值`} disabled={term.operator==='is_not_null'} placeholder={term.operator==='eq'?'取值（留空匹配 NULL）':'无需取值'} value={term.value==null?'':String(term.value)} onChange={e=>update({value:e.target.value||null})}/>}<Button type="text" danger aria-label={`删除条件${index+1}`} icon={<Icon name="close"/>} onClick={()=>{dirty.current=true;setConditions(items=>items.filter((_,i)=>i!==index));}}/></div>;})}<Button type="dashed" block disabled={conditions.length>=32} onClick={()=>{dirty.current=true;setConditions(items=>[...items,{side:'source',fieldId:'',operator:'eq',value:null}]);}}>添加条件</Button></div>
      <Form.Item name="description" label="业务说明" rules={semantic==='join'?[{required:true,message:'请说明一般 JOIN 的业务含义或类型差异。'}]:[]}><Input.TextArea placeholder="说明关联范围、业务用途或需要注意的条件…" rows={3} maxLength={2000} showCount/></Form.Item>
    </Form>
  </Modal>;
}
